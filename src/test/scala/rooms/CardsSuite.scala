package rooms

import munit.FunSuite

class CardsSuite extends FunSuite:

  test("the average folds in numeric cards and ignores the specials"):
    assertEquals(Cards.summary(List("5", "8", "coffee", "unsure")).average, Some("6.5"))

  test("an exact average never shows a trailing .0"):
    assertEquals(Cards.summary(List("5", "5", "5")).average, Some("5"))

  test("the spread reaches over every numeric card"):
    val summary = Cards.summary(List("0", "89", "inf", "3"))
    assertEquals(summary.lowest, Some("0"))
    assertEquals(summary.highest, Some("89"))

  test("no numeric card at all is an empty summary"):
    val summary = Cards.summary(List("coffee", "coffee"))
    assertEquals(summary.average, None)
    assertEquals(summary.lowest, None)
    assertEquals(summary.highest, None)

  test("counts show how often each face came up"):
    assertEquals(Cards.summary(List("5", "5", "8", "coffee")).counts, List(("5", 2), ("8", 1), ("☕", 1)))
