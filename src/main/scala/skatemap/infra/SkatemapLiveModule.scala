package skatemap.infra

import com.google.inject.{AbstractModule, Provides}
import skatemap.core.{Broadcaster, InMemoryBroadcaster, InMemoryLocationStore, LocationStore}

import java.time.Clock
import javax.inject.Singleton

class SkatemapLiveModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[LocationStore]).to(classOf[InMemoryLocationStore])
    bind(classOf[Broadcaster]).to(classOf[InMemoryBroadcaster])
  }

  @Provides
  @Singleton
  def provideClock(): Clock = Clock.systemUTC()
}
