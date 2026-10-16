package app

import io.eezo.core.html.*
import io.eezo.http.Request
import io.eezo.http.Response

/** `app/Index.scala` mounts `GET /`, calling `def index` — the filename is the route. */
object Index {

  def index(request: Request): Response = {
    val _ = request
    Response.Ok(
      Html.doctype ++ html(
        head(meta(Attrs.charset := "utf-8"), title("eezo")),
        body(
          h1("it works"),
          p("Add a case class with ", code("derives Form, Resource"), " under models/ "),
          p("and it mounts seven CRUD routes. ", code("eezo routes"), " lists them.")
        )
      )
    )
  }
}
