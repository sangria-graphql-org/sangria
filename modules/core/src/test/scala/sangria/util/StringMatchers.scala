package sangria.util

import org.scalactic.AbstractStringUniformity

trait StringMatchers {
  def strippedOfCarriageReturns = new AbstractStringUniformity {
    def normalized(s: String): String = stripCarriageReturns(s)
  }

  def stripCarriageReturns(s: String) = s.replaceAll("\\r", "")

  implicit class StringHelpers(s: String) {
    def stripCR: String = stripCarriageReturns(s)
  }
}
