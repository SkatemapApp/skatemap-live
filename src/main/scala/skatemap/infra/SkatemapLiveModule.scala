package skatemap.infra

import com.google.inject.{AbstractModule, Provides}
import com.typesafe.config.Config
import skatemap.core.{
  Broadcaster,
  CleanupConfig,
  CleanupService,
  InMemoryBroadcaster,
  InMemoryLocationStore,
  LocationStore,
  StreamConfig
}

import java.time.Clock
import javax.inject.Singleton
import scala.concurrent.duration._
import scala.util.Try

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
  def provideStreamConfig(): StreamConfig = StreamConfig.default

  @Provides
  @Singleton
  def provideCleanupConfig(config: Config): CleanupConfig = {
    val initialDelaySeconds = Try {
      if (config.hasPath("skatemap.cleanup.initialDelaySeconds")) {
        config.getInt("skatemap.cleanup.initialDelaySeconds")
      } else {
        10
      }
    }.filter(_ > 0).getOrElse(10)

    val intervalSeconds = Try {
      if (config.hasPath("skatemap.cleanup.intervalSeconds")) {
        config.getInt("skatemap.cleanup.intervalSeconds")
      } else {
        10
      }
    }.filter(_ > 0).getOrElse(10)

    CleanupConfig(
      initialDelay = initialDelaySeconds.seconds,
      interval = intervalSeconds.seconds
    )
  }
}
