import sbt.Keys._

lazy val simproto = {
  Project("simproto", file("simproto"))
    .settings(
      scalaVersion := "2.12.3",
      organization := "DMF",
      version := "3.4-SNAPSHOT",
      crossPaths := false,
      credentials += Credentials(Path.userHome / ".ivy2" / ".nexus"),
      publishTo := Some("snapshots" at "https://nexus.d-a-s.com/repository/maven-snapshots/"),
      version in ProtobufConfig := "3.3.1"
    )
    .enablePlugins(ProtobufPlugin)
}

lazy val devsdmf = {
  Project("devs-dmf",file(".")).aggregate(simproto).dependsOn(simproto)
    .settings(
      scalaVersion := "2.12.3",
      organization := "DMF",
      version := "3.4-SNAPSHOT",
      publishTo := Some("Artifactory Realm" at "https://dmf.d-a-s.com/artifactory/libs-snapshot-local;build.timestamp=" + new java.util.Date().getTime),
      credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-reflect" % scalaVersion.value,
        "com.typesafe.akka" %% "akka-remote" % "2.5.4",
        "com.typesafe.akka" %% "akka-actor" % "2.5.4",
        "com.typesafe.akka" %% "akka-testkit" % "2.5.4",
        "com.typesafe.akka" %% "akka-slf4j" % "2.5.4",
        "ch.qos.logback" % "logback-classic" % "1.1.7",
        "org.scalatest" %% "scalatest" % "3.0.4" % "test",
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
    .dependsOn(simproto)
}
