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

import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.{FeatureType, TransversalState, Neo4JUtilsImpl, CaseGroupType, ToposoidUtils, TRANSVERSAL_STATE}
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.{FeatureVectorIdentifier, FeatureVectorSearchResult, SingleFeatureVectorForSearch}
import com.ideal.linked.toposoid.knowledgebase.image.model.SingleImage
import com.ideal.linked.toposoid.knowledgebase.nlp.model.{FeatureVector, SingleSentence}
import com.ideal.linked.toposoid.knowledgebase.regist.model.KnowledgeForImage
import com.ideal.linked.toposoid.knowledgebase.regist.rdb.model.{DocumentAnalysisResultHistoryRecord, KnowledgeRegisterHistoryRecord, NonSentenceSectionsRecord}
import play.api.libs.json.Json

import java.nio.file.Path
import scala.util.matching.Regex
import scala.util.{Failure, Success}
import sttp.client4._
import sttp.model._
import java.io.File
import scala.concurrent.duration.{Duration, DurationInt}
import com.ideal.linked.toposoid.knowledgebase.document.model.Document
import play.api.libs.json.{Json, OWrites, Reads}
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.StatusInfo
import com.ideal.linked.toposoid.knowledgebase.regist.model.Reference
import com.ideal.linked.toposoid.knowledgebase.regist.model.ImageReference
import com.ideal.linked.toposoid.knowledgebase.regist.model.KnowledgeForTable
import java.nio.file.Paths
import com.ideal.linked.toposoid.knowledgebase.regist.model.TableReference
import com.ideal.linked.toposoid.knowledgebase.table.model.SingleTable


case class UploadContentContext(featureType:Int, url:String = "")
object UploadContentContext {
  implicit val jsonWrites: OWrites[UploadContentContext] = Json.writes[UploadContentContext]
  implicit val jsonReads: Reads[UploadContentContext] = Json.reads[UploadContentContext]
}

case class UploadResult(id: String, url:String, status:Int)
object UploadResult {
  implicit val jsonWrites: OWrites[UploadResult] = Json.writes[UploadResult]
  implicit val jsonReads: Reads[UploadResult] = Json.reads[UploadResult]
}

case class RegistDocumentContentResult(document:Document, statusInfo:StatusInfo)
object RegistDocumentContentResult {
  implicit val jsonWrites: OWrites[RegistDocumentContentResult] = Json.writes[RegistDocumentContentResult]
  implicit val jsonReads: Reads[RegistDocumentContentResult] = Json.reads[RegistDocumentContentResult]
}


object TestUtils {
  //val langPatternJP: Regex = "^ja_.*".r
  //val langPatternEN: Regex = "^en_.*".r

  def deleteNeo4JAllData(transversalState: TransversalState): Unit = {
    val query = "MATCH (n) OPTIONAL MATCH (n)-[r]-() DELETE n,r"
    val neo4JUtils = new Neo4JUtilsImpl()
    neo4JUtils.executeQuery(query, transversalState)
  }

