

/*
object MyBuild extends Build {
  lazy val myproject = Project(
    id = "myproject",
    base = file(".")
  ).settings(
    PB.protobufSettings : _*
  ).settings(
    /* custom settings here */
  )
}
*/