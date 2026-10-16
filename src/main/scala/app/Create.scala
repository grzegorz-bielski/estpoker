package app

import io.eezo.http.Request
import io.eezo.http.Response
import rooms.Rooms

import estpoker.TakeSeat

/** `app/Create.scala` mounts `POST /`: deal a fresh table, seat its creator as the moderator. */
object Create:

  def create(request: Request): Response =
    val name = request.form.get("name").flatMap(_.headOption).map(_.trim).getOrElse("")
    if name.isEmpty then TakeSeat.out(request, "a table needs your name")
    else
      val (session, mod) = Rooms.create(name)
      TakeSeat.into(request, session, mod)
