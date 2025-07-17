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
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatest.flatspec.AnyFlatSpec
import com.ideal.linked.toposoid.mq.TestUtils.{deleteFeatureVector, deleteNeo4JAllData, searchDocumentAnalysisResultHistoryRecord, searchKnowledgeRegisterHistoryRecord, searchNonSentenceSectionsRecord, uploadDocumentFile, uploadImage}
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.sentence.transformer.neo4j.Sentence2Neo4jTransformer.neo4JUtils.executeQueryAndReturn
import io.jvm.uuid.UUID

import java.nio.file.{Path, Paths}

class SubscriberJapaneseTest extends AnyFlatSpec with BeforeAndAfter with BeforeAndAfterAll{

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

    //val uploadFile: Path = Paths.get("src/test/resources/JAPANESE_DOCUMENT_FOR_TEST.pdf")
    val uploadFile: Path = Paths.get("src/test/resources/real-documents/jp/CONTRACT2.pdf")
    uploadDocumentFile(uploadFile, transversalState)

    val sentence1 = "これは最初のパラグラフの文章です。"
    val sentence2 = "図1はテスト画像です。"
    val sentence3 = "これは2番目のパラグラフの文章です。"
    val sentence4 = "表1は、テストデータです。"
    val title = "Test 用のドキュメント "
    val toc1 = "1. Test用見出し1 ......................................................................................................... 3 "
    val toc2 = "2. Test用見出し2 ......................................................................................................... 4 "
    val toc3 = "3. 参考文献 .................................................................................................................... 5 "
    val reference1 = "1) 佐々木達郎(. 2022). 論文・特許クラスター分析を用いた量子コンピュータの学術研究・技術開発動向調査 , SciREX ワー キングペーパー . http://doi.org/10.24545/00001885. ."
    val reference2 = "2) Gyongyosi, L., & Imre, S. (2019). A Survey on quantum computing technology. Computer Science Review, 31, 51-71. https://doi.org/https://doi.org/10.1016/j.cosrev.2018.11.002."
    val headline1 = "目次"
    val headline2 = "1. Test用見出し1"
    val headline3 = "2. Test用見出し2"
    val headline4 = "3. 参考文献"
    Thread.sleep(60000)
    //NEO4Jの内容チェック
    //ローカルノード
    val result: Neo4jRecords = executeQueryAndReturn("""MATCH x = (:ImageNode)-[:ImageEdge]->(:ClaimNode{surface:'図１は'})-[:LocalEdge]-(:ClaimNode{surface:'テスト画像です。'})-[:LocalEdge]-(:ClaimNode{surface: '文章です。'})-[:LocalEdge]-(:ClaimNode{surface:'パラグラフの'})-[:LocalEdge]-(:ClaimNode{surface:'最初の'}) RETURN x""", transversalState)
    assert(result.records.size == 1)
    val result2: Neo4jRecords = executeQueryAndReturn("""MATCH x = (:TableNode)-[:TableEdge]->(:ClaimNode{surface:'表１は、'})-[:LocalEdge]-(:ClaimNode{surface:'テストデータです。'})-[:LocalEdge]-(:ClaimNode{surface: '文章です。'})-[:LocalEdge]-(:ClaimNode{surface:'パラグラフの'})-[:LocalEdge]-(:ClaimNode{surface:'２番目の'}) RETURN x""", transversalState)
    assert(result2.records.size == 1)
    //センテンスノード
    val result3: Neo4jRecords = executeQueryAndReturn("""MATCH x = (:SemiGlobalClaimNode{sentence:'これは最初のパラグラフの文章です。'})-[:SemiGlobalEdge{logicType:'AND'}]-(:SemiGlobalClaimNode{sentence:'図1はテスト画像です。'}) RETURN x""", transversalState)
    assert(result3.records.size == 1)
    val result4: Neo4jRecords = executeQueryAndReturn("""MATCH x = (:SemiGlobalClaimNode{sentence:'これは2番目のパラグラフの文章です。'})-[:SemiGlobalEdge{logicType:'AND'}]-(:SemiGlobalClaimNode{sentence:'表1は、テストデータです。'}) RETURN x""", transversalState)
    assert(result4.records.size == 1)
    //ドキュメントノード
    val result5: Neo4jRecords = executeQueryAndReturn("""MATCH x = (:GlobalNode{titleOfTopPage:'Test 用のドキュメント '}) RETURN x""", transversalState)
    assert(result5.records.size == 1)

    //素性ベクトルの内容チェック
    val lang = "ja_JP"
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
    val references = List(reference1, reference2)
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
      assert(x.stateId == 1 && x.originalFilename.equals("JAPANESE_DOCUMENT_FOR_TEST.pdf"))
    })

    //文章
    val sentenceRecords = searchKnowledgeRegisterHistoryRecord(documentId: String, transversalState: TransversalState)
    assert(sentenceRecords.size == 3)
    sentenceRecords.foreach(x => {
      assert(x.stateId == 1)
    })

    //non-センテンス
    val nonSentenceRecords = searchNonSentenceSectionsRecord(documentId: String, transversalState: TransversalState)
    assert(nonSentenceRecords.size == 10)


    //ベクトル情報削除
    propositionIds2.foreach(x => {
      deleteFeatureVector(x, SENTENCE, "ja_JP", PROPOSITION_ID.index, transversalState: TransversalState)
      deleteFeatureVector(x, IMAGE, "ja_JP", PROPOSITION_ID.index, transversalState: TransversalState)
    })
    deleteFeatureVector(documentId, NON_SENTENCE, "ja_JP", DOCUMENT_ID.index, transversalState: TransversalState)


  }
}
