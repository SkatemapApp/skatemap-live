package skatemap.core

import org.apache.pekko.actor.ActorSystem
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.ApplicationLifecycle
import skatemap.test.LogCapture

import scala.concurrent.duration._
import scala.concurrent.Future

class BroadcasterCleanupServiceSpec
    extends AnyWordSpec
    with Matchers
    with Eventually
    with MockitoSugar
    with BeforeAndAfterAll
    with ScalaFutures {

  implicit val defaultPatience: PatienceConfig = PatienceConfig(
    timeout = Span(5, Seconds),
    interval = Span(50, Millis)
  )

  "BroadcasterCleanupService" should {

    "initialise with configured values" in {
      val mockBroadcaster = mock[InMemoryBroadcaster]
      val mockLifecycle   = mock[ApplicationLifecycle]
      val actorSystem     = ActorSystem("test")
      val config          = HubConfig(ttl = 300.seconds, cleanupInterval = 60.seconds, bufferSize = 128)

      when(mockBroadcaster.cleanupUnusedHubs(any[Long])).thenReturn(0)
      doNothing().when(mockLifecycle).addStopHook(any[() => Future[_]])

      try
        LogCapture.withCapture("skatemap.core.BroadcasterCleanupService") { capture =>
          new BroadcasterCleanupService(mockBroadcaster, config, actorSystem, mockLifecycle, actorSystem.dispatcher)

          capture.hasMessageContaining("BroadcasterCleanupService initialised") should be(true)
          capture.hasMessageContaining("ttl=300 seconds") should be(true)
          capture.hasMessageContaining("cleanupInterval=60 seconds") should be(true)
        }
      finally {
        actorSystem.terminate()
        actorSystem.whenTerminated.futureValue
      }
    }

    "schedule cleanup at configured interval and log when hubs are removed" in {
      val mockBroadcaster = mock[InMemoryBroadcaster]
      val mockLifecycle   = mock[ApplicationLifecycle]
      val actorSystem     = ActorSystem("test")
      val config          = HubConfig(ttl = 1.second, cleanupInterval = 100.millis, bufferSize = 128)

      when(mockBroadcaster.cleanupUnusedHubs(any[Long])).thenReturn(2)
      doNothing().when(mockLifecycle).addStopHook(any[() => Future[_]])

      try
        LogCapture.withCapture("skatemap.core.BroadcasterCleanupService") { capture =>
          new BroadcasterCleanupService(mockBroadcaster, config, actorSystem, mockLifecycle, actorSystem.dispatcher)

          eventually(timeout(Span(2, Seconds)), interval(Span(100, Millis))) {
            capture.hasMessageContaining("Hub cleanup completed") should be(true)
            capture.hasMessageContaining("removed 2 hubs") should be(true)
          }
        }
      finally {
        actorSystem.terminate()
        actorSystem.whenTerminated.futureValue
      }
    }

    "not log when no hubs are removed" in {
      val mockBroadcaster = mock[InMemoryBroadcaster]
      val mockLifecycle   = mock[ApplicationLifecycle]
      val actorSystem     = ActorSystem("test")
      val config          = HubConfig(ttl = 1.second, cleanupInterval = 100.millis, bufferSize = 128)

      when(mockBroadcaster.cleanupUnusedHubs(any[Long])).thenReturn(0)
      doNothing().when(mockLifecycle).addStopHook(any[() => Future[_]])

      try
        LogCapture.withCapture("skatemap.core.BroadcasterCleanupService") { capture =>
          new BroadcasterCleanupService(mockBroadcaster, config, actorSystem, mockLifecycle, actorSystem.dispatcher)

          eventually(timeout(Span(2, Seconds)), interval(Span(100, Millis))) {
            verify(mockBroadcaster, atLeastOnce()).cleanupUnusedHubs(any[Long])
            capture.hasMessageContaining("Hub cleanup completed") should be(false)
          }
        }
      finally {
        actorSystem.terminate()
        actorSystem.whenTerminated.futureValue
      }
    }

    "log cleanup errors" in {
      val mockBroadcaster = mock[InMemoryBroadcaster]
      val mockLifecycle   = mock[ApplicationLifecycle]
      val actorSystem     = ActorSystem("test")
      val config          = HubConfig(ttl = 1.second, cleanupInterval = 100.millis, bufferSize = 128)

      when(mockBroadcaster.cleanupUnusedHubs(any[Long])).thenThrow(new RuntimeException("Cleanup error"))
      doNothing().when(mockLifecycle).addStopHook(any[() => Future[_]])

      try
        LogCapture.withCapture("skatemap.core.BroadcasterCleanupService") { capture =>
          new BroadcasterCleanupService(mockBroadcaster, config, actorSystem, mockLifecycle, actorSystem.dispatcher)

          eventually(timeout(Span(2, Seconds)), interval(Span(100, Millis))) {
            capture.hasMessageContaining("Hub cleanup failed") should be(true)
            capture.hasMessageContaining("Cleanup error") should be(true)
          }
        }
      finally {
        actorSystem.terminate()
        actorSystem.whenTerminated.futureValue
      }
    }

    "cancel scheduled cleanup on application shutdown" in {
      val mockBroadcaster = mock[InMemoryBroadcaster]
      val mockLifecycle   = mock[ApplicationLifecycle]
      val actorSystem     = ActorSystem("test")
      val config          = HubConfig(ttl = 300.seconds, cleanupInterval = 60.seconds, bufferSize = 128)

      when(mockBroadcaster.cleanupUnusedHubs(any[Long])).thenReturn(0)
      doNothing().when(mockLifecycle).addStopHook(any[() => Future[_]])

      try {
        new BroadcasterCleanupService(mockBroadcaster, config, actorSystem, mockLifecycle, actorSystem.dispatcher)

        verify(mockLifecycle).addStopHook(any[() => Future[_]])
      } finally {
        actorSystem.terminate()
        actorSystem.whenTerminated.futureValue
      }
    }
  }
}
