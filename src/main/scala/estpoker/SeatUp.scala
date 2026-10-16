package estpoker

import io.eezo.http.Request
import io.eezo.http.Response
import rooms.Rooms

/** Everything the two join forms share: a code and a name in, a seat or a refusal out. The landing form
  * arrives here with the code as a field ((`POST /s/join`)); the room's own form names it in the path ((POST
  * /s/:code/join)); both end up in [[enter]].
  */
object SeatUp:

  def enter(code: String, name: String, request: Request): Response =
    if name.isEmpty then TakeSeat.out(request, "your table needs to know your name")
    else if code.isEmpty then TakeSeat.out(request, "a table code is wanted or nothing happens")
    else
      Rooms.join(code, name) match
        case Left(Rooms.JoinError.Unknown) => TakeSeat.out(request, "no table found")
        case Left(Rooms.JoinError.Expired) =>
          TakeSeat.out(request, "that table's chips are in the dark already")
        case Left(Rooms.JoinError.Full) =>
          TakeSeat.out(request, "that table is full — start another one")
        case Right((session, player)) => TakeSeat.into(request, session, player)
