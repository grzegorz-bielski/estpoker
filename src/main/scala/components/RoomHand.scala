package components

import io.eezo.core.html.*
import io.eezo.core.html.Tags.*
import io.eezo.live.Live
import rooms.Cards
import rooms.Rooms

object RoomHand:

  def apply(view: RoomView): Html =
    laid(view).fold(blank)(mine =>
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

  private def blank: Html =
    div(
      Attrs.cls := "relative z-10 mt-6 text-stone-500",
      "Waiting for the first cards to land."
    )

  private def laid(view: RoomView): Option[Rooms.Seat] =
    view.room.seats.find(place => place.player.id == view.viewer.playerId)
