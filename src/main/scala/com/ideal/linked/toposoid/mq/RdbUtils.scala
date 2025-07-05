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
import com.ideal.linked.toposoid.common.{ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.common.mq.{DocumentRegistration, KnowledgeRegistration}
import com.ideal.linked.toposoid.knowledgebase.regist.rdb.model.{DocumentAnalysisResultHistoryRecord, KnowledgeRegisterHistoryRecord, NonSentenceSectionsRecord}
import com.ideal.linked.toposoid.protocol.model.parser.KnowledgeSentenceSetForParser
import play.api.libs.json.Json

import scala.util.{Failure, Success, Try}

object RdbUtils {
  def addDocumentRegistrationResult(stateId: Int, documentRegistration: DocumentRegistration, totalSeparatedNumber: Int): Unit = Try {

    val documentAnalysisResultHistoryRecord = DocumentAnalysisResultHistoryRecord(
      stateId = stateId,
      documentId = documentRegistration.document.documentId,
      originalFilename = documentRegistration.document.filename,
      totalSeparatedNumber = totalSeparatedNumber
    )
    val json = Json.toJson(documentAnalysisResultHistoryRecord).toString()
    val result = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_RDB_WEB_HOST"), conf.getString("TOPOSOID_RDB_WEB_PORT"), "addDocumentAnalysisResultHistory", documentRegistration.transversalState)
    if (result.contains("Error")) throw new Exception(result)
  } match {
    case Success(s) => s
    case Failure(e) => throw e
  }

  def addKnowledgeRegistrationResult(stateId: Int, knowledgeRegistration: KnowledgeRegistration, knowledgeSentenceSetForParser: KnowledgeSentenceSetForParser): Unit = Try {
    val knowledgeRegisterHistoryRecord = KnowledgeRegisterHistoryRecord(
      stateId = stateId,
      documentId = knowledgeRegistration.documentId,
      sequentialNumber = knowledgeRegistration.sequentialNumber,
      propositionId = knowledgeSentenceSetForParser.claimList.head.propositionId,
      sentences = getSentence(knowledgeSentenceSetForParser),
      json = Json.toJson(knowledgeRegistration).toString())
    val json = Json.toJson(knowledgeRegisterHistoryRecord).toString()
    val result = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_RDB_WEB_HOST"), conf.getString("TOPOSOID_RDB_WEB_PORT"), "addKnowledgeRegisterHistory", knowledgeRegistration.transversalState)
    if (result.contains("Error")) throw new Exception(result)
  } match {
    case Success(s) => s
    case Failure(e) => throw e
  }

  def addNonSentenceSectionResultSub(documentId: String, nonSentenceType: Int, pageNo: Int, nonSentences: List[String], transversalState: TransversalState): Unit = Try {
    nonSentences.foreach(y => {
      val nonSentenceSectionsRecord = NonSentenceSectionsRecord(
        nonSentenceType = nonSentenceType,
        documentId = documentId,
        pageNo = pageNo,
        nonSentence = y
      )
      val json = Json.toJson(nonSentenceSectionsRecord).toString()
      val result = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_RDB_WEB_HOST"), conf.getString("TOPOSOID_RDB_WEB_PORT"), "addNonSentenceSections", transversalState)
      if (result.contains("Error")) throw new Exception(result)
    })

  } match {
    case Success(s) => s
    case Failure(e) => throw e
  }

  private def getSentence(knowledgeSentenceSetForParser: KnowledgeSentenceSetForParser): String = {
    val premiseSentence = knowledgeSentenceSetForParser.premiseList.foldLeft("") {
      (acc, x) => {
        acc + x.knowledge.sentence
      }
    }
    val claimSentence = knowledgeSentenceSetForParser.claimList.foldLeft("") {
      (acc, x) => {
        acc + x.knowledge.sentence
      }
    }
    premiseSentence + claimSentence
  }

}
