package components

import io.eezo.core.html.*
import io.eezo.core.html.Tags.*
import rooms.Cards
import rooms.Rooms

object RoomFelt:

  def apply(view: RoomView): Html =
    div(
      Attrs.cls := "relative flex w-full max-w-5xl flex-1 flex-col items-center justify-center gap-4",
      div(Attrs.cls := "table-felt"),
      ring(view, firstRow(view.room)),
      if view.revealed then verdict(view.room) else div(),
      ring(view, secondRow(view.room)),
      if view.revealed then div() else RoomHand(view)
    )

  private def ring(view: RoomView, seats: List[Rooms.Seat]): Html =
    div(
      Attrs.cls := "relative z-10 flex flex-wrap items-end justify-center gap-6",
      seats.map(PlayerSeat(view))
    )

  private def firstRow(room: Rooms.Room): List[Rooms.Seat] =
    room.seats.take((room.seats.size + 1) / 2)

  private def secondRow(room: Rooms.Room): List[Rooms.Seat] =
    room.seats.drop((room.seats.size + 1) / 2)

  private def verdict(room: Rooms.Room): Html =
    val summary = Cards.summary(room.seats.flatMap(_.vote))
    div(
      Attrs.cls := "relative z-10 flex flex-col items-center gap-0.5",
      summary.average.fold(div())(avg =>
        span(Attrs.cls := "text-xs uppercase tracking-widest text-stone-400", "average") ++ span(
          Attrs.cls := "text-5xl font-bold text-amber-200 drop-shadow-[0_2px_8px_rgba(0,0,0,0.5)]",
          avg
        )
      )
    )
