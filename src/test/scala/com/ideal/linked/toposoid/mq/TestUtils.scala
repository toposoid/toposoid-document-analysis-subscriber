package com.ideal.linked.toposoid.mq

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Post
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ContentTypes, Multipart}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common._
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.{FeatureVectorIdentifier, FeatureVectorSearchResult, RegistContentResult, SingleFeatureVectorForSearch}
import com.ideal.linked.toposoid.knowledgebase.image.model.SingleImage
import com.ideal.linked.toposoid.knowledgebase.nlp.model.{FeatureVector, SingleSentence}
import com.ideal.linked.toposoid.knowledgebase.regist.model.KnowledgeForImage
import com.ideal.linked.toposoid.knowledgebase.regist.rdb.model.{DocumentAnalysisResultHistoryRecord, KnowledgeRegisterHistoryRecord, NonSentenceSectionsRecord}
import play.api.libs.json.Json

import java.nio.file.Path
import scala.util.matching.Regex
import scala.util.{Failure, Success}

object TestUtils {
  //val langPatternJP: Regex = "^ja_.*".r
  //val langPatternEN: Regex = "^en_.*".r

  def deleteNeo4JAllData(transversalState: TransversalState): Unit = {
    val query = "MATCH (n) OPTIONAL MATCH (n)-[r]-() DELETE n,r"
    val neo4JUtils = new Neo4JUtilsImpl()
    neo4JUtils.executeQuery(query, transversalState)
  }

