package rooms

import java.time.Instant

import io.eezo.core.Id
import io.eezo.db.*
import io.eezo.db.Scopes.*
import munit.FunSuite
import models.*
import models.Player
import models.PokerSession
import models.Vote

class RoomsSuite extends FunSuite:

  TestDb.syncSchema()

  private def inTx[A](body: Tx ?=> A): A = TestDb.around(transact(body))
  private def inDb[A](body: DB ?=> A): A = TestDb.around(read(body))
  private def db[A](body: => A): A = TestDb.around(body)

  private def wipe()(using Tx): Unit =
    Table[Vote].deleteAll()
    Table[Player].deleteAll()
    Table[PokerSession].deleteAll()

  private def clear(): Unit = inTx(wipe())

  private def kill(session: PokerSession): Unit =
    inTx(Tx ?=> Table[PokerSession].update(session.copy(expiresAt = Instant.now().minusSeconds(1))))
  private def allVotes: List[Vote] = inDb(Table[Vote].all())
  private def allNames: List[String] = inDb(Table[Player].all().map(_.name))
  private def wholeRoom(code: String) = db(Rooms.room(code)).getOrElse(fail("no room"))

  override def beforeEach(context: BeforeEach): Unit = clear()

  test("create deals a fresh table and seats its creator as the moderator"):
    val (session, mod) = db(Rooms.create("Ada"))
    assertEquals(session.round, 1)
    assertEquals(session.phase, Phase.Voting)
    assertEquals(session.code.size, 6)
    assert(session.expiresAt.isAfter(Instant.now()), "expected a few days of life")
    assert(mod.mod, "expected the creator to moderate")

  test("a join seats a named player on the table the code names"):
    val (session, _) = db(Rooms.create("Ada"))
    val (seated, grace) = db(Rooms.join(session.code, "Grace")).getOrElse(fail("expected a seat"))
    assertEquals(seated.id, session.id)
    assertEquals(grace.name, "Grace")
    assertEquals(grace.mod, false)

  test("an unknown code joins nothing"):
    assertEquals(db(Rooms.join("XXXXXX", "Grace")), Left(Rooms.JoinError.Unknown))

  test("an expired code joins nothing, and reports expiry"):
    val (session, _) = db(Rooms.create("Ada"))
    kill(session)
    assertEquals(db(Rooms.join(session.code, "Grace")), Left(Rooms.JoinError.Expired))

  test("a room past the sanity gate joins nobody"):
    val (session, _) = db(Rooms.create("Ada"))
    val fills = (1 until Rooms.maxPlayers)
      .map(i => db(Rooms.join(session.code, s"P$i")))
    assert(fills.forall(_.isRight), "expected every seat up to the gate to fill")
    assertEquals(db(Rooms.join(session.code, "Latecomer")), Left(Rooms.JoinError.Full))

  test("a vote lands, and re-voting replaces it"):
    val (session, mod) = db(Rooms.create("Ada"))
    val (_, grace) = db(Rooms.join(session.code, "Grace")).getOrElse(fail("expected a seat"))
    db(Rooms.play(mod.id, session.id, "8"))
    db(Rooms.play(mod.id, session.id, "5"))
    assertEquals(allVotes.map(_.card), List("5"))

  test("nobody can vote with a card the deck does not hold"):
    val (session, mod) = db(Rooms.create("Ada"))
    assertEquals(db(Rooms.play(mod.id, session.id, "42")), false)

  test("voting after a reveal is refused"):
    val (session, mod) = db(Rooms.create("Ada"))
    db(Rooms.play(mod.id, session.id, "8"))
    db(Rooms.reveal(mod.id, session.id))
    assertEquals(db(Rooms.play(mod.id, session.id, "5")), false)

  test("when every seated player has voted, the cards turn over by themselves"):
    val (session, mod) = db(Rooms.create("Ada"))
    val (_, grace) = db(Rooms.join(session.code, "Grace")).getOrElse(fail("expected a seat"))
    db(Rooms.play(mod.id, session.id, "8"))
    assertEquals(wholeRoom(session.code).session.phase, Phase.Voting)
    db(Rooms.play(grace.id, session.id, "13"))
    assertEquals(wholeRoom(session.code).session.phase, Phase.Revealed)

  test("the moderator may turn the cards over early; a player may not"):
    val (session, mod) = db(Rooms.create("Ada"))
    val (_, grace) = db(Rooms.join(session.code, "Grace")).getOrElse(fail("expected a seat"))
    db(Rooms.play(grace.id, session.id, "13"))
    assertEquals(db(Rooms.reveal(grace.id, session.id)), false)
    assert(db(Rooms.reveal(mod.id, session.id)), "expected the moderator to reveal")
    assertEquals(wholeRoom(session.code).session.phase, Phase.Revealed)

  test("a new round lifts the round number, hides the cards and sweeps the table"):
    val (session, mod) = db(Rooms.create("Ada"))
    val (_, grace) = db(Rooms.join(session.code, "Grace")).getOrElse(fail("expected a seat"))
    db(Rooms.play(mod.id, session.id, "8"))
    db(Rooms.play(grace.id, session.id, "13"))
    assertEquals(db(Rooms.reset(grace.id, session.id)), false)
    assert(db(Rooms.reset(mod.id, session.id)), "expected the moderator to reset")
    val room = wholeRoom(session.code)
    assertEquals(room.session.round, 2)
    assertEquals(room.session.phase, Phase.Voting)
    assertEquals(room.seats.count(seat => seat.vote.isDefined), 0)

  test("old votes never bleed into a new round, even revealed ones"):
    val (session, mod) = db(Rooms.create("Ada"))
    db(Rooms.play(mod.id, session.id, "8"))
    db(Rooms.reset(mod.id, session.id))
    assertEquals(allVotes, List.empty)

  test("the room reads what the table holds"):
    val (session, mod) = db(Rooms.create("Ada"))
    val (_, grace) = db(Rooms.join(session.code, "Grace")).getOrElse(fail("expected a seat"))
    db(Rooms.play(grace.id, session.id, "8"))
    val room = wholeRoom(session.code)
    assertEquals(room.seats.size, 2)
    val graceSeat = room.seats.find(seat => seat.player.name == "Grace").getOrElse(fail("no seat"))
    assertEquals(graceSeat.vote, Some("8"))
    val modSeat = room.seats.find(seat => seat.player.name == "Ada").getOrElse(fail("no seat"))
    assertEquals(modSeat.vote, None)

  test("the expiry sweep takes the whole table down, votes and players with it"):
    val (dead, _) = db(Rooms.create("Ada"))
    val (alive, mod) = db(Rooms.create("Bob"))
    val (_, seated) = db(Rooms.join(dead.code, "Grace")).getOrElse(fail("expected a seat"))
    db(Rooms.play(seated.id, dead.id, "5"))
    kill(dead)
    val swept = db(Rooms.sweep())
    assert(swept >= 1, "expected the dead table to go")
    assertEquals(db(Rooms.room(dead.code)), None)
    assertEquals(allVotes.count(vote => vote.sessionId == Ref.to(dead.id)), 0)
    assert(!allNames.contains("Grace"), "expected the dead table's players to go")
    assert(db(Rooms.room(alive.code)).isDefined, "expected the live table to stay")
