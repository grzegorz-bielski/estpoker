package estpoker

import io.eezo.db.Schema
import models.Player
import models.PokerSession
import models.Vote

object AppSchema extends Schema:
  val votes = table[Vote]
  val players = table[Player]
  val sessions = table[PokerSession]
