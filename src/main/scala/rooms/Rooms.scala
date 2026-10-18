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

object Rooms:

  val maxPlayers: Int = 50

  val ttl: Duration = Duration.ofDays(3)

  enum JoinError:
    case Unknown, Expired, Full

  final case class Seat(player: Player, vote: Option[String])

  final case class Room(session: PokerSession, seats: List[Seat])

  def create(name: String): (PokerSession, Player) = transact(dealTable(name))

  def join(code: String, name: String): Either[JoinError, (PokerSession, Player)] =
    transact(seatUp(code, name))

  def play(playerId: Id[Player], sessionId: Id[PokerSession], card: String): Boolean =
    Cards.label(card).isDefined && transact(layCard(playerId, sessionId, card))

  def reveal(playerId: Id[Player], sessionId: Id[PokerSession]): Boolean =
    transact(turnCards(playerId, sessionId))

  def reset(playerId: Id[Player], sessionId: Id[PokerSession]): Boolean =
    transact(dealAgain(playerId, sessionId))

  def room(code: String): Option[Room] = read(snapShot(code))

  def sits(playerId: Id[Player]): Option[(PokerSession, Player)] = read(satOn(playerId))

  def sweep(): Int = transact(takeDown())

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

  private def live(code: String)(using DB): Option[PokerSession] =
    Table[PokerSession]
      .where(_.code === code.trim)
      .first()
      .filter(_.expiresAt.isAfter(Instant.now()))

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

  private def newCode(): String =
    val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ24689"
    (0 until 6).map(i => alphabet.charAt(codeRand.nextInt(alphabet.length))).mkString
