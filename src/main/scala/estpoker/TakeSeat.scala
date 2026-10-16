package estpoker

import io.eezo.http.Request
import io.eezo.http.Response
import components.PokerRoom
import models.Player
import models.PokerSession

/** What sitting down means: the player's name the browser remembers from now on, the seat for
  * the table it just joined, and a walk to the room. Every page that seats someone comes through
  * here, so a seat always carries its name and its table.
  */
object TakeSeat:

  def into(request: Request, session: PokerSession, player: Player): Response =
    val walked = request.session
      .set("playerName", player.name)
      .set(s"seat.${session.code}", player.id.show)
    PokerRoom.changed.publish(PokerRoom.Tick(session.code))
    Response
      .Redirect(s"/s/${session.code}")
      .withSession(walked)

  /** Back to the lobby with the message that sent you there, for the form to show once. */
  def out(request: Request, notice: String): Response =
    Response
      .Redirect("/")
      .withSession(request.session.flash("notice", notice))
