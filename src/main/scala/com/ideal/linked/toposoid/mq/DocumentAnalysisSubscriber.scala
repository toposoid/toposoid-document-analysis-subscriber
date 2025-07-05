package com.ideal.linked.toposoid.mq

import akka.actor.ActorSystem
import akka.stream.alpakka.sqs.{MessageAction, SqsAckResult, SqsSourceSettings}
import akka.stream.alpakka.sqs.scaladsl.{SqsAckFlow, SqsSource}
import akka.stream.scaladsl.Sink
import com.github.matsluni.akkahttpspi.AkkaHttpClient
import com.ibm.icu.text.Transliterator
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.ToposoidUtils.assignId
import com.ideal.linked.toposoid.common.{HEADLINES, Neo4JUtils, Neo4JUtilsImpl, NonSentenceType, REFERENCES, TABLE_OF_CONTENTS, TITLE_OF_TOP_PAGE, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.common.mq.{DocumentRegistration, KnowledgeRegistration}
import com.ideal.linked.toposoid.knowledgebase.document.model.{Document, Propositions}
import com.ideal.linked.toposoid.knowledgebase.model.PredicateArgumentStructure
import com.ideal.linked.toposoid.knowledgebase.regist.model.{ImageReference, Knowledge, KnowledgeForImage, KnowledgeForTable, KnowledgeSentenceSet, PropositionRelation, Reference, TableReference}
import com.ideal.linked.toposoid.knowledgebase.regist.rdb.model.{DocumentAnalysisResultHistoryRecord, KnowledgeRegisterHistoryRecord, NonSentenceSectionsRecord}
import com.ideal.linked.toposoid.mq.RdbUtils.{addDocumentRegistrationResult, addKnowledgeRegistrationResult, addNonSentenceSectionResultSub}
import com.ideal.linked.toposoid.protocol.model.base.AnalyzedSentenceObjects
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.protocol.model.parser.{InputSentenceForParser, KnowledgeForParser, KnowledgeSentenceSetForParser}
import com.ideal.linked.toposoid.sentence.transformer.neo4j.{AnalyzedPropositionPair, AnalyzedPropositionSet, Sentence2Neo4jTransformer}
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.Json
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient

import java.net.URI
import io.jvm.uuid.UUID

import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}


object DocumentAnalysisSubscriber extends App with LazyLogging {

  val endpoint = "http://" + conf.getString("TOPOSOID_MQ_HOST") + ":" + conf.getString("TOPOSOID_MQ_PORT")
  implicit val actorSystem = ActorSystem("example")

  //TODO:Credentialsは、BIZ環境にも対応できるようにしておく
  implicit val sqsClient = SqsAsyncClient
    .builder()
    .credentialsProvider(
      StaticCredentialsProvider.create(
        AwsBasicCredentials.create(conf.getString("TOPOSOID_MQ_ACCESS_KEY"), conf.getString("TOPOSOID_MQ_SECRET_KEY")) // (1)
      )
    )
    .endpointOverride(URI.create(endpoint)) // (2)
    .region(Region.AP_NORTHEAST_1)
    .httpClient(AkkaHttpClient.builder()
      .withActorSystem(actorSystem).build())
    .build()

  private val queueUrl = endpoint + "/" + conf.getString("TOPOSOID_MQ_DOCUMENT_ANALYSIS_QUENE")
  private val settings = SqsSourceSettings()
  private val langPatternJP: Regex = "^ja_.*".r
  private val langPatternEN: Regex = "^en_.*".r

  private def convertKnowledge(knowledge: Knowledge): Knowledge = {
    val knowledgeForImages: List[KnowledgeForImage] = knowledge.knowledgeForImages.map(y => {
      Option(y.id) match {
        case Some(x) => {
          if(x.strip().equals("")) KnowledgeForImage(UUID.random.toString, y.imageReference)
          else y
        }
        case None => {
          KnowledgeForImage(UUID.random.toString, y.imageReference)
        }
      }
    })
    val knowledgeForTables: List[KnowledgeForTable] = knowledge.knowledgeForTables.map(y => {
      Option(y.id) match {
        case Some(x) => {
          if (x.strip().equals("")) KnowledgeForTable(UUID.random.toString, y.tableReference)
          else y
        }
        case None => {
          KnowledgeForTable(UUID.random.toString, y.tableReference)
        }
      }
    })
    Knowledge(knowledge.sentence, knowledge.lang, knowledge.extentInfoJson, knowledge.isNegativeSentence, knowledgeForImages, knowledgeForTables, knowledgeForDocument = knowledge.knowledgeForDocument, documentPageReference = knowledge.documentPageReference)
  }

