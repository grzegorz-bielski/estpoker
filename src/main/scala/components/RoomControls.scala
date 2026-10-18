package components

import io.eezo.core.html.*
import io.eezo.core.html.Tags.*
import io.eezo.live.Live
import models.Phase

object RoomControls:

  def apply(view: RoomView): Html =
    val words = view.room.session.phase match
      case Phase.Voting   => "Waiting for every vote to land."
      case Phase.Revealed =>
        if view.mod then "Read the cards, then deal again."
        else "Waiting for the dealer to deal the next round."
    div(
      Attrs.cls := "relative z-10 mt-3 flex flex-col items-center gap-2",
      p(Attrs.cls := "text-sm text-stone-400", words),
      if view.revealed && view.mod then
        button(Live.onClick("reset"), Attrs.cls := "btn-deal", "Deal the next round")
      else if !view.revealed && view.mod then
        button(Live.onClick("reveal"), Attrs.cls := "btn-deal btn-deal--quiet", "Reveal now")
      else div()
    )
