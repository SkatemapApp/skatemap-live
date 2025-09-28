package skatemap.core

import scala.concurrent.duration.{DurationInt, FiniteDuration}

final case class StreamConfig(
  batchSize: Int,
  batchInterval: FiniteDuration
)

object StreamConfig {
  def default: StreamConfig = StreamConfig(100, 500.millis)
}
