package components

import io.eezo.core.html.*
import io.eezo.core.html.Tags.*
import rooms.Cards
import rooms.Rooms

object PlayerSeat:

  def apply(view: RoomView)(place: Rooms.Seat): Html =
    div(
      Attrs.cls := "flex flex-col items-center gap-2",
      name(place),
      card(view.revealed, place)
    )

  private def name(place: Rooms.Seat): Html =
    div(
      Attrs.cls := "flex items-center gap-1.5 text-stone-200",
      if place.player.mod then span(Attrs.cls := "text-amber-300", strong("♛ "), strong(place.player.name))
      else span(place.player.name)
    )

  private def card(revealed: Boolean, place: Rooms.Seat): Html =
    div(
      Attrs.cls := (if revealed then "card3d flipped" else "card3d"),
      div(
        Attrs.cls := "card-face",
        span(Attrs.cls := "text-4xl font-bold", Cards.label(place.vote.getOrElse("")).getOrElse(""))
      ),
      div(
        Attrs.cls := "card-back",
        span(Attrs.cls := "card-back-glyph", if place.vote.isDefined then "✦" else "✧")
      )
    )
