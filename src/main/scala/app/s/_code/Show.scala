package app.s._code

import io.eezo.core.Id
import io.eezo.core.html.*
import io.eezo.http.Request
import io.eezo.http.Response
import io.eezo.live.Live

import estpoker.pages.Pages
import components.PokerRoom
import models.Player
import rooms.Rooms

/** `app/s/_code/Show.scala` mounts `GET /s/:code`: the table itself, live. An unseated visitor walks to the
  * name form first; a room link whose table is gone is a 404, because the invitation died with the table.
  */
object Show:

  def show(request: Request): Response =
    val code = request.param[String]("code")
    Rooms.room(code) match
      case None       => Response.status(404)
      case Some(room) =>
        seatOf(request, code) match
          case None       => Response.Redirect(s"/s/$code/join")
          case Some(seat) =>
            Pages.shell("estpoker", request.csrf, Live.mount(request, PokerRoom(seat)))

  /** The seat the cookie names, only while it still belongs to the table in this address. */
  private def seatOf(request: Request, code: String): Option[PokerRoom.Seat] =
    request.session
      .get(s"seat.$code")
      .map(id => Id[Player](java.util.UUID.fromString(id)))
      .flatMap(Rooms.sits)
      .flatMap((session, player) =>
        Option.when(session.code == code)(
          PokerRoom.Seat(code, session.id, player.id, player.name, player.mod)
        )
      )
