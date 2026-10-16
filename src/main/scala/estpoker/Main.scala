package estpoker

import java.sql.Connection

import io.eezo.EezoApp
import io.eezo.db.Schema
import io.eezo.generated.Routes
import io.eezo.http.RouteTable
import rooms.Rooms

/** The whole entry point. `main` is inherited and dispatches: `sbt run` serves on port 8080,
  * `eezo dev` adds the drift check and restart-on-save, and `eezo db status|sync|freeze|migrate`
  * are the schema commands.
  */
object Main extends EezoApp:

  override def schema: Schema     = AppSchema
  override def routes: RouteTable = Routes.table()

  /** This app keeps its tables under a Postgres schema named for itself, so the dev database stays
    * fit to share. Two halves, told once each: the init hook runs on every pooled connection
    * (search_path is per connection), and `databaseSchema` points the drift commands' catalog reads
    * at the same name.
    */
  override def databaseSchema: String = "estpoker"

  override def databaseInit: Connection => Unit = c =>
    val st = c.createStatement()
    try
      st.execute("""create schema if not exists "estpoker"""")
      st.execute("""set search_path to "estpoker"""")
    finally st.close()

  /** Tables nobody came back for go before the serving starts, so a restart also tidies up. */
  override def boot(): Unit =
    val _ = Rooms.sweep()
    super.boot()
end Main
