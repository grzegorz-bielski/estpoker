package components

import io.eezo.core.html.*
import io.eezo.core.html.Tags.*
import io.eezo.live.{Component, Event, Init, Live, Topic}
import models.Phase
import rooms.Cards
import rooms.Rooms

object PokerRoom:

  import io.eezo.core.Id
  import models.Player
  import models.PokerSession

  /** A room that moved: play, reveal, reset, and someone taking a seat too. */
  final case class Tick(code: String)

  /** One topic carries every room's news; each page only listens for its own code. In-process,
    * as the framework says: all this sync lives on one machine, and that is exactly enough.
    */
  lazy val changed: Topic[Tick] = new Topic[Tick]

  /** Who is looking at the felt: the seat cookie's facts, validated against the DB. */
  final case class Seat(
      code: String,
      sessionId: Id[PokerSession],
      playerId: Id[Player],
      name: String,
      mod: Boolean
  )

end PokerRoom

/** A live room, run by a deck and a felt. State is nothing but the room as Postgres says it
  * stands: every card laid, every reveal, is one transaction plus a tick on the room's topic,
  * and every open page re-reads the room on that tick, so all of them converge on the same
  * truth. The browser runs none of it.
  */
final class PokerRoom(seat: PokerRoom.Seat) extends Component[Option[Rooms.Room]]:

  import PokerRoom.*
  import io.eezo.core.Id

  /** What the room looks like right now. */
  def load: Option[Rooms.Room] = Rooms.room(seat.code)

  def init(ctx: Init[Option[Rooms.Room]]): Option[Rooms.Room] =
    ctx.subscribe(changed)(refresh)
    load

  /** A tick: one room moved. Only the tick's room refreshes; the rest ignore their mail. */
  private def refresh(tick: Tick, state: Option[Rooms.Room]): Option[Rooms.Room] =
    if tick.code == seat.code then load else state

  def handle(event: Event, state: Option[Rooms.Room]): Option[Rooms.Room] =
    event.name match
      case s"vote-$card" => playCard(card)
      case "reveal"      => reveal()
      case "reset"       => deal()
      case _             => state

  /** One card laid down: the DB moves, the room's pages are told, and the state is re-read so
    * this page is as converged as every other, ticks or no ticks.
    */
  private def playCard(card: String): Option[Rooms.Room] =
    if Rooms.play(seat.playerId, seat.sessionId, card) then changed.publish(Tick(seat.code))
    load

  private def reveal(): Option[Rooms.Room] =
    if Rooms.reveal(seat.playerId, seat.sessionId) then changed.publish(Tick(seat.code))
    load

  private def deal(): Option[Rooms.Room] =
    if Rooms.reset(seat.playerId, seat.sessionId) then changed.publish(Tick(seat.code))
    load

  // -- the table -----------------------------------------------------------------------------

  /** The whole room, folded into one root element the differ can trust. */
  def render(room: Option[Rooms.Room]): Html =
    room.fold(gone)(felted)

  private def gone: Html =
    div(
      Attrs.cls := "flex min-h-screen flex-col items-center justify-center gap-4 text-center",
      p(Attrs.cls := "text-2xl text-stone-300", "This room folded its cards."),
      a(
        Attrs.cls := "btn-deal no-underline",
        Attrs.href := "/",
        "Deal a fresh one"
      )
    )

  private def felted(room: Rooms.Room): Html =
    val revealed = room.session.phase == Phase.Revealed
    val round    = room.session.round
    div(
      Attrs.cls := "flex min-h-screen w-full flex-col items-center px-4 pb-12 pt-8",
      marker(room, round),
      scene(room, revealed),
      controls(room, revealed)
    )

  /** The small strip that names the table and counts the hands. */
  private def marker(room: Rooms.Room, round: Int): Html =
    div(
      Attrs.cls := "mb-4 flex flex-wrap items-center justify-center gap-3 text-sm",
      span(Attrs.cls := "chip chip-strong", "Table ", room.session.code),
      span(Attrs.cls := "chip", "Round ", round),
      span(Attrs.cls := "chip", "a few days left")
    )

  /** The felt: every seat around it, cards face down or face up as the round says. */
  private def scene(room: Rooms.Room, revealed: Boolean): Html =
    div(
      Attrs.cls := "flex w-full max-w-5xl flex-col items-center gap-2",
      ring(room, revealed, firstRow(room)),
      tablecloth,
      ring(room, revealed, secondRow(room)),
      tally(room, revealed)
    )

  /** Seats ride two benches around the oval: half above, half below the centre line. */
  private def ring(room: Rooms.Room, revealed: Boolean, seats: List[Rooms.Seat]): Html =
    div(
      Attrs.cls := "flex flex-wrap items-end justify-center gap-6",
      seats.map(person(room, revealed))
    )

  private def firstRow(room: Rooms.Room): List[Rooms.Seat] =
    room.seats.take((room.seats.size + 1) / 2)

  private def secondRow(room: Rooms.Room): List[Rooms.Seat] =
    room.seats.drop((room.seats.size + 1) / 2)

  private def person(room: Rooms.Room, revealed: Boolean)(place: Rooms.Seat): Html =
    div(
      Attrs.cls := "flex flex-col items-center gap-2",
      nameOf(place),
      cardFor(room, revealed, place),
      hint(place)
    )

  private def nameOf(place: Rooms.Seat): Html =
    div(
      Attrs.cls := "flex items-center gap-1.5 text-stone-200",
      if place.player.mod then span(Attrs.cls := "text-amber-300", strong("♛ "), strong(place.player.name))
      else span(place.player.name)
    )

  /** One card: face down until the round shows it, then turned over with a CSS flip. The card's
    * own DOM never changes shape, so the flip animates and the differ only patches a class.
    */
  private def cardFor(room: Rooms.Room, revealed: Boolean, place: Rooms.Seat): Html =
    div(
      Attrs.cls := cardMount(revealed, place),
      div(
        Attrs.cls := "card-face",
        span(
          Attrs.cls := "text-4xl font-bold",
          Cards.label(place.vote.getOrElse("")).getOrElse("")
        )
      ),
      div(
        Attrs.cls := "card-back",
        span(Attrs.cls := "card-back-glyph", if place.vote.isDefined then "✦" else "✧")
      )
    )

  private def cardMount(revealed: Boolean, place: Rooms.Seat): String =
    "card3d" + (if revealed then " flipped" else "")

  private def hint(place: Rooms.Seat): Html =
    val laid = place.vote.isDefined
    span(
      Attrs.cls := "text-xs " + (if laid then "text-emerald-300" else "text-stone-500"),
      if laid then "has voted" else "still thinking"
    )

  /** The felt itself, under every card: pure decoration, patched but never diffed for content. */
  private val tablecloth: Html =
    div(
      Attrs.cls := "table-felt",
      span(Attrs.cls := "table-felt-brand", "E ST PO KER")
    )

  /** The honest verdict of the round, or your own hand while it is being played. */
  private def tally(room: Rooms.Room, revealed: Boolean): Html =
    if revealed then verdict(room) else hand(room)

  private def verdict(room: Rooms.Room): Html =
    val cards           = room.seats.flatMap(seat => seat.vote.toList)
    val summary         = Cards.summary(cards)
    div(
      Attrs.cls := "mt-6 flex w-full max-w-3xl flex-col items-center gap-4",
      div(
        Attrs.cls := "flex flex-wrap items-center justify-center gap-4",
        summary.average.map(avg =>
          span(
            Attrs.cls := "chip chip-strong text-lg",
            "average ",
            strong(avg)
          )
        ),
        summary.lowest.map(low => span(Attrs.cls := "chip", "low ", strong(low))),
        summary.highest.map(high => span(Attrs.cls := "chip", "high ", strong(high)))
      ),
      div(
        Attrs.cls := "flex flex-wrap items-center justify-center gap-2",
        summary.counts.map((face, howMany) =>
          span(Attrs.cls := "chip", face, " × ", howMany)
        )
      )
    )

  /** Your own deck: every card of it, one click each. The one you laid rides high. */
  private def hand(room: Rooms.Room): Html =
    laidAlready(room).fold(blankHand) { mine =>
      div(
        Attrs.cls := "mt-10 flex flex-col items-center gap-3",
        p(Attrs.cls := "text-xs uppercase tracking-widest text-stone-400", "Your hand"),
        div(
          Attrs.cls := "flex flex-wrap items-center justify-center gap-2",
          Cards.deck.map(card =>
            button(
              Live.onClick(s"vote-${card.value}"),
              Attrs.cls :=
                "hand-card" + (if mine.vote.contains(card.value) then " hand-card--raised" else ""),
              span(Attrs.cls := "hand-card-label", card.label)
            )
          )
        )
      )
    }

  private def blankHand: Html =
    div(
      Attrs.cls := "mt-10 text-stone-500",
      "Waiting for the first cards to land."
    )

  private def laidAlready(room: Rooms.Room): Option[Rooms.Seat] =
    room.seats.find(place => place.player.id == this.seat.playerId)

  /** What sits at the bottom: the moderator's two reigns, or the players' patience. */
  private def controls(room: Rooms.Room, revealed: Boolean): Html =
    val strings = room.session.phase match
      case Phase.Voting   => "Waiting for every vote to land."
      case Phase.Revealed => "Read the cards, then deal again."
    div(
      Attrs.cls := "mt-8 flex flex-col items-center gap-3",
      p(Attrs.cls := "text-sm text-stone-400", strings),
      if revealed && seat.mod then
        button(Live.onClick("reset"), Attrs.cls := "btn-deal", "Deal the next round")
      else if !revealed && seat.mod then
        button(Live.onClick("reveal"), Attrs.cls := "btn-deal btn-deal--quiet", "Reveal now")
      else div()
    )
