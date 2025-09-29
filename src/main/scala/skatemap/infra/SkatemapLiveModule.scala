package skatemap.infra

import com.google.inject.{AbstractModule, Provides}
import skatemap.core.{
  Broadcaster,
  CleanupService,
  InMemoryBroadcaster,
  InMemoryLocationStore,
  LocationStore,
  StreamConfig
}

import java.time.Clock
import javax.inject.Singleton

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
}
