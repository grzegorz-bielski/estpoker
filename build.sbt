name         := "estpoker"
scalaVersion := "3.10.0"

enablePlugins(EezoPlugin)

libraryDependencies += "io.eezo" %% "eezo" % "0.2.0"

scalacOptions ++= Seq("-release", "25", "-deprecation", "-feature", "-unchecked")

// eezo needs a modern JDK to run (virtual threads without pinning), so the app forks.
run / fork           := true
run / outputStrategy := Some(OutputStrategy.StdoutOutput)

Compile / run / mainClass := Some("estpoker.Main")

// -- test
libraryDependencies += "org.scalameta" %% "munit" % "1.3.4" % Test
