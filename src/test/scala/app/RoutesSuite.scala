package app

import java.nio.charset.StandardCharsets

import io.eezo.db.*
import io.eezo.db.Scopes.*
import io.eezo.http.Body
import io.eezo.http.Method
import io.eezo.http.Request
import io.eezo.http.Response
import io.eezo.http.Session
import io.eezo.generated.Routes
import munit.FunSuite
import models.PokerSession
import rooms.Rooms
import rooms.TestDb

class RoutesSuite extends FunSuite:

  private def get(path: String, session: Session = Session.empty): Response =
    TestDb.around(
      Routes
        .table()
        .dispatch(
          Request(Method.GET, path, Map.empty, Map.empty, Array.empty, Map.empty, session)
        )
    )

  private def post(path: String, fields: String, session: Session): Response =
    TestDb.around(
      Routes
        .table()
        .dispatch(
          Request(
            Method.POST,
            path,
            Map.empty,
            Map("Content-Type" -> Seq("application/x-www-form-urlencoded")),
            fields.getBytes(StandardCharsets.UTF_8),
            Map.empty,
            session
          )
        )
    )

  private def html(response: Response): String = response.body match
    case Body.Html(html) => html.render
    case other           => fail(s"expected a page, got $other")

  private def read(path: String): (Response, String, Session) =
    val response = get(path)
    val body = html(response)
    val token = """name="_csrf" value="([^"]+)"""".r.findFirstMatchIn(body).get.group(1)
    (response, token, response.session.getOrElse(fail("no session")))

  private def kill(session: PokerSession): Unit =
    TestDb.around(
      transact(Tx ?=>
        Table[PokerSession].update(
          session.copy(expiresAt = java.time.Instant.now().minusSeconds(1))
        )
      )
    )

  TestDb.syncSchema()

  test("the compiled stylesheet is served"):
    val response = get("/tailwindcss")
    assertEquals(response.status, 200)
    assertEquals(response.header("Content-Type"), Some("text/css; charset=utf-8"))

  test("dealing seats the creator on a fresh table"):
    val (_, token, session) = read("/")
    val response = post("/", s"_csrf=$token&name=Ada", session)
    assertEquals(response.status, 303)
    val location = response.header("Location").getOrElse(fail("no redirect"))
    assert(location.startsWith("/s/"), location)
    val code = location.stripPrefix("/s/")
    val seated = response.session.getOrElse(fail("no session"))
    assertEquals(seated.get("playerName"), Some("Ada"))
    assert(seated.get(s"seat.$code").isDefined, "expected a seat in the session")

  test("a stranger on a room link is asked for a name first"):
    val (session, _) = TestDb.around(Rooms.create("Ada"))
    val response = get(s"/s/${session.code}")
    assertEquals(response.status, 303)
    assertEquals(response.header("Location"), Some(s"/s/${session.code}/join"))

  test("joining seats a named player and walks them into the room"):
    val (session, _) = TestDb.around(Rooms.create("Ada"))
    val (_, token, s) = read("/")
    val response = post(s"/s/${session.code}/join", s"_csrf=$token&name=Grace", s)
    assertEquals(response.status, 303)
    assertEquals(response.header("Location"), Some(s"/s/${session.code}"))
    val seated = response.session.getOrElse(fail("no session"))
    assertEquals(seated.get("playerName"), Some("Grace"))
    assert(seated.get(s"seat.${session.code}").isDefined, "expected a seat in the session")

  test("an unknown code hands you back to the front page"):
    val (_, token, s) = read("/")
    val response = post("/s/join", s"_csrf=$token&code=XXXXXX&name=Grace", s)
    assertEquals(response.status, 303)
    assertEquals(response.header("Location"), Some("/"))

  test("an expired code hands you back to the front page too"):
    val (session, _) = TestDb.around(Rooms.create("Ada"))
    val (_, token, s) = read("/")
    kill(session)
    val response = post("/s/join", s"_csrf=$token&code=${session.code}&name=Grace", s)
    assertEquals(response.status, 303)
    assertEquals(response.header("Location"), Some("/"))

  test("a room link to an expired table is gone"):
    val (session, mod) = TestDb.around(Rooms.create("Ada"))
    kill(session)
    val (_, token, s) = read("/")
    val response = get(s"/s/${session.code}", s.set(s"seat.${session.code}", mod.id.show))
    assertEquals(response.status, 404)
