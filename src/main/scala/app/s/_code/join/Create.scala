package app.s._code.join

import io.eezo.http.Request
import io.eezo.http.Response

import estpoker.SeatUp

/** `app/s/_code/join/Create.scala` mounts `POST /s/:code/join`: the same seat-up as the lobby's form, with
  * the code in the path the form was rendered under.
  */
object Create:

  def create(request: Request): Response =
    val name = request.form.get("name").flatMap(_.headOption).map(_.trim).getOrElse("")
    SeatUp.enter(request.param[String]("code"), name, request)
