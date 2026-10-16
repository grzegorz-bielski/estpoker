package app

import io.eezo.core.html.*
import io.eezo.core.html.Tags.*
import io.eezo.http.Request
import io.eezo.http.Response

import estpoker.pages.Pages

/** `app/Index.scala` mounts `GET /`: the launch screen. Two doors, shut until they are knocked on: take a
  * seat at a table someone else holds, or deal one of your own. The doors are native `details` elements
  * sharing one `name`, so the browser opens exactly one without a line of script.
  */
object Index:

  /** eezo's tag list stops before the disclosure pair; the constructor is right there. */
  private val details = Tag("details")
  private val summary = Tag("summary")

  def index(request: Request): Response =
    val remembered = request.session.get("playerName").getOrElse("")
    val notice = request.session.flash("notice")
    Pages.shell(
      "estimation poker",
      request.csrf,
      main(
        Attrs.cls := "flex h-dvh flex-col items-center justify-center px-4",
        p(
          Attrs.cls := "text-sm uppercase tracking-[0.35em] text-amber-300/90",
          "estimation"
        ),
        h1(
          Attrs.cls :=
            "mb-2 text-4xl font-bold tracking-[0.35em] text-amber-100 drop-shadow-[0_2px_6px_rgba(0,0,0,0.6)] sm:text-5xl",
          "POKER"
        ),
        p(
          Attrs.cls := "mb-10 text-sm text-stone-400",
          "deal a table, share the link, estimate as a table"
        ),
        notice.map(msg =>
          p(
            Attrs.cls := "mb-6 rounded-lg border border-amber-600/60 bg-amber-950/60 px-4 py-2 text-amber-200",
            msg
          )
        ),
        div(
          Attrs.cls := "flex w-full max-w-md flex-col gap-3",
          details(
            Attrs.name := "lobby",
            summary(
              Attrs.cls := "btn-deal select-none text-center",
              "Take a seat"
            ),
            div(
              Attrs.cls := "mt-3 rounded-xl border border-stone-700/60 bg-black/30 p-4",
              Pages.seatForm("/s/join", request.csrf, remembered, code = Some(""))
            )
          ),
          details(
            Attrs.name := "lobby",
            summary(
              Attrs.cls := "btn-deal btn-deal--quiet select-none text-center",
              "Deal a table"
            ),
            div(
              Attrs.cls := "mt-3 rounded-xl border border-stone-700/60 bg-black/30 p-4",
              Pages.form(
                "/",
                request.csrf,
                Pages.field("your name", "name", remembered),
                button(Attrs.cls := "btn-deal", Attrs.tpe := "submit", "Deal it")
              )
            )
          )
        )
      )
    )
