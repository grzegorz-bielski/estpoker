package rooms

import java.sql.Connection

import io.eezo.db.DbApp
import io.eezo.db.Schema

/** The application's database, booted in test clothing: the same schema, the same Postgres schema name and
  * per-connection hook as `Main`, but pointed at whatever the environment names and started empty. The
  * `eezo-pg` Docker container is the convention every example shares.
  */
object TestDb extends DbApp:

  override def schema: Schema = estpoker.AppSchema
  override def databaseSchema: String = estpoker.Main.databaseSchema
  override def databaseInit: Connection => Unit = estpoker.Main.databaseInit
  override def boot(): Unit = ()

  /** The one thing this object exists to publish: a `Database` installed around a block. */
  def around[A](body: => A): A = withDatabase(body)

  /** The tables, from the models — the runner's whole setup. */
  def syncSchema(): Unit = main(Array("db", "sync", "--apply"))

end TestDb
