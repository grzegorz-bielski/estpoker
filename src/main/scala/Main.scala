import io.eezo.EezoApp
import io.eezo.db.Schema
import io.eezo.generated.Routes
import io.eezo.http.RouteTable

/** The whole entry point. `main` is inherited and dispatches: `sbt run` serves on port 8080,
  * `eezo dev` adds the drift check and restart-on-save, and `eezo db status|sync|freeze|migrate`
  * are the schema commands.
  *
  * `schema` is abstract on `EezoApp`, so an application on the umbrella always says what it has,
  * and a new one has no tables yet. When you add a model with `derives Table`, declare it in a
  * Schema and name that here instead of `Schema.empty`:
  *
  * {{{
  * object AppSchema extends Schema { val things = table[Thing] }
  * // and in Main:  override def schema: Schema = AppSchema
  * }}}
  */
object Main extends EezoApp {
  override def schema: Schema     = Schema.empty
  override def routes: RouteTable = Routes.table()
}
