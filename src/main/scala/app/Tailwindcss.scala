package app

import io.eezo.http.Body
import io.eezo.http.Request
import io.eezo.http.Response

/** `app/Tailwindcss.scala` mounts `GET /tailwindcss`: the compiled stylesheet, on the classpath
  * in `resources/estpoker/`. The CLI regenerates it; the app ships it.
  */
object Tailwindcss:

  def tailwindcss(request: Request): Response =
    val in = getClass.getResourceAsStream("/estpoker/tailwind.css")
    if in == null then Response.status(404)
    else
      val css = in.readAllBytes()
      in.close()
      Response(
        200,
        Seq(
          "Content-Type"  -> "text/css; charset=utf-8",
          "Cache-Control" -> "no-cache"
        ),
        Body.Bytes(css)
      )
