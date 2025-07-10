import Dependencies._
import de.heikoseeberger.sbtheader.License

ThisBuild / scalaVersion     := "2.13.11"
ThisBuild / version          := "0.6-SNAPSHOT"
ThisBuild / organization     := "com.ideal.linked"
ThisBuild / organizationName := "toposoid-document-analysis-subscriber"

lazy val root = (project in file("."))
  .settings(
    name := "toposoid-document-analysis-subscriber",
    libraryDependencies += scalaTest % Test,
    libraryDependencies += "com.ideal.linked" %% "scala-common" % "0.6-SNAPSHOT",
    libraryDependencies += "com.ideal.linked" %% "toposoid-common" % "0.6-SNAPSHOT",
    libraryDependencies += "com.ideal.linked" %% "toposoid-knowledgebase-model" % "0.6-SNAPSHOT",
    libraryDependencies += "com.ideal.linked" %% "toposoid-deduction-protocol-model" % "0.6-SNAPSHOT",
    libraryDependencies += "com.ideal.linked" %% "toposoid-sentence-transformer-neo4j" % "0.6-SNAPSHOT",
    libraryDependencies += "com.ideal.linked" %% "toposoid-feature-vectorizer" % "0.6-SNAPSHOT",
    libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.6.13",
    libraryDependencies += "com.lightbend.akka" %% "akka-stream-alpakka-sqs" % "2.0.2",
    libraryDependencies += "com.typesafe.play" %% "play-json" % "2.9.2",
    libraryDependencies += "io.jvm.uuid" %% "scala-uuid" % "0.3.1",
    libraryDependencies += "com.ibm.icu" % "icu4j" % "63.1",
      libraryDependencies += "com.softwaremill.sttp.client4" %% "core" % "4.0.9",
  )

organizationName := "Linked Ideal LLC.[https://linked-ideal.com/]"
startYear := Some(2021)
licenses += ("AGPL-3.0-or-later", new URL("http://www.gnu.org/licenses/agpl-3.0.en.html"))
headerLicense := Some(License.AGPLv3("2025", organizationName.value))
