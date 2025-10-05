package skatemap.core

import scala.concurrent.duration.FiniteDuration

final case class CleanupConfig(
  initialDelay: FiniteDuration,
  interval: FiniteDuration
) {
  require(initialDelay.toMillis > 0, "initialDelay must be positive")
  require(interval.toMillis > 0, "interval must be positive")
}