  private def makeKnowledgeSentenceSet(proposition:List[Knowledge]): KnowledgeSentenceSet = {
    //Relationは、上から順番に繋がっているものとする。
    //Relationは、Documentの場合なしとするのもありか？　ただし、このようにするとパラグラフの意味はほぼなくなる。。。
    val propositionRelations: List[PropositionRelation] = (0 to proposition.length -1).toList.foldLeft(List.empty[PropositionRelation]) {
      (acc, x) => {
        if(x < proposition.length -1) {
          acc :+ PropositionRelation("AND", x, x+1)
        }else {
          acc
        }
      }
    }
    KnowledgeSentenceSet(
      premiseList = List.empty[Knowledge], premiseLogicRelation = List.empty[PropositionRelation],
      claimList = proposition, claimLogicRelation = propositionRelations)
  }

  //Common化候補
  //==================================================================================================================================
  /*
  private def assignId(knowledgeSentenceSet: KnowledgeSentenceSet): (KnowledgeSentenceSetForParser, String) = {
    val propositionId = UUID.random.toString
    val knowledgeForParserPremise: List[KnowledgeForParser] = knowledgeSentenceSet.premiseList.map(x => KnowledgeForParser(propositionId, UUID.random.toString, convertKnowledge(x)))
    val knowledgeForParserClaim: List[KnowledgeForParser] = knowledgeSentenceSet.claimList.map(x => KnowledgeForParser(propositionId, UUID.random.toString, convertKnowledge(x)))

    (KnowledgeSentenceSetForParser(
      premiseList = knowledgeForParserPremise,
      premiseLogicRelation = knowledgeSentenceSet.premiseLogicRelation,
      claimList = knowledgeForParserClaim,
      claimLogicRelation = knowledgeSentenceSet.claimLogicRelation
    ), propositionId)
  }
  */
  private def classifyKnowledgeBySentenceType(premiseList: List[AnalyzedPropositionPair], premiseLogicRelation: List[PropositionRelation],
                                              claimList: List[AnalyzedPropositionPair], claimLogicRelation: List[PropositionRelation]): AnalyzedPropositionSet = {
    //TODO:マイクロサービス化
    //Claim側の情報から、Premiseの情報を追加する。
    AnalyzedPropositionSet(premiseList = premiseList, premiseLogicRelation = premiseLogicRelation, claimList = claimList, claimLogicRelation = claimLogicRelation)
  }

  private def deleteObject(knowledgeForParser: KnowledgeForParser, transversalState: TransversalState) = {
    //TODO:documentIdを持っているノードも削除
    //Delete relationships
    val query = s"MATCH (n)-[r]-() WHERE n.propositionId = '${knowledgeForParser.propositionId}' DELETE n,r"
    val neo4JUtils = new Neo4JUtilsImpl()
    neo4JUtils.executeQuery(query, transversalState)
    //Delete orphan nodes
    val query2 = s"MATCH (n) WHERE n.propositionId = '${knowledgeForParser.propositionId}' DELETE n"
    neo4JUtils.executeQuery(query2, transversalState)
    val query3 = s"MATCH (n) WHERE n.documentId = '${knowledgeForParser.knowledge.knowledgeForDocument.id}' DELETE n"
    neo4JUtils.executeQuery(query3, transversalState)
    FeatureVectorizer.removeVector(knowledgeForParser, transversalState)

  }

