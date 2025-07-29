/*
 * Copyright (C) 2025  Linked Ideal LLC.[https://linked-ideal.com/]
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ideal.linked.toposoid.mq

import com.ideal.linked.toposoid.common.{DOCUMENT_ID, FeatureType, IMAGE, NON_SENTENCE, PROPOSITION_ID, SENTENCE, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{ImageReference, KnowledgeForImage, Reference}
import com.ideal.linked.toposoid.mq.TestUtils.uploadImage
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatest.flatspec.AnyFlatSpec
import com.ideal.linked.toposoid.mq.TestUtils.{deleteFeatureVector, deleteNeo4JAllData, searchDocumentAnalysisResultHistoryRecord, searchKnowledgeRegisterHistoryRecord, searchNonSentenceSectionsRecord, uploadDocumentFile}
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.sentence.transformer.neo4j.Sentence2Neo4jTransformer.neo4JUtils.executeQueryAndReturn
import io.jvm.uuid.UUID

import java.nio.file.{Path, Paths}

class SubscriberEnglishTest extends AnyFlatSpec with BeforeAndAfter with BeforeAndAfterAll{

  val transversalState: TransversalState = TransversalState(userId = "test-user", username = "guest", roleId = 0, csrfToken = "")

  before {
    deleteNeo4JAllData(transversalState)
  }

  override def beforeAll(): Unit = {
    deleteNeo4JAllData(transversalState)
  }

  override def afterAll(): Unit = {
    deleteNeo4JAllData(transversalState)
  }


  "the request json" should "be properly analyzed" in {

    val uploadFile: Path = Paths.get("src/test/resources/ENGLISH_DOCUMENT_FOR_TEST.pdf")
    uploadDocumentFile(uploadFile, transversalState)

    val sentence1 = "This is the text of the first paragraph."
    val sentence2 = "Table 1 is the test data."
    val sentence3 = "This is the text of the second paragraph."
    val sentence4 = "Figure1 is a test image."
    val title = "Document for Test"
    val toc1 = "I. Test heading 1 .............................................................................................................. 3 "
    val toc2 = "II. Test heading 2.......................................................................................................... 4 "
    val toc3 = "III. References ................................................................................................................ 5 "
    val reference1 = "[XLS+23] Guangxuan Xiao, Ji Lin, Mickaël Seznec, Hao Wu, Julien Demouth, and Song Han. SmoothQuant: accurate and efficient post-training quantization for large language models. In International Conference on Machine Learning, ICML 2023, 23-29 July 2023, Honolulu, Hawaii, USA, 2023. "
    val reference2 = "[YBS19] Vikas Yadav, Steven Bethard, and Mihai Surdeanu. Quick and (not so) dirty: Unsuper- vised selection of justification sentences for multi-hop question answering. In Kentaro Inui, Jing Jiang, Vincent Ng, and Xiaojun Wan, editors, EMNLP-IJCNLP, 2019. "
    val reference3 = "[ZHB+19] Rowan Zellers, Ari Holtzman, Yonatan Bisk, Ali Farhadi, and Yejin Choi. HellaSwag: can a machine really finish your sentence? In Proceedings of the 57th Conference of the Association for Computational Linguistics, pages 4791–4800, 2019."
    val headline1 = "Contents"
    val headline2 = "I. Test heading 1"
    val headline3 = "II. Test heading 2"
    val headline4 = "III. References"
    Thread.sleep(60000)
    //NEO4Jの内容チェック
    //ローカルノード
    val result: Neo4jRecords = executeQueryAndReturn("""MATCH x = (:ImageNode)-[:ImageEdge]->(:ClaimNode{surface:'Figure1'})-[:LocalEdge]-(:ClaimNode{surface: 'is'})-[:LocalEdge]-(:ClaimNode{surface: 'is'})-[:LocalEdge]-(:ClaimNode{surface: 'text'})-[:LocalEdge]-(:ClaimNode{surface:'of'})-[:LocalEdge]-(:ClaimNode{surface:'paragraph'})-[:LocalEdge]-(:ClaimNode{surface:'first'}) RETURN x""", transversalState)
    assert(result.records.size == 1)
    val result2: Neo4jRecords = executeQueryAndReturn("""MATCH x = (:TableNode)-[:TableEdge]->(:ClaimNode{surface:'1'})-[:LocalEdge]-(:ClaimNode{surface:'Table'})-[:LocalEdge]-(:ClaimNode{surface: 'is'})-[:LocalEdge]-(:ClaimNode{surface:'is'})-[:LocalEdge]-(:ClaimNode{surface:'text'})-[:LocalEdge]-(:ClaimNode{surface:'of'})-[:LocalEdge]-(:ClaimNode{surface:'paragraph'})-[:LocalEdge]-(:ClaimNode{surface:'second'}) return x""", transversalState)
    assert(result2.records.size == 1)
    //センテンスノード
    val result3: Neo4jRecords = executeQueryAndReturn("""MATCH x = (:SemiGlobalClaimNode{sentence:'This is the text of the first paragraph.'})-[:SemiGlobalEdge{logicType:'AND'}]-(:SemiGlobalClaimNode{sentence:'Figure1 is a test image.'}) RETURN x""", transversalState)
    assert(result3.records.size == 1)
    val result4: Neo4jRecords = executeQueryAndReturn("""MATCH x = (:SemiGlobalClaimNode{sentence:'This is the text of the second paragraph.'})-[:SemiGlobalEdge{logicType:'AND'}]-(:SemiGlobalClaimNode{sentence:'Table 1 is the test data.'}) RETURN x""", transversalState)
    assert(result4.records.size == 1)
    //ドキュメントノード
    val result5: Neo4jRecords = executeQueryAndReturn("""MATCH x = (:GlobalNode{titleOfTopPage:'Document For Test '}) RETURN x""", transversalState)
    assert(result5.records.size == 1)

    //素性ベクトルの内容チェック
    val lang = "en_US"
    //センテンス
    val sentences = List(sentence1, sentence2, sentence3, sentence4)
    val searchSentences = TestUtils.searchSentenceVector(sentences, lang, transversalState)
    val propositionIds = searchSentences.foldLeft(List.empty[String]) {
      (acc, x) => {
        acc ::: x.ids.map(_.superiorId)
      }
    }.distinct
    assert(searchSentences.size == sentences.size)
    //イメージ
    val reference = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = false, originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg", metaInformations = List.empty[String])
    val imageReference = ImageReference(reference = reference, x = 0, y = 0, width = 640, height = 480)
    val knowledgeForImage = KnowledgeForImage(id = UUID.random.toString, imageReference = imageReference)
    val imageInfo = uploadImage(knowledgeForImage, transversalState)
    val searchImages = TestUtils.searchImageVector(imageInfo.imageReference.reference.url, transversalState)
    assert(searchImages.ids.size == 1)

    //non-センテンス
    val titles = List(title)
    val tocs = List(toc1, toc2, toc3)
    val references = List(reference1, reference2, reference3)
    val headlines = List(headline1, headline2, headline3, headline4)
    val searchTitle = TestUtils.searchNonSentenceVector(titles, lang, transversalState)
    val documentId = searchTitle.head.ids.head.superiorId
    assert(searchTitle.size == titles.size)
    assert(TestUtils.searchNonSentenceVector(tocs, lang, transversalState).size == tocs.size)
    assert(TestUtils.searchNonSentenceVector(references, lang, transversalState).size == references.size)
    assert(TestUtils.searchNonSentenceVector(headlines, lang, transversalState).size == headlines.size)

    //NO_REFERENCEパラグラフ
    val noReferences = List("NO_REFERENCE_" + documentId + "_2", "NO_REFERENCE_" + documentId + "_5")
    val searchNoReferences = TestUtils.searchSentenceVector(noReferences, lang, transversalState)
    val propositionIds2 = searchNoReferences.foldLeft(propositionIds) {
      (acc, x) => {
        acc ::: x.ids.map(_.superiorId)
      }
    }.distinct
    assert(searchNoReferences.size == noReferences.size)

    //MYSQLの内容チェック
    //ドキュメント
    val docRecords = searchDocumentAnalysisResultHistoryRecord(documentId, transversalState)
    docRecords.foreach(x => {
      assert(x.stateId == 1 && x.originalFilename.equals("ENGLISH_DOCUMENT_FOR_TEST.pdf"))
    })

    //文章
    val sentenceRecords = searchKnowledgeRegisterHistoryRecord(documentId: String, transversalState: TransversalState)
    assert(sentenceRecords.size == 3)
    sentenceRecords.foreach(x => {
      assert(x.stateId == 1)
    })

    //non-センテンス
    val nonSentenceRecords = searchNonSentenceSectionsRecord(documentId: String, transversalState: TransversalState)
    assert(nonSentenceRecords.size == 11)


    //ベクトル情報削除
    propositionIds2.foreach(x => {
      deleteFeatureVector(x, SENTENCE, lang, PROPOSITION_ID.index, transversalState: TransversalState)
      deleteFeatureVector(x, IMAGE, lang, PROPOSITION_ID.index, transversalState: TransversalState)
    })
    deleteFeatureVector(documentId, NON_SENTENCE, lang, DOCUMENT_ID.index, transversalState: TransversalState)


  }
}
