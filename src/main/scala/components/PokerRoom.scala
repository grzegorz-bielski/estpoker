package components

import io.eezo.core.Id
import io.eezo.core.html.*
import io.eezo.core.html.Tags.*
import io.eezo.live.*
import models.Player
import models.PokerSession
import rooms.Rooms

object PokerRoom:

  final case class Tick(code: String)

  lazy val changed: Topic[Tick] = new Topic[Tick]

  final case class Seat(
      code: String,
      sessionId: Id[PokerSession],
      playerId: Id[Player],
      name: String,
      mod: Boolean
  )

final class PokerRoom(seat: PokerRoom.Seat) extends Component[Option[Rooms.Room]]:

  import PokerRoom.*

  def load: Option[Rooms.Room] = Rooms.room(seat.code)

  def init(ctx: Init[Option[Rooms.Room]]): Option[Rooms.Room] =
    ctx.subscribe(changed)(refresh)
    load

  private def refresh(tick: Tick, state: Option[Rooms.Room]): Option[Rooms.Room] =
    if tick.code == seat.code then load else state

  def handle(event: Event, state: Option[Rooms.Room]): Option[Rooms.Room] =
    event.name match
      case s"vote-$card" => playCard(card)
      case "reveal"      => reveal()
      case "reset"       => deal()
      case _             => state

  private def playCard(card: String): Option[Rooms.Room] =
    if Rooms.play(seat.playerId, seat.sessionId, card) then changed.publish(Tick(seat.code))
    load

  private def reveal(): Option[Rooms.Room] =
    if Rooms.reveal(seat.playerId, seat.sessionId) then changed.publish(Tick(seat.code))
    load

  private def deal(): Option[Rooms.Room] =
    if Rooms.reset(seat.playerId, seat.sessionId) then changed.publish(Tick(seat.code))
    load

  def render(room: Option[Rooms.Room]): Html =
    room.fold(gone)(present)

  private def present(room: Rooms.Room): Html =
    val view = RoomView(room, seat)
    div(
      Attrs.cls := "room-scene relative flex h-dvh w-full flex-col items-center overflow-hidden px-4 pb-3 pt-3",
      marker(room),
      RoomFelt(view),
      RoomControls(view),
      leaveTheTable
    )

  private def marker(room: Rooms.Room): Html =
    div(
      Attrs.cls := "relative z-10 flex w-full justify-between px-2 text-sm text-stone-300",
      span(span("Table "), strong(Attrs.cls := "tracking-widest text-amber-200", room.session.code)),
      span(span("Round "), strong(room.session.round))
    )

  private def leaveTheTable: Html =
    a(
      Attrs.cls := "absolute bottom-2 right-4 z-10 text-xs text-stone-500 transition-colors hover:text-amber-200",
      Attrs.href := "/",
      "leave table"
    )

  private def gone: Html =
    div(
      Attrs.cls := "room-scene flex h-dvh flex-col items-center justify-center gap-4 text-center",
      p(Attrs.cls := "text-2xl text-stone-300", "This room folded its cards."),
      a(Attrs.cls := "btn-deal no-underline", Attrs.href := "/", "Deal a fresh one")
    )
