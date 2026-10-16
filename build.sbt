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

// The braceless rule is enforced, not aspirational: every compile formats first.
scalafmtOnCompile := true

// A stable secret so seat cookies survive a restart during local play; a deploy is expected
// to set EEZO_SECRET itself, the way the eezo http module wants it.
Compile / run / envVars += "EEZO_SECRET" -> "Qp7Xk2mWrvE8hLJd3yB9tN5zS6fGc4A1"

// -- test
libraryDependencies += "org.scalameta" %% "munit" % "1.3.4" % Test
