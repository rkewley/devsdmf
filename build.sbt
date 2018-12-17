import sbt.Keys._

lazy val simproto = {
  Project("simproto", file("simproto"))
    .settings(
      scalaVersion := "2.12.7",
      organization := "DMF",
      version := "3.6-SNAPSHOT",
      crossPaths := false,
      credentials += Credentials(Path.userHome / ".ivy2" / ".nexus"),
      publishTo := Some("snapshots" at "https://nexus.d-a-s.com/repository/maven-snapshots/"),
      version in ProtobufConfig := "3.6.1"
    )
    .enablePlugins(ProtobufPlugin)
}

lazy val devsdmf = {
  Project("devs-dmf",file(".")).aggregate(simproto).dependsOn(simproto)
    .settings(
      scalaVersion := "2.12.7",
      organization := "DMF",
      version := "3.6-SNAPSHOT",
      credentials += Credentials(Path.userHome / ".ivy2" / ".nexus"),
      publishTo := Some("snapshots" at "https://nexus.d-a-s.com/repository/maven-snapshots/"),
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-reflect" % scalaVersion.value,
        "com.typesafe.akka" %% "akka-remote" % "2.5.17",
        "com.typesafe.akka" %% "akka-actor" % "2.5.17",
        "com.typesafe.akka" %% "akka-testkit" % "2.5.17",
        "com.typesafe.akka" %% "akka-slf4j" % "2.5.17",
        "ch.qos.logback" % "logback-classic" % "1.2.3",
        "org.scalatest" %% "scalatest" % "3.0.5" % "test",
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
