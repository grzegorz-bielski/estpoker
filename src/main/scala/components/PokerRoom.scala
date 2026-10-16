package components

import io.eezo.core.html.*
import io.eezo.core.html.Tags.*
import io.eezo.live.*
import models.Phase
import rooms.Cards
import rooms.Rooms

object PokerRoom:

  import io.eezo.core.Id
  import models.Player
  import models.PokerSession

  /** A room that moved: play, reveal, reset, and someone taking a seat too. */
  final case class Tick(code: String)

  /** One topic carries every room's news; each page only listens for its own code. In-process, as the
    * framework says: all this sync lives on one machine, and that is exactly enough.
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

/** A live room, run by a deck and a felt. State is nothing but the room as Postgres says it stands: every
  * card laid, every reveal, is one transaction plus a tick on the room's topic, and every open page re-reads
  * the room on that tick, so all of them converge on the same truth. The browser runs none of it.
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

  /** One card laid down: the DB moves, the room's pages are told, and the state is re-read so this page is as
    * converged as every other, ticks or no ticks.
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
      Attrs.cls :=
        "room-scene flex h-dvh flex-col items-center justify-center gap-4 text-center",
      p(Attrs.cls := "text-2xl text-stone-300", "This room folded its cards."),
      a(
        Attrs.cls := "btn-deal no-underline",
        Attrs.href := "/",
        "Deal a fresh one"
      )
    )

  private def felted(room: Rooms.Room): Html =
    val revealed = room.session.phase == Phase.Revealed
    div(
      Attrs.cls := "room-scene flex h-dvh w-full flex-col items-center overflow-hidden px-4 pb-3 pt-3",
      marker(room),
      scene(room, revealed),
      controls(room, revealed)
    )

  /** The table's name and round, as plain words in the screen's corners — badges got in the way. The road
    * home sits between them, quiet but always in reach.
    */
  private def marker(room: Rooms.Room): Html =
    div(
      Attrs.cls := "relative z-10 flex w-full items-center justify-between px-2 text-sm text-stone-300",
      span(span("Table "), strong(Attrs.cls := "tracking-widest text-amber-200", room.session.code)),
      a(
        Attrs.cls := "text-xs text-stone-500 transition-colors hover:text-amber-200",
        Attrs.href := "/",
        "leave table"
      ),
      span(span("Round "), strong(room.session.round))
    )

  /** The felt: every seat around it, cards face down or face up as the round says. The felt is the backdrop
    * now — an absolutely placed oval the benches ride *on*, never under, on every screen.
    */
  private def scene(room: Rooms.Room, revealed: Boolean): Html =
    div(
      Attrs.cls := "relative flex w-full max-w-5xl flex-1 flex-col items-center justify-center gap-4",
      tablecloth,
      ring(room, revealed, firstRow(room)),
      theRoundReads(room, revealed),
      ring(room, revealed, secondRow(room)),
      inYourHands(room, revealed)
    )

  /** The table's single number, sitting just above the cards; nothing to read while the backs face you.
    */
  private def theRoundReads(room: Rooms.Room, revealed: Boolean): Html =
    if revealed then verdict(room) else div()

  /** Your deck only rides where it belongs: below your bench, while the round is still young. */
  private def inYourHands(room: Rooms.Room, revealed: Boolean): Html =
    if revealed then div() else hand(room)

  /** Seats ride two benches around the oval: half above, half below the centre line. */
  private def ring(room: Rooms.Room, revealed: Boolean, seats: List[Rooms.Seat]): Html =
    div(
      Attrs.cls := "relative z-10 flex flex-wrap items-end justify-center gap-6",
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
      cardFor(room, revealed, place)
    )

  private def nameOf(place: Rooms.Seat): Html =
    div(
      Attrs.cls := "flex items-center gap-1.5 text-stone-200",
      if place.player.mod then span(Attrs.cls := "text-amber-300", strong("♛ "), strong(place.player.name))
      else span(place.player.name)
    )

  /** One card: face down until the round shows it, then turned over with a CSS flip. The card's own DOM never
    * changes shape, so the flip animates and the differ only patches a class.
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

  /** The felt itself, under every card: pure decoration, patched but never diffed for content. It sits
    * absolutely behind the benches, so its edges can never cover a name, a card, or a word.
    */
  private val tablecloth: Html =
    div(Attrs.cls := "table-felt")

  /** The round reads out one number — the table's average — drawn as a caption over a numeral, not a badge.
    */
  private def verdict(room: Rooms.Room): Html =
    val cards = room.seats.flatMap(seat => seat.vote.toList)
    val summary = Cards.summary(cards)
    div(
      Attrs.cls := "relative z-10 flex flex-col items-center gap-0.5",
      summary.average.fold(div())(avg =>
        span(Attrs.cls := "text-xs uppercase tracking-widest text-stone-400", "average") ++ span(
          Attrs.cls := "text-5xl font-bold text-amber-200 drop-shadow-[0_2px_8px_rgba(0,0,0,0.5)]",
          avg
        )
      )
    )

  /** Your own deck: every card of it, one click each. The one you laid rides high. */
  private def hand(room: Rooms.Room): Html =
    laidAlready(room).fold(blankHand)(mine =>
      div(
        Attrs.cls := "relative z-10 mt-4 flex flex-col items-center gap-2",
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
    )

  private def blankHand: Html =
    div(
      Attrs.cls := "relative z-10 mt-6 text-stone-500",
      "Waiting for the first cards to land."
    )

  private def laidAlready(room: Rooms.Room): Option[Rooms.Seat] =
    room.seats.find(place => place.player.id == this.seat.playerId)

  /** What sits at the bottom: the moderator's two reigns, or the players' patience. */
  private def controls(room: Rooms.Room, revealed: Boolean): Html =
    val strings = room.session.phase match
      case Phase.Voting   => "Waiting for every vote to land."
      case Phase.Revealed =>
        if seat.mod then "Read the cards, then deal again."
        else "Waiting for the dealer to deal the next round."
    div(
      Attrs.cls := "relative z-10 mt-3 flex flex-col items-center gap-2",
      p(Attrs.cls := "text-sm text-stone-400", strings),
      if revealed && seat.mod then
        button(Live.onClick("reset"), Attrs.cls := "btn-deal", "Deal the next round")
      else if !revealed && seat.mod then
        button(Live.onClick("reveal"), Attrs.cls := "btn-deal btn-deal--quiet", "Reveal now")
      else div()
    )
