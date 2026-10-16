package rooms

/** The deck hanging over the felt: the classic Fibonacci run, then the three cards every team asks for —
  * infinity for "too big", the question mark for "cannot say", the coffee cup for "let's step away".
  */
final case class Card(value: String, label: String, points: Option[Int])

object Cards:

  val deck: List[Card] =
    List("0", "1", "2", "3", "5", "8", "13", "21", "34", "55", "89")
      .map(v => Card(v, v, Some(v.toInt))) ++
      List(Card("inf", "∞", None), Card("unsure", "?", None), Card("coffee", "☕", None))

  /** The face a card shows, by the value the browser sent. */
  def label(value: String): Option[String] = deck.find(_.value == value).map(_.label)

  def points(value: String): Option[Int] = deck.find(_.value == value).flatMap(_.points)

  /** What a revealed round says: the average of the numeric cards (one decimal at most, truncated — a card is
    * a hint, not a calculator), the spread they reach, and how often every face came up. Special cards never
    * join the numbers.
    */
  def summary(votes: List[String]): Summary =
    val laid = votes.flatMap(points)
    Summary(
      average = Option.when(laid.nonEmpty)(average(laid)),
      lowest = laid.minOption.map(_.toString),
      highest = laid.maxOption.map(_.toString),
      counts = votes
        .map(label(_))
        .flatten
        .groupMapReduce(identity)(_ => 1)(_ + _)
        .toList
        .sortBy((face, _) => deck.indexWhere(_.label == face))
    )

  /** The agreed picture after a reveal. */
  final case class Summary(
      average: Option[String],
      lowest: Option[String],
      highest: Option[String],
      counts: List[(String, Int)]
  )

  private def average(values: List[Int]): String =
    val sum = values.sum
    val whole = sum / values.size
    val tenth = (sum * 10) / values.size % 10
    if tenth == 0 then whole.toString else s"$whole.$tenth"

end Cards
