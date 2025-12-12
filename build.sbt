import Dependencies._
import de.heikoseeberger.sbtheader.License

ThisBuild / scalaVersion     := "3.3.6"
ThisBuild / version          := "0.7-SNAPSHOT"
ThisBuild / organization     := "com.ideal.linked"
ThisBuild / organizationName := "toposoid-document-analysis-subscriber"

val AkkaVersion = "2.10.11"
val AkkaHttpVersion = "10.7.3"

lazy val root = (project in file("."))
  .settings(
    name := "toposoid-document-analysis-subscriber",
    resolvers += Resolver.mavenLocal,    
    mainClass := Some("com.ideal.linked.toposoid.mq.DocumentAnalysisSubscriber"),
    libraryDependencies += scalaTest % Test,
    libraryDependencies += "com.ideal.linked" %% "scala-common" % "0.7-SNAPSHOT",
    libraryDependencies += "com.ideal.linked" %% "toposoid-common" % "0.7-SNAPSHOT",
    libraryDependencies += "com.ideal.linked" %% "toposoid-knowledgebase-model" % "0.7-SNAPSHOT",
    libraryDependencies += "com.ideal.linked" %% "toposoid-deduction-protocol-model" % "0.7-SNAPSHOT",
    libraryDependencies += "com.ideal.linked" %% "toposoid-sentence-transformer-neo4j" % "0.7-SNAPSHOT",
    libraryDependencies += "com.ideal.linked" %% "toposoid-feature-vectorizer" % "0.7-SNAPSHOT",
    libraryDependencies += "com.typesafe.akka" %% "akka-stream" % AkkaVersion exclude("org.slf4j","slf4j-api"),
    libraryDependencies += "com.typesafe.akka" %% "akka-pki" % AkkaVersion exclude("org.slf4j","slf4j-api"),    
    libraryDependencies += "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion exclude("org.slf4j","slf4j-api"),    
    libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion exclude("org.slf4j","slf4j-api"),
    libraryDependencies += "com.lightbend.akka" %% "akka-stream-alpakka-sqs" % "10.0.0" exclude("org.slf4j","slf4j-api"),
    libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion exclude("org.slf4j","slf4j-api"),
    libraryDependencies += "org.playframework" %% "play-json" % "3.0.6" exclude("org.slf4j","slf4j-api"),     
    libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.36",
    //libraryDependencies += "io.jvm.uuid" %% "scala-uuid" % "0.3.1",
    libraryDependencies += "com.ibm.icu" % "icu4j" % "63.1",
    libraryDependencies += "com.softwaremill.sttp.client4" %% "core" % "4.0.9"
  )

organizationName := "Linked Ideal LLC.[https://linked-ideal.com/]"
startYear := Some(2021)
licenses += ("AGPL-3.0-or-later", new URL("http://www.gnu.org/licenses/agpl-3.0.en.html"))
headerLicense := Some(License.AGPLv3("2025", organizationName.value))
