package components

import models.Phase
import rooms.Rooms

final case class RoomView(room: Rooms.Room, viewer: PokerRoom.Seat):

  def revealed: Boolean = room.session.phase == Phase.Revealed
  def mod: Boolean = viewer.mod