  private def rollback(knowledgeSentenceSetForParser: KnowledgeSentenceSetForParser, transversalState: TransversalState) = {
    try {
      knowledgeSentenceSetForParser.premiseList.map(deleteObject(_, transversalState))
      knowledgeSentenceSetForParser.claimList.map(deleteObject(_, transversalState))
      logger.info(ToposoidUtils.formatMessageForLogger("RollBack completed", transversalState.userId))
    } catch {
      case e: Exception => {
        logger.error(ToposoidUtils.formatMessageForLogger("RollBack failed: " + Json.toJson(knowledgeSentenceSetForParser).toString(), transversalState.userId), e)
      }
    }
  }


  private def getNonSentenceTuples(propositions: List[List[Knowledge]]): List[(Int, Int, List[String])] = {
    val title = propositions.head.head.knowledgeForDocument.titleOfTopPage
    propositions.foldLeft(List((TITLE_OF_TOP_PAGE.index, -1, List(title)))) {
      (acc, x) => {
        acc ::: x.map(y => {
          val referenceTuple = (REFERENCES.index, y.documentPageReference.pageNo, y.documentPageReference.references)
          val tocTuple = (TABLE_OF_CONTENTS.index, y.documentPageReference.pageNo, y.documentPageReference.tableOfContents)
          val headlineTuple = (HEADLINES.index, y.documentPageReference.pageNo, y.documentPageReference.headlines)
          List(referenceTuple, tocTuple, headlineTuple)
        }).flatten
      }
    }.distinct
  }

  private def addNonSentenceSectionResult(documentRegistration: DocumentRegistration, propositions:List[List[Knowledge]]) = Try {
    getNonSentenceTuples(propositions).foreach(x => {
      if(x._3.size > 0){
        addNonSentenceSectionResultSub(documentRegistration.document.documentId, x._1, x._2, x._3, documentRegistration.transversalState)
      }
    })
  } match {
    case Success(s) => s
    case Failure(e) => throw e
  }


//==================================================================================================================================

  private def analyzePdfDocument(document :Document, transversalState:TransversalState):Propositions = {
    try {
      val propositionsJson: String = ToposoidUtils.callComponent(
        Json.toJson(document).toString(),
        conf.getString("TOPOSOID_CONTENTS_ADMIN_HOST"),
        conf.getString("TOPOSOID_CONTENTS_ADMIN_PORT"),
        "analyzePdfDocument",
        transversalState)
      //toposoid-contents-admin-webでPDFに解析
      val propositions: Propositions = Json.parse(propositionsJson).as[Propositions]
      if(propositions.propositions.size == 0) throw new Exception("There are no analysis results for the document.")
      logger.info(ToposoidUtils.formatMessageForLogger("Completed acceptance of proposition data", transversalState.userId))
      Json.parse(propositionsJson).as[Propositions]
    } catch {
      case e: Exception => {
        logger.error(ToposoidUtils.formatMessageForLogger(e.toString(), transversalState.userId), e)
        throw e
      }
    }
  }

  private def getSurfaceIndex(label:String, predicateArgumentStructures:List[PredicateArgumentStructure], transliterator:Transliterator):(Int, String) = {

    val sortedPredicateArgumentStructures = predicateArgumentStructures.sortBy(_.currentId)
    val convertSurfaces = sortedPredicateArgumentStructures.zipWithIndex.map(x =>  (x._2, x._1.currentId, transliterator.transliterate(x._1.surface).toLowerCase.strip))
    val convertLabel = transliterator.transliterate(label).toLowerCase.strip
    val result = convertSurfaces.foldLeft((-1, -1, "")){
      (acc, x) => {
        //If there are multiple labels with the same label in one sentence, link only the first hit.
        if(acc._1 == -1 && (acc._3 + x._3).contains(convertLabel)){
          (x._1, x._2, acc._3 + x._3)
        }else{
          (acc._1, acc._2, acc._3 + x._3)
        }
      }
    }
    //When parsing a pdf document, a label is temporarily given to the surface, so convert it to the surface given by SentenceParser.
    result._1 match  {
      case -1 => (-1, label)
      case _ => (result._2, sortedPredicateArgumentStructures(result._1).surface)
    }

  }


