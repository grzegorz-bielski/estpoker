// The route table generator: scans src/main/scala/app/ and your models into
// io.eezo.generated.Routes, and provides the eezo* tasks (eezoDev, eezoStatus, ...).
addSbtPlugin("io.eezo" % "sbt-eezo" % "0.2.0")

// The formatter enforces the house style: bracelessScala, or a compile turns the braces in.
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.6")
