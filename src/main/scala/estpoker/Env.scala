package estpoker

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object Env:

  def load(path: Path = Paths.get(".env")): Map[String, String] =
    if !Files.isReadable(path) then Map.empty
    else
      val source = scala.io.Source.fromFile(path.toFile)
      try parse(source.getLines())
      finally source.close()

  private def parse(lines: Iterator[String]): Map[String, String] =
    lines
      .map(_.trim)
      .filter(line => line.nonEmpty && !line.startsWith("#"))
      .flatMap: line =>
        line.split("=", 2) match
          case Array(key, value) => Some(key.trim -> unquote(value.trim))
          case _                 => None
      .toMap

  private def unquote(value: String): String =
    val quoted = value.length >= 2 && (value.head == '"' || value.head == '\'') && value.last == value.head
    if quoted then value.slice(1, value.length - 1) else value
