package estpoker

import java.sql.Connection

import io.eezo.EezoApp
import io.eezo.db.Schema
import io.eezo.generated.Routes
import io.eezo.http.RouteTable
import io.eezo.http.Secret
import rooms.Rooms

object Main extends EezoApp:

  override def schema: Schema = AppSchema
  override def routes: RouteTable = Routes.table()

  override def secret: Secret = Secret.fromEnv(Env.load() ++ sys.env)

  override def databaseSchema: String = "estpoker"

  override def databaseInit: Connection => Unit = c =>
    val st = c.createStatement()
    try
      st.execute("""create schema if not exists "estpoker"""")
      st.execute("""set search_path to "estpoker"""")
    finally st.close()

  override def boot(): Unit =
    val _ = Rooms.sweep()
    super.boot()
