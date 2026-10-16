package rooms

import munit.FunSuite

class CardsSuite extends FunSuite:

  test("the deck is the classic fib set plus inf, unsure and coffee"):
    val values = Cards.deck.map(_.value)
    assertEquals(values, List("0", "1", "2", "3", "5", "8", "13", "21", "34", "55", "89", "inf", "unsure", "coffee"))

  test("every card shows its intended face"):
    assertEquals(Cards.label("8"), Some("8"))
    assertEquals(Cards.label("inf"), Some("∞"))
    assertEquals(Cards.label("unsure"), Some("?"))
    assertEquals(Cards.label("coffee"), Some("☕"))
    assertEquals(Cards.label("42"), None)

  test("only numeric cards carry points"):
    assertEquals(Cards.points("13"), Some(13))
    assertEquals(Cards.points("coffee"), None)
    assertEquals(Cards.points("inf"), None)

  test("an average of numeric cards, ignore the specials"):
    val summary = Cards.summary(List("5", "8", "coffee", "unsure"))
    assertEquals(summary.average, Some("6.5"))

  test("an exact average never shows a trailing .0"):
    val summary = Cards.summary(List("5", "5", "5"))
    assertEquals(summary.average, Some("5"))

  test("the spread reaches over every numeric card on the table"):
    val summary = Cards.summary(List("0", "89", "inf", "3"))
    assertEquals(summary.lowest, Some("0"))
    assertEquals(summary.highest, Some("89"))

  test("no numeric card at all is an empty summary"):
    val summary = Cards.summary(List("coffee", "coffee"))
    assertEquals(summary.average, None)
    assertEquals(summary.lowest, None)
    assertEquals(summary.highest, None)

  test("counts show how often each face came up"):
    val summary = Cards.summary(List("5", "5", "8", "coffee"))
    assertEquals(summary.counts, List(("5", 2), ("8", 1), ("☕", 1)))
