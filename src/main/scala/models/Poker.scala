package models

import java.time.Instant

import io.eezo.core.Id
import io.eezo.db.Column
import io.eezo.db.Ref
import io.eezo.db.Table

case class PokerSession(
    id: Id[PokerSession],
    code: String,
    createdBy: String,
    round: Int,
    phase: Phase,
    expiresAt: Instant
) derives Table

case class Player(
    id: Id[Player],
    sessionId: Ref[PokerSession],
    name: String,
    mod: Boolean
) derives Table

case class Vote(
    id: Id[Vote],
    sessionId: Ref[PokerSession],
    playerId: Ref[Player],
    round: Int,
    card: String
) derives Table

enum Phase:
  case Voting, Revealed

object Phase:
  given Column[Phase] = Column[String].imap(forStatus)(forColumn)

  private def forStatus(status: String): Phase = status match
    case "voting"   => Phase.Voting
    case "revealed" => Phase.Revealed
    case other      => throw new IllegalArgumentException(s"unknown phase '$other'")

  private def forColumn(phase: Phase): String = phase match
    case Phase.Voting   => "voting"
    case Phase.Revealed => "revealed"
