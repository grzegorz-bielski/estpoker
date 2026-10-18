import scala.sys.process._

import sbt.nio.Keys.watchTriggers
import sbt.nio.file.Glob
import sbt.nio.file.RecursiveGlob

name := "estpoker"
scalaVersion := "3.10.0"

enablePlugins(EezoPlugin)

libraryDependencies += "io.eezo" %% "eezo" % "0.2.0"

scalacOptions ++= Seq("-release", "25", "-deprecation", "-feature", "-unchecked")

run / fork := true
run / outputStrategy := Some(OutputStrategy.StdoutOutput)

Compile / run / mainClass := Some("estpoker.Main")

// The suites share one Postgres and one installed-database slot
Test / parallelExecution := false

scalafmtOnCompile := true

libraryDependencies += "org.scalameta" %% "munit" % "1.3.4" % Test

// -- tailwind

lazy val tailwindVersion = "4.3.3"

// Effectful shell-out, so it must never be satisfied from sbt 2's task cache.
@transient val tailwindBuild = taskKey[Unit]("Compile the stylesheet (minified) into resources.")

def tailwindCli(dir: File, log: sbt.util.Logger): File = {
  val cli = dir / "tools" / "tailwindcss"
  if (cli.exists()) cli
  else {
    val arch = sys.props("os.arch")
    val platform = sys.props("os.name").toLowerCase match {
      case os if os.contains("mac")   => if (arch == "aarch64") "macos-arm64" else "macos-x64"
      case os if os.contains("linux") => if (arch == "aarch64") "linux-arm64" else "linux-x64"
      case other                      => sys.error(s"tailwind: unsupported OS '$other'")
    }
    val url =
      s"https://github.com/tailwindlabs/tailwindcss/releases/download/v$tailwindVersion/tailwindcss-$platform"
    IO.createDirectory(cli.getParentFile)
    log.info(s"tailwind: fetching $url")
    val in = new java.net.URI(url).toURL.openStream()
    try java.nio.file.Files.copy(in, cli.toPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    finally in.close()
    cli.setExecutable(true)
    cli
  }
}

tailwindBuild := {
  val cli = tailwindCli(baseDirectory.value, streams.value.log)
  val input = baseDirectory.value / "tailwind" / "input.css"
  val output = (Compile / resourceDirectory).value / "estpoker" / "tailwind.css"
  IO.createDirectory(output.getParentFile)
  val exit = Process(
    Seq(cli.getAbsolutePath, "-i", input.getAbsolutePath, "-o", output.getAbsolutePath, "--minify"),
    baseDirectory.value
  ).!
  if (exit != 0) sys.error(s"tailwind exited with $exit")
}

// `eezoDev` is `~eezoRestart`, which compiles on every save and so already runs `tailwindBuild`.
// Watching the stylesheet here folds CSS-only edits into that same loop.
tailwindBuild / watchTriggers += Glob(baseDirectory.value / "tailwind", RecursiveGlob / "*.css")

Compile / compile := Def.uncached {
  (Compile / compile).dependsOn(tailwindBuild).value
}
