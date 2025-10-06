package skatemap.test

import java.time.{Clock, Instant, ZoneId}

object TestClock {
  def fixed(epochMillis: Long): Clock = Clock.fixed(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
}
