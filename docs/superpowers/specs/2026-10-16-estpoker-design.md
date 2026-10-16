# estpoker — design

Planning-poker (scrum poker) for a distributed team. No login, no accounts: create a session,
share the link, play. Everything interactive is server-side; the browser never runs custom
JavaScript. Framework: [Eezo 0.2.0](https://eezo.io/) (Scala, `EezoApp`), Scala 3.10.0, braceless
syntax throughout. Styling: Tailwind, compiled once with the standalone CLI, served by the app.

## What was agreed

- Sessions, players and votes live in **Postgres**; a session expires a few days after creation
  (TTL = 3 days), enforced at lookup *and* by a sweep at boot.
- **Flow**: create session → share link → players open the link, type a name (always remembered
  in the browser), join. Rounds: everyone picks a card, reveal happens **automatically when all
  players have voted**, with a manual **"reveal now"** override for the creator, then a new round.
- **Live**: one Eezo `Topic` per room plus one `Component[RoomState]` mounted on the room page;
  zero custom JS. CSS-only card-flip animation at reveal.
- **Look**: a real poker table — green felt, card backs before reveal, faces after, raised
  selected card. Tailwind utilities + a few hand-written CSS blocks (flip, felt texture).
- Max players is a **sanity gate**, set very high (50).

## Data model

```scala
final case class PokerSession(
    id: Id[PokerSession], code: String, createdBy: String,
    round: Int, phase: Phase, expiresAt: Instant) derives Table

final case class Player(
    id: Id[Player], sessionId: Ref[PokerSession], name: String, mod: Boolean) derives Table

final case class Vote(
    id: Id[Vote], sessionId: Ref[PokerSession], playerId: Ref[Player],
    round: Int, card: String) derives Table
```

- `Phase` is a small enum with a `given Column[Phase] = Column[String].imap(...)` (`voting` |
  `revealed`).
- Round starts at 1. One vote per `(playerId, round)`: re-voting replaces the row. Votes are
  keyed by round so an old round's votes never leak into a new one.
- Session `code`: 6 characters, unambiguous alphabet (no `0/O/1/I/L`), the whole invite URL is
  `/s/:code`.
- Deck: `0 1 2 3 5 8 13 21 34 55 89` plus special cards `inf` (∞), `unsure` (?), `coffee` (☕).
- FKs (`Ref`) have no `ON DELETE CASCADE` in eezo, so the TTL sweep deletes votes, then players,
  then sessions, inside one transaction.

## Identity (no login)

The signed eezo session cookie carries:
- `playerName` — the remembered display name (cookie lifetime = the browser's, framework
  limitation: no custom Max-Age yet); the join form always pre-fills from it.
- `seat_<code>` — `"playerId"` for the current session.

Joining means: `POST /s/:code/join` with a name → insert `Player` → seat the cookie → redirect to
the room. Refreshing or returning through the same link keeps your seat. The creator is `mod`.

## Live room

- `object PokerRoom { final case class Tick(code: String); val changed: Topic[Tick] }`
- Component state is a `RoomState` snapshot read from Postgres: session meta, players
  (name, mod, joined), this player's vote, everyone's vote (current round), phase, round.
- **Discipline**: every event (`vote`, `reveal`, `reset`, a join by someone else) does one
  `transact` against Postgres, then publishes `Tick(code)`. `handle` never mutates local state
  from the event path. The subscription transition ignores the payload and re-reads the room from
  the DB, so all open pages converge identically — the signer's page inclusive.
- Late joiners and expired pages self-heal: sockets rejoin with a full resync; every page
  re-derives its state from the DB.

## Routes (file layout = routes)

| file | route | |
|---|---|---|
| `app/Index.scala` | `GET /` | landing: create button, join form (code + remembered name) |
| `app/Create.scala` | `POST /` | create session + seat the creator, redirect to room |
| `app/s/_code/Show.scala` | `GET /s/:code` | room page; redirects to join when unseated |
| `app/s/_code/join/Show.scala` | `GET /s/:code/join` | name form, pre-filled |
| `app/s/_code/join/Create.scala` | `POST /s/:code/join` | join, seat, redirect |
| `app/TailwindCss.scala` | `GET /tailwind.css` | serves the compiled stylesheet from resources |
| `app/Gone.scala` | — | expiry/not-found handling by thrown refusals, not a route |

## Styling

- `tailwind/input.css` + `tailwind.config.js`; compiled with `npx tailwindcss` into
  `src/main/resources/estpoker/tailwind.css` (the committed artifact the route serves).
- Custom CSS blocks (not utilities): card 3D flip keyframes, felt texture, card back pattern.

## Errors

- Unknown or expired code → crisp "session expired or not found" page.
- Illegal live events (vote after reveal, vote before join, first to a full room) are refused in
  `handle` with no state change; every page re-reads truth, so it self-corrects.
- Socket drops: Eezo's own grace window + resync. Room re-derives from Postgres.

## Testing

- munit + a test `DbApp` pointed at the local Postgres (the `eezo-pg` Docker convention).
- Round state machine tests against real rows: vote → auto-reveal, re-vote replaces, moderator
  override, reset clears, expiry sweep cascades.
- Route dispatch tests: create/join/seating/redirect/expired-code.
- Live component: `handle`/`render` as pure functions over state (`handle` DB path covered by the
  state machine tests); no WebSocket-driver tests for the manual smoke test to check live.

## Explicitly out

No story backlog, no round history, no deploy config beyond what `eezo deploy` needs.
