package skatemap.core

import scala.concurrent.duration._

final case class CleanupConfig(
  initialDelay: FiniteDuration,
  interval: FiniteDuration
) {
  require(initialDelay.toMillis > 0, "initialDelay must be positive")
  require(interval.toMillis > 0, "interval must be positive")
}

object CleanupConfig {
  def default: CleanupConfig = CleanupConfig(
    initialDelay = 10.seconds,
    interval = 10.seconds
  )
}