  private def linkContentLabelIndex(analyzedPropositionPair: AnalyzedPropositionPair): AnalyzedPropositionPair = {
    val transliterator = Transliterator.getInstance("Fullwidth-Halfwidth")
    val analyzedSentenceObjects = analyzedPropositionPair.analyzedSentenceObjects
    val knowledgeForParser = analyzedPropositionPair.knowledgeForParser
    val predicateArgumentStructures: List[PredicateArgumentStructure] = analyzedSentenceObjects.analyzedSentenceObjects.head.nodeMap.map(_._2.predicateArgumentStructure).toList
    val knowledge = analyzedPropositionPair.knowledgeForParser.knowledge
    if (knowledge.knowledgeForImages.size == 0 && knowledge.knowledgeForTables.size == 0) {
      analyzedPropositionPair
    } else {
      val updatedKnowledgeForImages = knowledge.knowledgeForImages.map(x => {
        val label: String = x.imageReference.reference.surface
        val (index ,surface) = getSurfaceIndex(label, predicateArgumentStructures, transliterator)
        val imageRef = x.imageReference
        val ref = imageRef.reference
        val updatedReference = Reference(url = ref.url, surface = surface, surfaceIndex = index, isWholeSentence = ref.isWholeSentence, originalUrlOrReference = ref.originalUrlOrReference)
        val updatedImageReference = ImageReference(reference = updatedReference, x = imageRef.x, y = imageRef.y, width = imageRef.width, height = imageRef.height)
        KnowledgeForImage(id = x.id, imageReference = updatedImageReference)
      })
      val updatedKnowledgeForTables = knowledge.knowledgeForTables.map(x => {
        val label: String = x.tableReference.reference.surface
        val (index ,surface) = getSurfaceIndex(label, predicateArgumentStructures, transliterator)
        val tableRef = x.tableReference
        val ref = tableRef.reference
        val updatedReference = Reference(url = ref.url, surface = surface, surfaceIndex = index, isWholeSentence = ref.isWholeSentence, originalUrlOrReference = ref.originalUrlOrReference)
        val updatedTableReference = TableReference(reference = updatedReference)
        KnowledgeForTable(id = x.id, tableReference = updatedTableReference)
      })
      val updateKnowledge = Knowledge(sentence = knowledge.sentence,
        lang = knowledge.lang, extentInfoJson = knowledge.extentInfoJson, isNegativeSentence = knowledge.isNegativeSentence,
        knowledgeForImages = updatedKnowledgeForImages, knowledgeForTables = updatedKnowledgeForTables, knowledgeForDocument = knowledge.knowledgeForDocument, documentPageReference = knowledge.documentPageReference)
      val updatedKnowledgeForParser = KnowledgeForParser(
        propositionId = knowledgeForParser.propositionId,
        sentenceId = knowledgeForParser.sentenceId, knowledge = updateKnowledge)
      AnalyzedPropositionPair(analyzedSentenceObjects = analyzedPropositionPair.analyzedSentenceObjects, knowledgeForParser = updatedKnowledgeForParser)
    }

  }

