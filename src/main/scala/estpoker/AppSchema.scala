package estpoker

import io.eezo.db.Schema
import models.Player
import models.PokerSession
import models.Vote

/** Every table the application owns, named once each. Child tables first, so the expiry sweep's
  * delete order (votes, players, sessions) mirrors the declaration order.
  */
object AppSchema extends Schema:
  val votes    = table[Vote]
  val players  = table[Player]
  val sessions = table[PokerSession]
