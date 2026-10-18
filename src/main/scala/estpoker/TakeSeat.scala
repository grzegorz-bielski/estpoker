package estpoker

import io.eezo.http.Request
import io.eezo.http.Response
import components.PokerRoom
import models.Player
import models.PokerSession

object TakeSeat:

  def into(request: Request, session: PokerSession, player: Player): Response =
    val walked = request.session
      .set("playerName", player.name)
      .set(s"seat.${session.code}", player.id.show)
    PokerRoom.changed.publish(PokerRoom.Tick(session.code))
    Response
      .Redirect(s"/s/${session.code}")
      .withSession(walked)

  def out(request: Request, notice: String): Response =
    Response
      .Redirect("/")
      .withSession(request.session.flash("notice", notice))