  def deleteFeatureVector(superiorId:String, featureType: FeatureType, lang:String, superiorType:Int, transversalState: TransversalState): Unit = {
    val featureVectorIdentifier: FeatureVectorIdentifier = FeatureVectorIdentifier(superiorId = superiorId, featureId = "-", sentenceType = -1, lang = lang , superiorType = superiorType, nonSentenceType = 0)
    val json: String = Json.toJson(featureVectorIdentifier).toString()
    if (featureType.equals(SENTENCE)) {
      ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "deleteBySuperiorId", transversalState)
    } else if (featureType.equals(IMAGE)) {
      ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "deleteBySuperiorId", transversalState)
    } else if (featureType.equals(NON_SENTENCE)){
      ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_NON_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_NON_SENTENCE_VECTORDB_ACCESSOR_PORT"), "deleteBySuperiorId", transversalState)
    }
  }

  def uploadDocumentFile(file:Path, transversalState:TransversalState): String = {

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()

    val uri = "http://" + conf.getString("TOPOSOID_CONTENTS_ADMIN_HOST") + ":" + conf.getString("TOPOSOID_CONTENTS_ADMIN_PORT") + "/uploadDocumentFile"

    val formData = Multipart.FormData(
      //Multipart.FormData.BodyPart.fromPath(file.getFileName.toString, ContentTypes.`application/octet-stream`, file)
      Multipart.FormData.BodyPart.fromPath("uploadfile", ContentTypes.`application/octet-stream`, file)
    )

    val request = Post(uri, entity=formData.toEntity()).withHeaders(RawHeader(TRANSVERSAL_STATE.str, Json.toJson(transversalState).toString()))
    val result = Http().singleRequest(request)
      .flatMap { res =>
        Unmarshal(res).to[String].map { data =>
          Json.parse(data.getBytes("UTF-8"))
        }
      }
    var queryResultJson: String = "{}"
    result.onComplete {
      case Success(js) =>
        println(s"Success: $js")
        queryResultJson = s"$js"
      case Failure(e) =>
        println(s"Failure: $e")
    }
    while (!result.isCompleted) {
      Thread.sleep(20)
    }
    queryResultJson

  }

  def searchSentenceVector(targets:List[String], lang:String, transversalState: TransversalState):List[FeatureVectorSearchResult] ={
    targets.map(x => {
      val singleSentence = SingleSentence(sentence = x)
      val json: String = Json.toJson(singleSentence).toString()
      val commonNLPInfo: (String, String) = lang match {
        case ToposoidUtils.langPatternJP() => (conf.getString("TOPOSOID_COMMON_NLP_JP_WEB_HOST"), conf.getString("TOPOSOID_COMMON_NLP_JP_WEB_PORT"))
        case ToposoidUtils.langPatternEN() => (conf.getString("TOPOSOID_COMMON_NLP_EN_WEB_HOST"), conf.getString("TOPOSOID_COMMON_NLP_EN_WEB_PORT"))
        case _ => throw new Exception("It is an invalid locale or an unsupported locale.")
      }
      val featureVectorJson: String = ToposoidUtils.callComponent(json, commonNLPInfo._1, commonNLPInfo._2, "getFeatureVector",transversalState)
      val vector: FeatureVector = Json.parse(featureVectorJson).as[FeatureVector]
      val searchOb = SingleFeatureVectorForSearch(vector = vector.vector, num = 10)
      val searchJson = Json.toJson(searchOb).toString()
      val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(searchJson, conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "search", transversalState)
      Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]
    })
  }

  def searchNonSentenceVector(targets:List[String], lang:String, transversalState: TransversalState):List[FeatureVectorSearchResult] = {
    targets.map(x => {
      val singleSentence = SingleSentence(sentence = x)
      val json: String = Json.toJson(singleSentence).toString()
      val commonNLPInfo: (String, String) = lang match {
        case ToposoidUtils.langPatternJP() => (conf.getString("TOPOSOID_COMMON_NLP_JP_WEB_HOST"), conf.getString("TOPOSOID_COMMON_NLP_JP_WEB_PORT"))
        case ToposoidUtils.langPatternEN() => (conf.getString("TOPOSOID_COMMON_NLP_EN_WEB_HOST"), conf.getString("TOPOSOID_COMMON_NLP_EN_WEB_PORT"))
        case _ => throw new Exception("It is an invalid locale or an unsupported locale.")
      }
      val featureVectorJson: String = ToposoidUtils.callComponent(json, commonNLPInfo._1, commonNLPInfo._2, "getFeatureVector", transversalState)
      val vector: FeatureVector = Json.parse(featureVectorJson).as[FeatureVector]
      val searchOb = SingleFeatureVectorForSearch(vector = vector.vector, num = 10)
      val searchJson = Json.toJson(searchOb).toString()
      val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(searchJson, conf.getString("TOPOSOID_NON_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_NON_SENTENCE_VECTORDB_ACCESSOR_PORT"), "search", transversalState)
      Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]
    })
  }

  def searchImageVector(url: String, transversalState: TransversalState): FeatureVectorSearchResult = {
    val singleImage = SingleImage(url)
    val json: String = Json.toJson(singleImage).toString()
    val featureVectorJson: String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_COMMON_IMAGE_RECOGNITION_HOST"), conf.getString("TOPOSOID_COMMON_IMAGE_RECOGNITION_PORT"), "getFeatureVector", transversalState)
    val vector: FeatureVector = Json.parse(featureVectorJson).as[FeatureVector]
    val searchOb = SingleFeatureVectorForSearch(vector = vector.vector, num = 10)
    val searchJson = Json.toJson(searchOb).toString()
    val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(searchJson, conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "search", transversalState)
    Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]
  }


  def searchKnowledgeRegisterHistoryRecord(documentId: String, transversalState: TransversalState): List[KnowledgeRegisterHistoryRecord] = {
    val knowledgeRegisterHistoryRecord = KnowledgeRegisterHistoryRecord(
      stateId = 1,
      documentId = documentId,
      sequentialNumber = -1,
      propositionId = "",
      sentences = "",
      json = "")
    val json = Json.toJson(knowledgeRegisterHistoryRecord).toString()
    val result = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_RDB_WEB_HOST"), conf.getString("TOPOSOID_RDB_WEB_PORT"), "searchKnowledgeRegisterHistoryByDocumentId", transversalState)
    Json.parse(result).as[List[KnowledgeRegisterHistoryRecord]]
  }

  def searchDocumentAnalysisResultHistoryRecord(documentId: String, transversalState: TransversalState): List[DocumentAnalysisResultHistoryRecord] = {
    val documentAnalysisResultHistoryRecord = DocumentAnalysisResultHistoryRecord(
      stateId = 1,
      documentId = documentId,
      originalFilename = "",
      totalSeparatedNumber = 0
    )
    val json = Json.toJson(documentAnalysisResultHistoryRecord).toString()
    val result = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_RDB_WEB_HOST"), conf.getString("TOPOSOID_RDB_WEB_PORT"), "searchDocumentAnalysisResultHistoryByDocumentIdAndStateId", transversalState)
    Json.parse(result).as[List[DocumentAnalysisResultHistoryRecord]]
  }

  def searchNonSentenceSectionsRecord(documentId: String, transversalState: TransversalState): List[NonSentenceSectionsRecord] = {
    val nonSentenceSectionsRecord = NonSentenceSectionsRecord(
      nonSentenceType = 0,
      documentId = documentId,
      pageNo = -1,
      nonSentence = ""
    )
    val json = Json.toJson(nonSentenceSectionsRecord).toString()
    val result = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_RDB_WEB_HOST"), conf.getString("TOPOSOID_RDB_WEB_PORT"), "searchNonSentenceSectionsByDocumentId", transversalState)
    Json.parse(result).as[List[NonSentenceSectionsRecord]]
  }

  def uploadImage(knowledgeForImage: KnowledgeForImage, transversalState: TransversalState): KnowledgeForImage = {
    val registContentResultJson = ToposoidUtils.callComponent(
      Json.toJson(knowledgeForImage).toString(),
      conf.getString("TOPOSOID_CONTENTS_ADMIN_HOST"),
      conf.getString("TOPOSOID_CONTENTS_ADMIN_PORT"),
      "uploadTemporaryImage",
      transversalState)
    val registContentResult: RegistContentResult = Json.parse(registContentResultJson).as[RegistContentResult]
    registContentResult.knowledgeForImage
  }


}
