name         := "estpoker"
scalaVersion := "3.10.0"

enablePlugins(EezoPlugin)

libraryDependencies += "io.eezo" %% "eezo" % "0.2.0"

scalacOptions ++= Seq("-release", "25", "-deprecation", "-feature", "-unchecked")

// eezo needs a modern JDK to run (virtual threads without pinning), so the app forks.
run / fork           := true
run / outputStrategy := Some(OutputStrategy.StdoutOutput)

Compile / run / mainClass := Some("estpoker.Main")

// The suites share one Postgres and one installed-database slot; running them at once
// interleaves wipes with reads. One suite at a time keeps each story straight.
Test / parallelExecution := false

// -- test
libraryDependencies += "org.scalameta" %% "munit" % "1.3.4" % Test
