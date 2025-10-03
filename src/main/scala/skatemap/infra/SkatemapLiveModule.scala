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
    def getPositiveInt(path: String): Int = {
      require(
        config.hasPath(path),
        s"Required configuration missing: $path. Add it to application.conf"
      )
      val value = config.getInt(path)
      require(
        value > 0,
        s"Invalid configuration: $path=${value.toString} (must be positive)"
      )
      value
    }

    CleanupConfig(
      initialDelay = getPositiveInt("skatemap.cleanup.initialDelaySeconds").seconds,
      interval = getPositiveInt("skatemap.cleanup.intervalSeconds").seconds
    )
  }
}
