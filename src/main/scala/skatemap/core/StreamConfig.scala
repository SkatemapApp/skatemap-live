package skatemap.core

import scala.concurrent.duration.FiniteDuration

final case class StreamConfig(
  batchSize: Int,
  batchInterval: FiniteDuration
)
