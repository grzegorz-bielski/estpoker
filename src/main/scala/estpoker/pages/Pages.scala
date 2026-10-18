package estpoker.pages

import io.eezo.core.html.*
import io.eezo.core.html.Tags.*
import io.eezo.http.Csrf
import io.eezo.http.Response

object Pages:

  def shell(subject: String, csrf: Csrf.Token, content: Html*): Response =
    Response.Ok(
      Html.doctype ++ html(
        head(
          meta(Attrs.charset := "utf-8"),
          meta(Attrs.name := "viewport", Attrs.content := "width=device-width, initial-scale=1"),
          title(subject),
          link(Attrs.rel := "stylesheet", Attrs.href := "/tailwindcss?v=5")
        ),
        body(
          Attrs.cls := "h-dvh overflow-hidden bg-night text-stone-100 font-sans antialiased",
          content
        )
      )
    )

  def form(action: String, csrf: Csrf.Token, fields: Html*): Html =
    Tags.form(
      Attrs.cls := "flex flex-col gap-4",
      Attrs.action := action,
      Attrs.method := "post",
      Csrf.hidden(csrf),
      fields
    )

  def field(labelText: String, name: String, value: String, extra: Attr*): Html =
    label(
      Attrs.cls := "mb-1.5 block text-sm uppercase tracking-wider text-stone-300",
      Attrs.htmlFor := name,
      labelText
    ) ++ input(
      Seq(
        Attrs.cls := "field",
        Attrs.tpe := "text",
        Attrs.name := name,
        Attrs.value := value
      ) ++ extra
    )

  def seatForm(
      action: String,
      csrf: Csrf.Token,
      remembered: String,
      code: Option[String] = None
  ): Html =
    Pages.form(
      action,
      csrf,
      (code.map(c => Pages.field("table code", "code", c)).toList :+ Pages.field(
        "your name",
        "name",
        remembered
      ) :+ button(Attrs.cls := "btn-deal", Attrs.tpe := "submit", "Take a seat"))*
    )
