# estpoker

Planning poker (scrum poker) for a distributed team. No accounts: create a table, share the link
(`/s/:code`), everyone picks a card. Everything interactive is server-side; the browser runs no
custom JavaScript.

Framework: [Eezo](https://eezo.io) 0.2.0 (`EezoApp`), Scala 3, Postgres, Tailwind.

## Requirements

- JDK 25 and sbt 2
- Postgres (defaults to `jdbc:postgresql://localhost:5442/eezo`, user/password `postgres`)
- The standalone Tailwind CLI is fetched into `tools/` on first use

## Setup

```sh
docker run -d --name eezo-pg -p 5442:5432 \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=eezo postgres:16

sbt "eezoSync --apply"   # create the tables from the models
```

## Configuration

`EEZO_SECRET` — the key the session cookie is signed with, so seats survive a restart — is read
from `.env` at boot. The real environment wins over the file; with neither set, the app generates a
throwaway and signs everyone out on restart.

The database takes the `EEZO_DB_URL` / `EEZO_DB_USER` / `EEZO_DB_PASS` environment variables when
set, and otherwise the postgres defaults above.

## Run

```sh
sbt run          # serve on :8080
sbt eezoDev      # serve and restart on every save
sbt eezoStop     # stop the dev server
```

## Styles

Tailwind compiles `tailwind/input.css` into `src/main/resources/estpoker/tailwind.css`, which the
app serves at `/tailwindcss`. `tailwind/` is a watch trigger, so `eezoDev` regenerates it on both
Scala and stylesheet edits; `run`, `test` and `package` get it through the compile they already do.

```sh
sbt tailwindBuild   # regenerate the stylesheet by hand
```

## Test

```sh
sbt test         # needs the Postgres above
```

## Layout

```
.env             local settings (EEZO_SECRET, ...)
src/main/scala/
  app/           routes (file path = URL path)
  components/    the live room and its view pieces
  estpoker/      entry point, schema, pages, seats
  models/        tables: PokerSession, Player, Vote
  rooms/         the round state machine and the deck
tailwind/        input.css
tools/           tailwindcss (gitignored, fetched)
```
