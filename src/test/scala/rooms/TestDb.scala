package rooms

import java.sql.Connection

import io.eezo.db.DbApp
import io.eezo.db.Schema

object TestDb extends DbApp:

  override def schema: Schema = estpoker.AppSchema
  override def databaseSchema: String = estpoker.Main.databaseSchema
  override def databaseInit: Connection => Unit = estpoker.Main.databaseInit
  override def boot(): Unit = ()

  def around[A](body: => A): A = withDatabase(body)

  def syncSchema(): Unit = main(Array("db", "sync", "--apply"))
