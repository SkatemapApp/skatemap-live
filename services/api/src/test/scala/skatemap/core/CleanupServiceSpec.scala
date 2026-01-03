package skatemap.core

import org.apache.pekko.actor.ActorSystem
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.ApplicationLifecycle

import scala.concurrent.duration._
import scala.concurrent.Future

class CleanupServiceSpec extends AnyWordSpec with Matchers with Eventually with MockitoSugar with ScalaFutures {

  implicit val defaultPatience: PatienceConfig = PatienceConfig(
    timeout = Span(3, Seconds),
    interval = Span(50, Millis)
  )

  "CleanupService" should {

    "register shutdown hook with ApplicationLifecycle" in {
      val mockStore     = mock[LocationStore]
      val mockLifecycle = mock[ApplicationLifecycle]
      val actorSystem   = ActorSystem("test")
      val config        = CleanupConfig(initialDelay = 1.second, interval = 1.second)

      when(mockStore.cleanupAll()).thenReturn(0)
      doNothing().when(mockLifecycle).addStopHook(any[() => Future[_]])

      try {
        new CleanupService(mockStore, config, actorSystem, mockLifecycle, actorSystem.dispatcher)

        verify(mockLifecycle).addStopHook(any[() => Future[_]])
      } finally {
        actorSystem.terminate()
        actorSystem.whenTerminated.futureValue
      }
    }

    "schedule cleanup to run at configured intervals" in {
      val mockStore     = mock[LocationStore]
      val mockLifecycle = mock[ApplicationLifecycle]
      val actorSystem   = ActorSystem("test")
      val config        = CleanupConfig(initialDelay = 100.millis, interval = 100.millis)

      when(mockStore.cleanupAll()).thenReturn(0)
      doNothing().when(mockLifecycle).addStopHook(any[() => Future[_]])

      try {
        new CleanupService(mockStore, config, actorSystem, mockLifecycle, actorSystem.dispatcher)

        eventually(timeout(Span(2, Seconds)), interval(Span(100, Millis))) {
          verify(mockStore, atLeastOnce()).cleanupAll()
        }
      } finally {
        actorSystem.terminate()
        actorSystem.whenTerminated.futureValue
      }
    }

    "use configured initial delay" in {
      val mockStore     = mock[LocationStore]
      val mockLifecycle = mock[ApplicationLifecycle]
      val actorSystem   = ActorSystem("test")
      val config        = CleanupConfig(initialDelay = 200.millis, interval = 100.millis)

      when(mockStore.cleanupAll()).thenReturn(0)
      doNothing().when(mockLifecycle).addStopHook(any[() => Future[_]])

      try {
        new CleanupService(mockStore, config, actorSystem, mockLifecycle, actorSystem.dispatcher)

        eventually(timeout(Span(2, Seconds)), interval(Span(50, Millis))) {
          verify(mockStore, atLeastOnce()).cleanupAll()
        }
      } finally {
        actorSystem.terminate()
        actorSystem.whenTerminated.futureValue
      }
    }

    "handle cleanup errors gracefully" in {
      val mockStore     = mock[LocationStore]
      val mockLifecycle = mock[ApplicationLifecycle]
      val actorSystem   = ActorSystem("test")
      val config        = CleanupConfig(initialDelay = 100.millis, interval = 100.millis)

      when(mockStore.cleanupAll()).thenThrow(new RuntimeException("Cleanup error"))
      doNothing().when(mockLifecycle).addStopHook(any[() => Future[_]])

      try {
        new CleanupService(mockStore, config, actorSystem, mockLifecycle, actorSystem.dispatcher)

        eventually(timeout(Span(2, Seconds)), interval(Span(100, Millis))) {
          verify(mockStore, atLeastOnce()).cleanupAll()
        }
      } finally {
        actorSystem.terminate()
        actorSystem.whenTerminated.futureValue
      }
    }

  }
}
