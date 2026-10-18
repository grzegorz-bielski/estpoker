package app.s.join

import io.eezo.http.Request
import io.eezo.http.Response

import estpoker.SeatUp

object Create:

  def create(request: Request): Response =
    val code = request.form.get("code").flatMap(_.headOption).map(_.trim).getOrElse("")
    val name = request.form.get("name").flatMap(_.headOption).map(_.trim).getOrElse("")
    SeatUp.enter(code, name, request)
