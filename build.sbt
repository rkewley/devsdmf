organization := "DSE"

name := """devs-dmf"""

version := "2.1"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "com.typesafe.akka" %% "akka-remote" % "2.4.7",
  "com.typesafe.akka" %% "akka-actor" % "2.4.7",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.7",
  "com.typesafe.akka" % "akka-slf4j_2.11" % "2.4.7",
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test",
  "junit" % "junit" % "4.12" % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test"
)

testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")

// Remove logback.xml from jar file
mappings in (Compile,packageBin) ~= {
  (ms: Seq[(File,String)]) =>
    ms filter { case (file, toPath) => !(toPath.endsWith("logback.xml")) }
}
