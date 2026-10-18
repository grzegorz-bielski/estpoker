# AGENTS.md

Planning-poker web app. Scala 3.10 / **sbt 2**, Eezo framework, Postgres, Tailwind. Server-rendered
HTML with Eezo `Live` for interactivity; no hand-written JavaScript.

## Commands

- `sbt compile` — also runs scalafmt and regenerates the stylesheet.
- `sbt run` — serve on :8080 (forks). `sbt eezoDev` (`~eezoRestart`) serves and restarts on every
  save; `sbt eezoStop` stops it.
- `sbt "eezoSync --apply"` — install the schema from `AppSchema`. Needed before the first
  run/test. `sbt eezoRoutes` prints the route table; `sbt eezoStatus` the DB status.
- `sbt test` — needs Postgres (below). One suite: `sbt "testOnly rooms.RoomsSuite"`.

sbt 2 caches task results, so a re-run with nothing changed prints `Passed: Total 0` (a cache hit,
not a skip). Force a real run with `sbt "testOnly *"`.

## Routes are files

Handlers live under `src/main/scala/app/`. The directory path **is** the URL path, and `_seg` is a
`:seg` param: `app/s/_code/Show.scala` → `GET /s/:code`. The file/object name picks the verb and
`def` name — `Index`→GET/`index`, `New`→GET/`new`, `Show`→GET/`show`, `Edit`→GET/`edit`,
`Create`→POST/`create`, `Update`→PUT/`update`, `Delete`→DELETE/`delete`; any other name is a GET at
`/<name-lowercased>`. Each object defines `def <name>(request: Request): Response`.
`io.eezo.generated.Routes` is generated on compile by the sbt-eezo plugin — never edit it.

## Generated files — do not edit

- `src/main/resources/estpoker/tailwind.css`: built from `tailwind/input.css` by `tailwindBuild`,
  which is wired into `compile`. The CLI is auto-fetched to `tools/tailwindcss` (gitignored, version
  pinned in `build.sbt`).
- `io/eezo/generated/Routes.scala` (under `target/`).

## Style (enforced)

- Braceless Scala 3, no `end` markers. `scalafmtOnCompile := true` formats before every compile, so
  `sbt compile` is the formatter gate.
- Keep comments sparse — the codebase deliberately has almost none.
- `.scalafmt.conf` keeps `*.sbt` on the `sbt1` dialect (scalafmt has no sbt2 dialect), so build
  files keep their braces.

## Tests

- Require Postgres on `localhost:5442` (`postgres`/`postgres`, db `eezo`), schema `estpoker`.
  `docker run -d --name eezo-pg -p 5442:5432 -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=eezo postgres:16`
- `rooms.TestDb` boots against that DB and syncs the schema. Suites wipe tables in `beforeEach` and
  share one database slot, so `Test / parallelExecution := false` is required — never run suites
  concurrently.

## Config and build quirks

- `EEZO_SECRET` is read from a root `.env` by `estpoker.Env` (`Main.secret`); a real environment
  variable wins. `.env` is currently untracked (not in `.gitignore`).
- DB URL/user/pass come from `EEZO_DB_URL` / `EEZO_DB_USER` / `EEZO_DB_PASS`, else the defaults
  above.
- This is sbt 2, whose caching rejects `File`-returning cached tasks: effectful tasks are
  `@transient`, and `Compile / compile` is wrapped in `Def.uncached` in `build.sbt`. Don't add
  `File`/`Path` task outputs without opting out of the cache.

## Layout

- `src/main/scala/app/` — route handlers (path = URL).
- `src/main/scala/components/` — the live table, split into view pieces (`PokerRoom`, `RoomView`,
  `RoomFelt`, `RoomHand`, `RoomControls`, `PlayerSeat`).
- `src/main/scala/estpoker/` — `Main` entrypoint, `AppSchema`, `Env`, pages, seat helpers.
- `src/main/scala/models/` — tables: `PokerSession`, `Player`, `Vote`.
- `src/main/scala/rooms/` — round state machine and deck.
