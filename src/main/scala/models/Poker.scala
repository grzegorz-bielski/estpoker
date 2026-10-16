package models

import java.time.Instant

import io.eezo.core.Id
import io.eezo.db.Column
import io.eezo.db.Ref
import io.eezo.db.Table

/** One planning-poker table. `code` is the whole invite address (`/s/:code`); `round` grows on
  * every reset; `phase` says whether cards are hidden (`Voting`) or shown (`Revealed`); the row
  * itself dies at `expiresAt`, a few days after it was made.
  */
case class PokerSession(
    id: Id[PokerSession],
    code: String,
    createdBy: String,
    round: Int,
    phase: Phase,
    expiresAt: Instant
) derives Table

/** A seat. `mod` is whoever created the table; they reveal early and deal the next round.
  */
case class Player(
    id: Id[Player],
    sessionId: Ref[PokerSession],
    name: String,
    mod: Boolean
) derives Table

/** One card on one round — a `$round` column key on the vote, so a new round never sees old
  * cards. Re-voting replaces the row instead of adding one.
  */
case class Vote(
    id: Id[Vote],
    sessionId: Ref[PokerSession],
    playerId: Ref[Player],
    round: Int,
    card: String
) derives Table

/** What the cards' faces are doing inside a round: hidden until everyone has voted (or the
  * moderator reveals early), then shown.
  */
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
