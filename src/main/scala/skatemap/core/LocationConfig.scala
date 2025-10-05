package skatemap.core

import scala.concurrent.duration.FiniteDuration

final case class LocationConfig(
  ttl: FiniteDuration
) {
  require(ttl.toMillis > 0, "ttl must be positive")
}
