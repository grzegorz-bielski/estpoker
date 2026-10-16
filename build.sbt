name         := "estpoker"
scalaVersion := "3.8.4"

enablePlugins(EezoPlugin)

libraryDependencies += "io.eezo" %% "eezo" % "0.2.0"

scalacOptions ++= Seq("-release", "25", "-deprecation", "-feature", "-unchecked", "-no-indent")

// eezo needs a modern JDK to run (virtual threads without pinning), so the app forks.
run / fork           := true
run / outputStrategy := Some(OutputStrategy.StdoutOutput)
