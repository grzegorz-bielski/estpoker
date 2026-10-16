package rooms

import java.security.SecureRandom
import java.time.Duration
import java.time.Instant

import io.eezo.core.Id
import io.eezo.db.*
import io.eezo.db.Scopes.*
import models.Phase
import models.Player
import models.PokerSession
import models.Vote

/** The tables and their rounds: create, join, play, reveal, reset — every move a hand makes, plus the expiry
  * sweep that takes a table down when its few days are over. Postgres is the truth behind every function
  * here; the live component reads through [[room]] and its ticks re-read it the same way.
  *
  * Refusals are answers, not exceptions: `play`, `reveal` and `reset` answer `false` and change nothing, and
  * every open page re-reads the room from the database on its next tick, so a refused move self-corrects
  * everywhere. The scope stays at the object's front door; every move's work below is a helper that joins it
  * with `(using Tx)` or `(using DB)`.
  */
object Rooms:

  /** A sanity gate, deliberately generous: a real scrum table is a few people, and anything past this is a
    * bug or a bot walking in.
    */
  val maxPlayers: Int = 50

  /** A table lives a few days, then its invite dies. */
  val ttl: Duration = Duration.ofDays(3)

  /** Why a join did not seat anybody. */
  enum JoinError:
    case Unknown, Expired, Full

  /** One seat around the table, its card shown only when the room says so. */
  final case class Seat(player: Player, vote: Option[String])

  /** A room snapshot: the table and every seat on it, for this round. */
  final case class Room(session: PokerSession, seats: List[Seat])

  // -- opening a table ------------------------------------------------------------------------

  /** Deals a fresh table and seats its creator as the moderator. */
  def create(name: String): (PokerSession, Player) = transact(dealTable(name))

  /** Seats a named player on the table the code names. Expired and unknown marks are the same invitation to
    * leave.
    */
  def join(code: String, name: String): Either[JoinError, (PokerSession, Player)] =
    transact(seatUp(code, name))

  // -- a hand's moves -------------------------------------------------------------------------

  /** Lays a card down for this round. Laying again replaces the card; a finished round takes nothing; only
    * cards the deck holds are legal, and when the last player's card lands the round turns over by itself.
    */
  def play(playerId: Id[Player], sessionId: Id[PokerSession], card: String): Boolean =
    Cards.label(card).isDefined && transact(layCard(playerId, sessionId, card))

  /** A moderator turning the cards over before everyone has voted. */
  def reveal(playerId: Id[Player], sessionId: Id[PokerSession]): Boolean =
    transact(turnCards(playerId, sessionId))

  /** A moderator dealing the next round: the count goes up, the cards hide, the table clears. */
  def reset(playerId: Id[Player], sessionId: Id[PokerSession]): Boolean =
    transact(dealAgain(playerId, sessionId))

  // -- reading --------------------------------------------------------------------------------

  /** The room as it stands right now: the table and one `Seat` per player, votes attached. All operands of a
    * tick re-read this, so every page converges on the same truth.
    */
  def room(code: String): Option[Room] = read(snapShot(code))

  /** A session and its player, for the seat cookie's handshake. */
  /** The session the player sits on, for the seat cookie's handshake. */
  def sits(playerId: Id[Player]): Option[(PokerSession, Player)] = read(satOn(playerId))

  // -- the end of a table ---------------------------------------------------------------------

  /** Takes every table whose few days are over down, votes and players with it. Answers how many tables went.
    * Child tables go first, because eezo's foreign keys do not cascade.
    */
  def sweep(): Int = transact(takeDown())

  // -- inside the box: one helper per move, with the scope it joins named in its argument ------

  private def dealTable(name: String)(using Tx): (PokerSession, Player) =
    val sessions = Table[PokerSession]
    val players = Table[Player]
    val seated = name.trim
    val now = Instant.now()
    val session = PokerSession(
      Id.gen(),
      unusedCode(),
      seated,
      1,
      Phase.Voting,
      now.plus(ttl)
    )
    sessions.insert(session)
    val mod = Player(Id.gen(), Ref.to(session.id), seated, mod = true)
    players.insert(mod)
    (session, mod)

  private def seatUp(
      code: String,
      name: String
  )(using Tx): Either[JoinError, (PokerSession, Player)] =
    Table[PokerSession].where(_.code === code.trim).first() match
      case None          => Left(JoinError.Unknown)
      case Some(session) =>
        if !session.expiresAt.isAfter(Instant.now()) then Left(JoinError.Expired)
        else if Table[Player].where(_.sessionId === Ref.to(session.id)).count() >= maxPlayers.toLong
        then Left(JoinError.Full)
        else
          val player = Player(Id.gen(), Ref.to(session.id), name.trim, mod = false)
          Table[Player].insert(player)
          Right((session, player))

  private def layCard(
      playerId: Id[Player],
      sessionId: Id[PokerSession],
      card: String
  )(using Tx): Boolean =
    Table[PokerSession].where(_.id === sessionId).first() match
      case None          => false
      case Some(session) =>
        if session.phase != Phase.Voting then false
        else lay(playerId, session, card)

  private def lay(playerId: Id[Player], session: PokerSession, card: String)(using Tx): Boolean =
    val votes = Table[Vote]
    votes
      .where(v => v.playerId === Ref.to(playerId))
      .where(v => v.round === session.round)
      .first() match
      case Some(existing) => votes.update(existing.copy(card = card))
      case None           =>
        votes.insert(Vote(Id.gen(), Ref.to(session.id), Ref.to(playerId), session.round, card))
    if everybodyHasLaid(session) then Table[PokerSession].update(session.copy(phase = Phase.Revealed))
    true

  private def everybodyHasLaid(session: PokerSession)(using DB): Boolean =
    Table[Player].where(_.sessionId === Ref.to(session.id)).count() ==
      Table[Vote]
        .where(v => v.sessionId === Ref.to(session.id))
        .where(v => v.round === session.round)
        .count()

  private def turnCards(playerId: Id[Player], sessionId: Id[PokerSession])(using Tx): Boolean =
    moderator(playerId).fold(false)((session, _) =>
      if session.phase == Phase.Voting then showAll(session) else false
    )

  private def showAll(session: PokerSession)(using Tx): Boolean =
    Table[PokerSession].update(session.copy(phase = Phase.Revealed))
    true

  private def dealAgain(playerId: Id[Player], sessionId: Id[PokerSession])(using Tx): Boolean =
    moderator(playerId).fold(false)((session, _) =>
      Table[Vote].deleteWhere(v => v.sessionId === Ref.to(sessionId))
      Table[PokerSession].update(session.copy(round = session.round + 1, phase = Phase.Voting))
      true
    )

  private def snapShot(code: String)(using DB): Option[Room] =
    live(code).map(session =>
      val seats = Table[Player]
        .where(_.sessionId === Ref.to(session.id))
        .list()
      val laid = Table[Vote]
        .where(v => v.sessionId === Ref.to(session.id))
        .where(v => v.round === session.round)
        .list()
      Room(
        session,
        seats
          .sortBy(player => (player.name, player.id.show))
          .map(player => Seat(player, laid.find(v => v.playerId == Ref.to(player.id)).map(_.card)))
      )
    )

  private def satOn(playerId: Id[Player])(using DB): Option[(PokerSession, Player)] =
    for
      player <- Table[Player].findById(playerId)
      session <- Table[PokerSession].findById(player.sessionId.asId)
      if session.expiresAt.isAfter(Instant.now())
    yield (session, player)

  private def takeDown()(using Tx): Int =
    val gone = Table[PokerSession].where(_.expiresAt <= Instant.now()).list()
    gone.foreach(expired =>
      Table[Vote].deleteWhere(v => v.sessionId === Ref.to(expired.id))
      Table[Player].deleteWhere(p => p.sessionId === Ref.to(expired.id))
      Table[PokerSession].deleteWhere(s => s.id === expired.id)
    )
    gone.size

  /** A session that still has days left, or nothing. */
  private def live(code: String)(using DB): Option[PokerSession] =
    Table[PokerSession]
      .where(_.code === code.trim)
      .first()
      .filter(_.expiresAt.isAfter(Instant.now()))

  /** The session the player sits on, only when that player is the moderator. */
  private def moderator(playerId: Id[Player])(using DB): Option[(PokerSession, Player)] =
    Table[Player]
      .where(p => p.id === playerId)
      .where(_.mod === true)
      .first()
      .flatMap(player =>
        Table[PokerSession].findById(player.sessionId.asId).map(session => (session, player))
      )

  private def unusedCode()(using DB): String =
    LazyList
      .continually(newCode())
      .find(code => Table[PokerSession].where(_.code === code).count() == 0L)
      .get

  private val codeRand = SecureRandom()

  /** Six characters no one misreads over a phone call: `0O` and `1IL` are out of the alphabet. */
  private def newCode(): String =
    val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ24689"
    (0 until 6).map(i => alphabet.charAt(codeRand.nextInt(alphabet.length))).mkString
end Rooms
