package skatemap.infra

import com.google.inject.{AbstractModule, Provides}
import com.typesafe.config.Config
import skatemap.core._

import java.time.Clock
import javax.inject.Singleton
import scala.concurrent.duration._

class SkatemapLiveModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[LocationStore]).to(classOf[InMemoryLocationStore])
    bind(classOf[Broadcaster]).to(classOf[InMemoryBroadcaster])
    bind(classOf[CleanupService]).asEagerSingleton()
  }

  @Provides
  @Singleton
  def provideClock(): Clock = Clock.systemUTC()

  @Provides
  @Singleton
  def provideStreamConfig(config: Config): StreamConfig =
    StreamConfig(
      batchSize = getPositiveInt(config, "skatemap.stream.batchSize"),
      batchInterval = getPositiveInt(config, "skatemap.stream.batchIntervalMillis").millis
    )

  @Provides
  @Singleton
  def provideCleanupConfig(config: Config): CleanupConfig =
    CleanupConfig(
      initialDelay = getPositiveInt(config, "skatemap.cleanup.initialDelaySeconds").seconds,
      interval = getPositiveInt(config, "skatemap.cleanup.intervalSeconds").seconds
    )

  @Provides
  @Singleton
  def provideLocationConfig(config: Config): LocationConfig =
    LocationConfig(ttl = getPositiveInt(config, "skatemap.location.ttlSeconds").seconds)

  private def getPositiveInt(config: Config, path: String): Int = {
    require(config.hasPath(path), s"Required configuration missing: $path. Add it to application.conf")
    val value = config.getInt(path)
    require(value > 0, s"Invalid configuration: $path=${value.toString} (must be positive)")
    value
  }
}
