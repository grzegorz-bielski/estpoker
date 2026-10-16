package components

import io.eezo.core.html.*
import io.eezo.db.*
import io.eezo.db.Scopes.*
import io.eezo.live.*
import munit.FunSuite
import models.Phase
import models.Player
import models.PokerSession
import models.Vote
import rooms.Rooms
import rooms.TestDb

/** The room page's brain, without a socket: a card laid is a row written plus a tick published, and the
  * page's state is the DB's room, re-read. Render is a pure function of that state.
  */
class PokerRoomSuite extends FunSuite:

  private def inTx[A](body: Tx ?=> A): A = TestDb.around(transact(body))
  private def inDb[A](body: DB ?=> A): A = TestDb.around(read(body))
  private def db[A](body: => A): A = TestDb.around(body)

  private def phase(code: String): Phase =
    db(Rooms.room(code)).getOrElse(fail("no room")).session.phase

  private def voteFor(code: String, name: String): Option[String] =
    db(Rooms.room(code))
      .getOrElse(fail("no room"))
      .seats
      .find(seat => seat.player.name == name)
      .getOrElse(fail("no seat"))
      .vote

  TestDb.syncSchema()

  override def beforeEach(context: BeforeEach): Unit = clear()

  private def clear(): Unit = inTx(wipe())

  private def wipe()(using Tx): Unit =
    Table[Vote].deleteAll()
    Table[Player].deleteAll()
    Table[PokerSession].deleteAll()

  test("a click on a card lays that card down in the room"):
    val (session, mod) = db(Rooms.create("Ada"))
    val comp = room(seat(session, mod, name = "Ada", mod = true))
    val after = db(comp.handle(Event("vote-8"), comp.load))
    assertEquals(voteFor(session.code, "Ada"), Some("8"))
    assertEquals(after.map(_.seats.flatMap(seat => seat.vote.toList)), Some(List("8")))

  test("a card the deck does not hold lays nothing"):
    val (session, mod) = db(Rooms.create("Ada"))
    val comp = room(seat(session, mod, name = "Ada", mod = true))
    db(comp.handle(Event("vote-42"), comp.load))
    assertEquals(voteFor(session.code, "Ada"), None)

  test("laying again replaces the card, same as the module says"):
    val (session, mod) = db(Rooms.create("Ada"))
    val (_, grace) = db(Rooms.join(session.code, "Grace")).getOrElse(fail("no seat"))
    val comp = room(seat(session, mod, name = "Ada", mod = true))
    db(comp.handle(Event("vote-8"), comp.load))
    db(comp.handle(Event("vote-5"), comp.load))
    assertEquals(voteFor(session.code, "Ada"), Some("5"))

  test("a player clicking reveal turns nothing; the moderator's click does"):
    val (session, mod) = db(Rooms.create("Ada"))
    val (_, grace) = db(Rooms.join(session.code, "Grace")).getOrElse(fail("no seat"))
    val comp = room(seat(session, grace, name = "Grace", mod = false))
    db(comp.handle(Event("vote-8"), comp.load))
    db(comp.handle(Event("reveal"), comp.load))
    assertEquals(phase(session.code), Phase.Voting)
    val owner = room(seat(session, mod, name = "Ada", mod = true))
    db(owner.handle(Event("reveal"), owner.load))
    assertEquals(phase(session.code), Phase.Revealed)

  test("the moderator's reset click deals the next round"):
    val (session, mod) = db(Rooms.create("Ada"))
    val (_, grace) = db(Rooms.join(session.code, "Grace")).getOrElse(fail("no seat"))
    val comp = room(seat(session, mod, name = "Ada", mod = true))
    db(comp.handle(Event("vote-8"), comp.load))
    db(comp.handle(Event("vote-13"), comp.load))
    db(comp.handle(Event("reset"), comp.load))
    val round = db(Rooms.room(session.code)).getOrElse(fail("no room")).session.round
    assertEquals(round, 2)
    assertEquals(voteFor(session.code, "Ada"), None)

  test("an event the room does not know leaves the state alone"):
    val (session, mod) = db(Rooms.create("Ada"))
    val comp = room(seat(session, mod, name = "Ada", mod = true))
    val before = db(comp.load)
    assertEquals(comp.handle(Event("knock-knock"), before), before)

  test("the room is drawn face down while voting, face up once revealed"):
    val (session, mod) = db(Rooms.create("Ada"))
    val comp = room(seat(session, mod, name = "Ada", mod = true))
    val voting = db(comp.render(comp.load)).render
    assert(voting.contains("card3d"), voting)
    assert(!voting.contains("flipped"), "expected the cards face down before a reveal")
    assert(voting.contains("Reveal now"), voting)
    assert(voting.contains("Waiting"), voting)

    val (_, grace) = db(Rooms.join(session.code, "Grace")).getOrElse(fail("no seat"))
    db(Rooms.play(mod.id, session.id, "8"))
    db(Rooms.play(grace.id, session.id, "13"))
    val revealed = db(comp.render(comp.load)).render
    assert(revealed.contains("flipped"), revealed)
    assert(revealed.contains("average"), revealed)
    assert(revealed.contains("Deal the next round"), revealed)

  test("a view whose room has folded is drawn as gone"):
    val (session, mod) = db(Rooms.create("Ada"))
    val comp = room(seat(session, mod, name = "Ada", mod = true))
    val gone = db(comp.render(None)).render
    assert(gone.contains("folded its cards"), gone)
    assert(gone.contains("Deal a fresh one"), gone)

  // -- fitting the parts together -------------------------------------------------------------

  private def seat(session: PokerSession, player: Player, name: String, mod: Boolean): PokerRoom.Seat =
    PokerRoom.Seat(session.code, session.id, player.id, name, mod)

  private def room(seat: PokerRoom.Seat): PokerRoom = new PokerRoom(seat)

end PokerRoomSuite
