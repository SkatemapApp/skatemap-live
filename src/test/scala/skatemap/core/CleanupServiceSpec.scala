package skatemap.core

import org.apache.pekko.actor.ActorSystem
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Second, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.ApplicationLifecycle

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class CleanupServiceSpec extends AnyWordSpec with Matchers with Eventually with MockitoSugar {

  "CleanupService" should {

    "register shutdown hook with ApplicationLifecycle" in {
      val mockStore     = mock[LocationStore]
      val mockLifecycle = mock[ApplicationLifecycle]
      val actorSystem   = ActorSystem("test")

      doNothing().when(mockLifecycle).addStopHook(any[() => Future[_]])

      try {
        new CleanupService(mockStore, actorSystem, mockLifecycle, actorSystem.dispatcher)

        verify(mockLifecycle).addStopHook(any[() => Future[_]])
      } finally {
        actorSystem.terminate()
        Await.result(actorSystem.whenTerminated, 5.seconds)
      }
    }

    "schedule cleanup to run every 10 seconds" in {
      val mockStore     = mock[LocationStore]
      val mockLifecycle = mock[ApplicationLifecycle]
      val actorSystem   = ActorSystem("test")

      doNothing().when(mockLifecycle).addStopHook(any[() => Future[_]])

      try {
        new CleanupService(mockStore, actorSystem, mockLifecycle, actorSystem.dispatcher)

        eventually(timeout(Span(12, Seconds)), interval(Span(1, Second))) {
          verify(mockStore, atLeastOnce()).cleanupAll()
        }
      } finally {
        actorSystem.terminate()
        Await.result(actorSystem.whenTerminated, 5.seconds)
      }
    }

  }
}
