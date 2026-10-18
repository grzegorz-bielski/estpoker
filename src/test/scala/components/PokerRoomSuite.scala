package components

import io.eezo.db.*
import io.eezo.db.Scopes.*
import io.eezo.live.*
import munit.FunSuite
import models.Player
import models.PokerSession
import models.Vote
import rooms.Rooms
import rooms.TestDb

class PokerRoomSuite extends FunSuite:

  private def inTx[A](body: Tx ?=> A): A = TestDb.around(transact(body))
  private def db[A](body: => A): A = TestDb.around(body)

  private def wipe()(using Tx): Unit =
    Table[Vote].deleteAll()
    Table[Player].deleteAll()
    Table[PokerSession].deleteAll()

  private def voteFor(code: String, name: String): Option[String] =
    db(Rooms.room(code))
      .getOrElse(fail("no room"))
      .seats
      .find(seat => seat.player.name == name)
      .getOrElse(fail("no seat"))
      .vote

  TestDb.syncSchema()

  override def beforeEach(context: BeforeEach): Unit = inTx(wipe())

  test("a click on a card lays that card down in the room"):
    val (session, mod) = db(Rooms.create("Ada"))
    val comp = room(seat(session, mod, "Ada", mod = true))
    db(comp.handle(Event("vote-8"), comp.load))
    assertEquals(voteFor(session.code, "Ada"), Some("8"))

  test("an event the room does not know leaves the state alone"):
    val (session, mod) = db(Rooms.create("Ada"))
    val comp = room(seat(session, mod, "Ada", mod = true))
    val before = db(comp.load)
    assertEquals(comp.handle(Event("knock-knock"), before), before)

  test("the cards are drawn face down while voting and face up once revealed"):
    val (session, mod) = db(Rooms.create("Ada"))
    val (_, grace) = db(Rooms.join(session.code, "Grace")).getOrElse(fail("no seat"))
    val comp = room(seat(session, mod, "Ada", mod = true))
    val voting = db(comp.render(comp.load)).render
    assert(voting.contains("card3d"), voting)
    assert(!voting.contains("flipped"), voting)

    db(Rooms.play(mod.id, session.id, "8"))
    db(Rooms.play(grace.id, session.id, "13"))
    val revealed = db(comp.render(comp.load)).render
    assert(revealed.contains("flipped"), revealed)
    assert(revealed.contains("average"), revealed)

  test("only the moderator gets the reveal and deal controls"):
    val (session, mod) = db(Rooms.create("Ada"))
    val (_, grace) = db(Rooms.join(session.code, "Grace")).getOrElse(fail("no seat"))
    val modPage = room(seat(session, mod, "Ada", mod = true))
    val gracePage = room(seat(session, grace, "Grace", mod = false))
    assert(db(modPage.render(modPage.load)).render.contains("Reveal now"), "the moderator reveals")
    assert(!db(gracePage.render(gracePage.load)).render.contains("Reveal now"), "a player does not")
    db(Rooms.play(mod.id, session.id, "8"))
    db(Rooms.play(grace.id, session.id, "13"))
    assert(db(modPage.render(modPage.load)).render.contains("Deal the next round"), "the moderator deals")
    assert(!db(gracePage.render(gracePage.load)).render.contains("Deal the next round"), "a player does not")

  test("a view whose room has folded is drawn as gone"):
    val (session, mod) = db(Rooms.create("Ada"))
    val comp = room(seat(session, mod, "Ada", mod = true))
    val gone = db(comp.render(None)).render
    assert(gone.contains("folded its cards"), gone)
    assert(gone.contains("Deal a fresh one"), gone)

  private def seat(session: PokerSession, player: Player, name: String, mod: Boolean): PokerRoom.Seat =
    PokerRoom.Seat(session.code, session.id, player.id, name, mod)

  private def room(seat: PokerRoom.Seat): PokerRoom = new PokerRoom(seat)