  def deleteFeatureVector(superiorId:String, featureType: FeatureType, lang:String, superiorType:Int, transversalState: TransversalState): Unit = {
    val featureVectorIdentifier: FeatureVectorIdentifier = FeatureVectorIdentifier(superiorId = superiorId, featureId = "-", sentenceType = -1, lang = lang , superiorType = superiorType, nonSentenceType = 0, caseGroupType = 0)
    val json: String = Json.toJson(featureVectorIdentifier).toString()
    if (featureType.equals(FeatureType.SENTENCE)) {
      ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "deleteBySuperiorId", transversalState)
    } else if (featureType.equals(FeatureType.IMAGE)) {
      ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "deleteBySuperiorId", transversalState)
    } else if (featureType.equals(FeatureType.NON_SENTENCE)){
      ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_NON_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_NON_SENTENCE_VECTORDB_ACCESSOR_PORT"), "deleteBySuperiorId", transversalState)
    } else if (featureType.equals(FeatureType.TABLE)){
      ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_TABLE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_TABLE_VECTORDB_ACCESSOR_PORT"), "deleteBySuperiorId", transversalState)
    }
  }

  def uploadDocumentFile(file:Path, transversalState:TransversalState): String = {
    val filePath = new File("/app/toposoid-document-analysis-subscriber/src/test/resources/JAPANESE_DOCUMENT_FOR_TEST.pdf") // Replace with your file path
    val fileName = "JAPANESE_DOCUMENT_FOR_TEST.pdf" // The name the file will have on the server
    val endpoint = "http://" + conf.getString("TOPOSOID_FILE_UPLOAD_FACADE_HOST") + ":" + conf.getString("TOPOSOID_FILE_UPLOAD_FACADE_PORT") + "/upload"    
    val backend = DefaultSyncBackend(
      options = BackendOptions.connectionTimeout(1.minute))
    val request = basicRequest
    .header(TRANSVERSAL_STATE.str, Json.toJson(transversalState).toString())      
    .httpVersion(HttpVersion.HTTP_1_1)
    .post(uri"${endpoint}") // Replace with your upload endpoint
    .multipartBody(
        multipart("featureType", FeatureType.DOCUMENT.index.toString),
        multipart("url", ""), // デフォルト値を明示的に送る場合      
        multipartFile("uploadfile", file.toFile()).fileName(file.getFileName().toString()).contentType("application/octet-stream") // "file" is the field name on the server
    )
    val response = request.send(backend)
    val responseJson = response.body match {
      case Right(successBody) => s"$successBody"
      case Left(errorBody) => s"Upload failed. Status code: ${response.code}. Error body: $errorBody"
    }

    val uploadResult = Json.parse(responseJson).as[UploadResult]
    val endpoint2 = "http://" + conf.getString("TOPOSOID_CONTENTS_ADMIN_HOST") + ":" + conf.getString("TOPOSOID_CONTENTS_ADMIN_PORT") + "/registerDocument"
    val request2 = basicRequest
    .header(TRANSVERSAL_STATE.str, Json.toJson(transversalState).toString())      
    .httpVersion(HttpVersion.HTTP_1_1)
    .contentType("application/json")
    .post(uri"${endpoint2}") // Replace with your upload endpoint
    .body(Json.toJson(Document(documentId = "", filename="",  url = uploadResult.url, size = 0)).toString)
    val response2 = request2.send(backend)
    val responseJson2 = response2.body match {
      case Right(successBody) => s"$successBody"
      case Left(errorBody) => s"Upload failed. Status code: ${response.code}. Error body: $errorBody"
    }    
    val registDocumentContentResult = Json.parse(responseJson2).as[RegistDocumentContentResult]

    ""

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

  def searchTableVector(url: String, transversalState: TransversalState): FeatureVectorSearchResult = {
    val singleTable = SingleTable(url)
    val json: String = Json.toJson(singleTable).toString()
    val featureVectorJson: String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_COMMON_TABLE_RECOGNITION_HOST"), conf.getString("TOPOSOID_COMMON_TABLE_RECOGNITION_PORT"), "getFeatureVector", transversalState)
    val vector: FeatureVector = Json.parse(featureVectorJson).as[FeatureVector]
    val searchOb = SingleFeatureVectorForSearch(vector = vector.vector, num = 10)
    val searchJson = Json.toJson(searchOb).toString()
    val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(searchJson, conf.getString("TOPOSOID_TABLE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_TABLE_VECTORDB_ACCESSOR_PORT"), "search", transversalState)
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
    
    val endpoint = "http://" + conf.getString("TOPOSOID_FILE_UPLOAD_FACADE_HOST") + ":" + conf.getString("TOPOSOID_FILE_UPLOAD_FACADE_PORT") + "/upload"    
    val backend = DefaultSyncBackend(
      options = BackendOptions.connectionTimeout(1.minute))
    val request = basicRequest
    .header(TRANSVERSAL_STATE.str, Json.toJson(transversalState).toString())      
    .httpVersion(HttpVersion.HTTP_1_1)
    .post(uri"${endpoint}") // Replace with your upload endpoint
    .multipartBody(
        multipart("featureType", FeatureType.IMAGE.index.toString),
        multipart("url", knowledgeForImage.imageReference.reference.originalUrlOrReference), // デフォルト値を明示的に送る場合              
    )
    val response = request.send(backend)
    val responseJson = response.body match {
      case Right(successBody) => s"$successBody"
      case Left(errorBody) => s"Upload failed. Status code: ${response.code}. Error body: $errorBody"
    }

    val uploadResult = Json.parse(responseJson).as[UploadResult]

    val reference = Reference(url = uploadResult.url, surface = "", surfaceIndex = -1, isWholeSentence = false, originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg", metaInformations = List.empty[String])
    val imageReference = ImageReference(reference = reference, x = 0, y = 0, width = 640, height = 480)
    KnowledgeForImage(id = uploadResult.id, imageReference = imageReference)
  }

  def uploadTable(file:Path, transversalState: TransversalState): KnowledgeForTable = {

    val endpoint = "http://" + conf.getString("TOPOSOID_FILE_UPLOAD_FACADE_HOST") + ":" + conf.getString("TOPOSOID_FILE_UPLOAD_FACADE_PORT") + "/upload"    
    val backend = DefaultSyncBackend(
      options = BackendOptions.connectionTimeout(1.minute))
    val request = basicRequest
    .header(TRANSVERSAL_STATE.str, Json.toJson(transversalState).toString())      
    .httpVersion(HttpVersion.HTTP_1_1)
    .post(uri"${endpoint}") // Replace with your upload endpoint
    .multipartBody(
        multipart("featureType", FeatureType.TABLE.index.toString),
        multipart("url", ""), // デフォルト値を明示的に送る場合     
        multipartFile("uploadfile", file.toFile()).fileName(file.getFileName().toString()).contentType("application/octet-stream") // "file" is the field name on the server         
    )
    val response = request.send(backend)
    val responseJson = response.body match {
      case Right(successBody) => s"$successBody"
      case Left(errorBody) => s"Upload failed. Status code: ${response.code}. Error body: $errorBody"
    }

    val uploadResult = Json.parse(responseJson).as[UploadResult]
    val reference = Reference(url = uploadResult.url, surface = "", surfaceIndex = -1, isWholeSentence = false, originalUrlOrReference = file.getFileName().toString(), metaInformations = List.empty[String])
    val tableReference = TableReference(reference=reference)
    KnowledgeForTable(id = uploadResult.id, tableReference = tableReference)

  }

}

