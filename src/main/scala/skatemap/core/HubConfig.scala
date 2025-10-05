package skatemap.core

import scala.concurrent.duration.FiniteDuration

final case class HubConfig(
  ttl: FiniteDuration,
  cleanupInterval: FiniteDuration
)