  SqsSource(queueUrl, settings)
    .map(MessageAction.Delete(_))
    .via(SqsAckFlow(queueUrl))
    .runWith(Sink.foreach { res: SqsAckResult =>
      val body = res.messageAction.message.body
      val documentRegistration: DocumentRegistration = Json.parse(body).as[DocumentRegistration]
      val document :Document = documentRegistration.document
      val transversalState:TransversalState = documentRegistration.transversalState
      val KNOWLEDGE_REGISTRATION_SUCCESS = 1
      val KNOWLEDGE_REGISTRATION_FAILURE = 2
      val DOCUMENT_REGISTRATION_SUCCESS = 1
      val DOCUMENT_REGISTRATION_FAILURE = 2

      try {
        //PDFドキュメントの解析
        val propositions = analyzePdfDocument(document, transversalState)
        for ((proposition: List[Knowledge], i: Int) <- propositions.propositions.zipWithIndex) {
          //propositionごとに、知識の登録は行われる。
          val (knowledgeSentenceSetForParser, propositionId) = assignId(makeKnowledgeSentenceSet(proposition))
          val knowledgeRegistration: KnowledgeRegistration = KnowledgeRegistration(
            documentId = document.documentId,
            propositionId = propositionId,
            sequentialNumber = propositions.propositions.size,
            transversalState = transversalState)
          try {
            val analyzedPropositionPairs: List[AnalyzedPropositionPair] = knowledgeSentenceSetForParser.claimList.foldLeft(List.empty[AnalyzedPropositionPair]) {
              (acc, x) => {
                //SentenceParserで解析
                val knowledgeForParser: KnowledgeForParser = x
                val inputSentenceForParser = InputSentenceForParser(List.empty[KnowledgeForParser], List(knowledgeForParser))
                val json: String = Json.toJson(inputSentenceForParser).toString()
                val parserInfo: (String, String) = knowledgeForParser.knowledge.lang match {
                  case langPatternJP() => (conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_PORT"))
                  case langPatternEN() => (conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_PORT"))
                  case _ => throw new Exception("It is an invalid locale or an unsupported locale.")
                }
                val parseResult: String = ToposoidUtils.callComponent(json, parserInfo._1, parserInfo._2, "analyze", transversalState)
                val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(parseResult).as[AnalyzedSentenceObjects]

                val analyzedPropositionPair: AnalyzedPropositionPair = AnalyzedPropositionPair(analyzedSentenceObjects, knowledgeForParser)
                //In the analysis results of pdf documents, there is no link between images and table labels and predicate terms.
                acc :+ linkContentLabelIndex(analyzedPropositionPair)
              }
            }
            val classifiedKnowledgeBySentenceType = classifyKnowledgeBySentenceType(
              premiseList = List.empty[AnalyzedPropositionPair],
              premiseLogicRelation = List.empty[PropositionRelation],
              claimList = analyzedPropositionPairs,
              claimLogicRelation = knowledgeSentenceSetForParser.claimLogicRelation
            )
            Sentence2Neo4jTransformer.createGraph(classifiedKnowledgeBySentenceType, transversalState)
            FeatureVectorizer.createVector(knowledgeSentenceSetForParser, transversalState)

            addKnowledgeRegistrationResult(KNOWLEDGE_REGISTRATION_SUCCESS, knowledgeRegistration, knowledgeSentenceSetForParser)
          } catch {
            case e: Exception => {
              logger.error(ToposoidUtils.formatMessageForLogger(e.toString(), transversalState.userId), e)
              rollback(knowledgeSentenceSetForParser, transversalState)
              addKnowledgeRegistrationResult(KNOWLEDGE_REGISTRATION_FAILURE, knowledgeRegistration, knowledgeSentenceSetForParser)
            }
          }
        }
        addNonSentenceSectionResult(documentRegistration: DocumentRegistration, propositions.propositions)
        addDocumentRegistrationResult(DOCUMENT_REGISTRATION_SUCCESS, documentRegistration, propositions.propositions.size)
        logger.info(ToposoidUtils.formatMessageForLogger("Document Registration completed", transversalState.userId))
      } catch {
          case e: Exception => {
            logger.error(ToposoidUtils.formatMessageForLogger(e.toString(), transversalState.userId), e)
            //TODO:mysqlにドキュメントに関しては、failureを登録
            addDocumentRegistrationResult(DOCUMENT_REGISTRATION_FAILURE, documentRegistration, -1)
          }
        }
    })
}


/*
//MQに通知
val queueUrl2 = endpoint + "/" + conf.getString("TOPOSOID_MQ_KNOWLEDGE_REGISTER_QUENE")
val knowledgeRegistration: KnowledgeRegistration = KnowledgeRegistration(
  documentId = document.documentId,
  propositionId = propositionId,
  sequentialNumber = i + 1,
  transversalState = transversalState)
Source
  .single(SendMessageRequest.builder().messageBody(Json.toJson(knowledgeRegistration).toString()).messageGroupId("y").queueUrl(queueUrl2).build())
  .runWith(SqsPublishSink.messageSink())
logger.info(ToposoidUtils.formatMessageForLogger("Data registration to redis completed", transversalState.userId))
 */

/*

private def isMatchLabel(surface:String, label:String):Boolean={
  val pattern: Regex = "((fig|figure|image|table|scheme|図|表|画像)+[\\.\\- 　]*[0-9]+)".r
  val transliterator = Transliterator.getInstance("Fullwidth-Halfwidth")

  val comparableSurface = transliterator.transliterate(surface).strip()
  val comparableLabel = transliterator.transliterate(label).strip()
  val result = pattern.findAllIn(comparableSurface).matchData.filter(x => {
    x.group(1).equals(comparableLabel)
  })
  result.size match  {
    case 0 => false
    case _ => true
  }

}


private def linkContentLabelIndex(analyzedPropositionPair: AnalyzedPropositionPair): AnalyzedPropositionPair = {
  val analyzedSentenceObjects = analyzedPropositionPair.analyzedSentenceObjects
  val knowledgeForParser = analyzedPropositionPair.knowledgeForParser
  val predicateArgumentStructures:List[PredicateArgumentStructure] = analyzedSentenceObjects.analyzedSentenceObjects.head.nodeMap.map(_._2.predicateArgumentStructure).toList
  val knowledge = analyzedPropositionPair.knowledgeForParser.knowledge
  if(knowledge.knowledgeForImages.size == 0 && knowledge.knowledgeForTables.size == 0){
    analyzedPropositionPair
  }else {
    val updatedKnowledgeForImages = knowledge.knowledgeForImages.map(x => {
      val label:String = x.imageReference.reference.surface
      //If there are multiple labels with the same label in one sentence, link only the first hit.
      val predicateArgumentStructure = predicateArgumentStructures.filter(y => isMatchLabel(y.surface, label))
      val index:Int = predicateArgumentStructure.size match {
        case 0  => -1
        case _ => predicateArgumentStructure.head.currentId
      }
      val imageRef = x.imageReference
      val ref = imageRef.reference
      //When parsing a pdf document, a label is temporarily given to the surface, so convert it to the surface given by SentenceParser.
      val surface: String = index match {
        case -1 => ref.surface
        case _ => predicateArgumentStructure.head.surface
      }
      val updatedReference = Reference(url = ref.url, surface = surface, surfaceIndex = index, isWholeSentence = ref.isWholeSentence, originalUrlOrReference = ref.originalUrlOrReference)
      val updatedImageReference = ImageReference(reference = updatedReference, x = imageRef.x, y = imageRef.y, width = imageRef.width, height = imageRef.height)
      KnowledgeForImage(id = x.id, imageReference = updatedImageReference )
    })
    val updatedKnowledgeForTables = knowledge.knowledgeForTables.map(x => {
      val label: String = x.tableReference.reference.surface
      //If there are multiple labels with the same label in one sentence, link only the first hit.
      val predicateArgumentStructure = predicateArgumentStructures.filter(y => isMatchLabel(y.surface, label))
      val index: Int = predicateArgumentStructure.size match {
        case 0 => -1
        case _ => predicateArgumentStructure.head.currentId
      }
      val tableRef = x.tableReference
      val ref = tableRef.reference
      val surface: String = index match {
        case -1 => ref.surface
        case _ => predicateArgumentStructure.head.surface
      }

      val updatedReference = Reference(url = ref.url, surface = surface, surfaceIndex = index, isWholeSentence = ref.isWholeSentence, originalUrlOrReference = ref.originalUrlOrReference)
      val updatedTableReference = TableReference(reference = updatedReference)
      KnowledgeForTable(id = x.id, tableReference = updatedTableReference)
    })
    val updateKnowledge = Knowledge(sentence = knowledge.sentence,
      lang = knowledge.lang, extentInfoJson = knowledge.extentInfoJson, isNegativeSentence = knowledge.isNegativeSentence,
      knowledgeForImages = updatedKnowledgeForImages, knowledgeForTables = updatedKnowledgeForTables, knowledgeForDocument = knowledge.knowledgeForDocument, documentPageReference = knowledge.documentPageReference)
    val updatedKnowledgeForParser = KnowledgeForParser(
      propositionId = knowledgeForParser.propositionId,
      sentenceId = knowledgeForParser.sentenceId, knowledge = updateKnowledge)
    AnalyzedPropositionPair(analyzedSentenceObjects = analyzedPropositionPair.analyzedSentenceObjects, knowledgeForParser = updatedKnowledgeForParser)
  }
}
*/