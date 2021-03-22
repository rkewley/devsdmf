import sbt.Keys._

val akkaVersion = "2.6.4"

lazy val simproto = {
  Project("simproto", file("simproto"))
    .settings(
      scalaVersion := "2.13.1",
      organization := "DMF",
      version := "3.8-SNAPSHOT",
      crossPaths := false,
      credentials += Credentials(Path.userHome / ".ivy2" / ".nexus"),
      publishTo := Some("snapshots" at "https://nexus.d-a-s.com/repository/maven-snapshots/"),
      version in ProtobufConfig := "3.9.1"
    )
    .enablePlugins(ProtobufPlugin)
}

lazy val devsdmf = {
  Project("devs-dmf",file(".")).aggregate(simproto).dependsOn(simproto)
    .settings(
      scalaVersion := "2.12.11",
      organization := "DMF",
      version := "3.8-SNAPSHOT",
      version in ProtobufConfig := "3.9.1",
      credentials += Credentials(Path.userHome / ".ivy2" / ".nexus"),
      publishTo := Some("snapshots" at "https://nexus.d-a-s.com/repository/maven-snapshots/"),
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-reflect" % scalaVersion.value,
        "com.typesafe.akka" %% "akka-remote" % akkaVersion,
        "com.typesafe.akka" %% "akka-actor" % akkaVersion,
        "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
        "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
        "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
        "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
        "com.typesafe.akka" %% "akka-stream" % akkaVersion,
        "com.lightbend.akka" %% "akka-stream-alpakka-mongodb" % "2.0.1",
        "ch.qos.logback" % "logback-classic" % "1.2.3",
        "org.mongodb" % "mongodb-driver-reactivestreams" % "1.13.1",
        "com.google.protobuf" % "protobuf-java-util" % (version in ProtobufConfig).value,
        "com.google.code.gson" % "gson" % "2.8.6",
        "org.scalactic" %% "scalactic" % "3.1.1",
        "org.scalatest" %% "scalatest" % "3.1.1" % "test"
      ),
      testOptions += Tests.Argument(TestFrameworks.JUnit, "-v"),
      compileOrder := CompileOrder.JavaThenScala,
      // Remove logback.xml from jar file
      mappings in (Compile,packageBin) ~= {
        (ms: Seq[(File,String)]) =>
          ms filter { case (file, toPath) => !(toPath.endsWith("logback.xml")) }
      }
    )
    .dependsOn(simproto)
    .enablePlugins(ProtobufPlugin)
}
