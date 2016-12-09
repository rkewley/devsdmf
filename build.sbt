import sbt.Keys._

import sbtprotobuf.{ProtobufPlugin=>PB}

lazy val simproto = {
  Project("simproto", file("simproto"))
    .settings(
      organization := "DMF",
      version := "3.1-SNAPSHOT",
      crossPaths := false,
      publishTo := Some("Artifactory Realm" at "https://dmf.d-a-s.com/artifactory/libs-snapshot-local;build.timestamp=" + new java.util.Date().getTime),
      PB.protobufSettings,
      version in PB.protobufConfig := "3.0.2"
    )
}

lazy val devsdmf = {
  Project("devs-dmf",file(".")).aggregate(simproto).dependsOn(simproto)
    .settings(
      scalaVersion := "2.11.8",
      organization := "DMF",
      version := "3.1-SNAPSHOT",
      publishTo := Some("Artifactory Realm" at "https://dmf.d-a-s.com/artifactory/libs-snapshot-local;build.timestamp=" + new java.util.Date().getTime),
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-reflect" % scalaVersion.value,
        "com.typesafe.akka" %% "akka-remote" % "2.4.10",
        "com.typesafe.akka" %% "akka-actor" % "2.4.10",
        "com.typesafe.akka" %% "akka-testkit" % "2.4.10",
        "com.typesafe.akka" % "akka-slf4j_2.11" % "2.4.10",
        "ch.qos.logback" % "logback-classic" % "1.1.7",
        "org.scalatest" %% "scalatest" % "2.2.6" % "test",
        "junit" % "junit" % "4.12" % "test",
        "com.novocode" % "junit-interface" % "0.11" % "test"
      ),
      testOptions += Tests.Argument(TestFrameworks.JUnit, "-v"),
      compileOrder := CompileOrder.JavaThenScala,
      // Remove logback.xml from jar file
      mappings in (Compile,packageBin) ~= {
        (ms: Seq[(File,String)]) =>
          ms filter { case (file, toPath) => !(toPath.endsWith("logback.xml")) }
      }
    )
}
