package app.s._code.join

import io.eezo.http.Request
import io.eezo.http.Response

import estpoker.SeatUp

object Create:

  def create(request: Request): Response =
    val name = request.form.get("name").flatMap(_.headOption).map(_.trim).getOrElse("")
    SeatUp.enter(request.param[String]("code"), name, request)
