package app.s._code.join

import io.eezo.core.html.*
import io.eezo.core.html.Tags.*
import io.eezo.http.Request
import io.eezo.http.Response

import estpoker.pages.Pages

/** `app/s/_code/join/Show.scala` mounts `GET /s/:code/join`: the name form a room link walks an unseated
  * visitor to. The name comes with them if the browser has been here before.
  */
object Show:

  def show(request: Request): Response =
    val code = request.param[String]("code")
    val showed = request.session.get("playerName").getOrElse("")
    Pages.shell(
      "estpoker",
      request.csrf,
      main(
        Attrs.cls := "flex min-h-screen flex-col items-center justify-center px-4",
        div(
          Attrs.cls := "w-full max-w-sm",
          p(
            Attrs.cls := "mb-6 text-center text-lg text-stone-300",
            "you are joining table ",
            strong(Attrs.cls := "font-bold tracking-widest text-amber-200", code)
          ),
          Pages.seatForm(s"/s/$code/join", request.csrf, showed)
        )
      )
    )
