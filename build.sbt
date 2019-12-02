import sbt.Keys._

val akkaVersion = "2.5.26"

lazy val simproto = {
  Project("simproto", file("simproto"))
    .settings(
      scalaVersion := "2.12.10",
      organization := "DMF",
      version := "3.7-SNAPSHOT",
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
      scalaVersion := "2.12.10",
      organization := "DMF",
      version := "3.7-SNAPSHOT",
      credentials += Credentials(Path.userHome / ".ivy2" / ".nexus"),
      publishTo := Some("snapshots" at "https://nexus.d-a-s.com/repository/maven-snapshots/"),
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-reflect" % scalaVersion.value,
        "com.typesafe.akka" %% "akka-remote" % akkaVersion,
        "com.typesafe.akka" %% "akka-actor" % akkaVersion,
        "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
        "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
        "ch.qos.logback" % "logback-classic" % "1.2.3",
        "org.scalatest" %% "scalatest" % "3.0.8" % "test",
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
    .dependsOn(simproto)
}
