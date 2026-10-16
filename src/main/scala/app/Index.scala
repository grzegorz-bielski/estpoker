package app

import io.eezo.core.html.*
import io.eezo.core.html.Tags.*
import io.eezo.http.Request
import io.eezo.http.Response

import estpoker.pages.Pages

/** `app/Index.scala` mounts `GET /`: the lobby. Deal a fresh table, or walk into one by code. */
object Index:

  def index(request: Request): Response =
    val remembered = request.session.get("playerName").getOrElse("")
    val notice     = request.session.flash("notice")
    Pages.shell(
      "estpoker",
      request.csrf,
      main(
        Attrs.cls := "flex min-h-screen flex-col items-center justify-center px-4 pb-16",
        h1(
          Attrs.cls := "text-5xl font-bold tracking-[0.3em] text-amber-100 drop-shadow-[0_2px_6px_rgba(0,0,0,0.6)]",
          "E-ST-PO-KER"
        ),
        p(
          Attrs.cls := "mt-3 mb-10 text-lg text-stone-300",
          "planning poker at a real table — deal a room, share the link, play"
        ),
        notice.map(msg =>
          p(
            Attrs.cls := "mb-6 rounded-lg border border-amber-600/60 bg-amber-950/60 px-4 py-2 text-amber-200",
            msg
          )
        ),
        div(
          Attrs.cls := "flex w-full max-w-md flex-col gap-8",
          Pages.form(
            "/",
            request.csrf,
            Pages.field("your name", "name", remembered),
            button(
              Attrs.cls := "btn-deal",
              Attrs.tpe := "submit",
              "Deal a table"
            )
          ),
          Pages.seatForm("/s/join", request.csrf, remembered, code = Some(""))
        ),
        footer(
          Attrs.cls := "mt-12 text-sm text-stone-500",
          "cards hidden until everyone has voted"
        )
      )
    )
