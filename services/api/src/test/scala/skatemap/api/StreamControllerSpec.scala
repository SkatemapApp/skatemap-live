package skatemap.api

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.scalatest.exceptions.TestFailedException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.FakeRequest
import play.api.test.Helpers.stubControllerComponents
import skatemap.core.{EventStreamService, InMemoryLocationStore, LocationConfig, StreamConfig}
import skatemap.test.{LogCapture, ScalaFuturesSpec, StubBroadcaster, TestClock}

import scala.concurrent.duration._

class StreamControllerSpec extends AnyWordSpec with Matchers with ScalaFuturesSpec {

  private class MockEventStreamService
      extends EventStreamService(
        new InMemoryLocationStore(TestClock.fixed(1234567890123L), LocationConfig(1.hour)),
        new StubBroadcaster(),
        StreamConfig(100, 500.millis),
        TestClock.fixed(1234567890123L)
      ) {
    override def createEventStream(eventId: String): Source[String, NotUsed] =
      Source.single("test-data")
  }

  private class FailingEventStreamService
      extends EventStreamService(
        new InMemoryLocationStore(TestClock.fixed(1234567890123L), LocationConfig(1.hour)),
        new StubBroadcaster(),
        StreamConfig(100, 500.millis),
        TestClock.fixed(1234567890123L)
      ) {
    override def createEventStream(eventId: String): Source[String, NotUsed] =
      Source.failed(new RuntimeException("Stream processing error"))
  }

  "StreamController" should {

    "create WebSocket for given event ID" in {
      val eventId    = "550e8400-e29b-41d4-a716-446655440000"
      val service    = new MockEventStreamService()
      val controller = new StreamController(stubControllerComponents(), service)

      val webSocket = controller.streamEvent(eventId)
      webSocket.getClass.getName should include("WebSocket")
    }

    "execute WebSocket callback to create Flow" in {
      val eventId    = "550e8400-e29b-41d4-a716-446655440000"
      val service    = new MockEventStreamService()
      val controller = new StreamController(stubControllerComponents(), service)

      val webSocket = controller.streamEvent(eventId)

      noException should be thrownBy webSocket.apply(FakeRequest())
    }

    "handle different event IDs" in {
      val service    = new MockEventStreamService()
      val controller = new StreamController(stubControllerComponents(), service)

      val event1 = "550e8400-e29b-41d4-a716-446655440000"
      val event2 = "660e8400-e29b-41d4-a716-446655440001"

      val webSocket1 = controller.streamEvent(event1)
      val webSocket2 = controller.streamEvent(event2)

      webSocket1.getClass.getName should include("WebSocket")
      webSocket2.getClass.getName should include("WebSocket")
    }

    "log WebSocket connection establishment when callback is invoked" in {
      val eventId    = "550e8400-e29b-41d4-a716-446655440000"
      val service    = new MockEventStreamService()
      val controller = new StreamController(stubControllerComponents(), service)

      val result = LogCapture.withCapture("skatemap.api.StreamController") { capture =>
        val webSocket = controller.streamEvent(eventId)
        val flow      = webSocket.apply(FakeRequest())

        capture.hasMessageContaining("WebSocket connection established") should be(true)
        capture.hasMessageContaining(eventId) should be(true)
        flow
      }
      result should be(defined)
    }

    "log stream errors when createErrorHandledStream fails" in {
      val eventId    = "550e8400-e29b-41d4-a716-446655440000"
      val service    = new FailingEventStreamService()
      val controller = new StreamController(stubControllerComponents(), service)

      LogCapture.withCapture("skatemap.api.StreamController") { capture =>
        implicit val system: ActorSystem = ActorSystem("test")
        implicit val mat: Materializer   = Materializer(system)

        try {
          val caught = intercept[TestFailedException] {
            controller.createErrorHandledStream(eventId).runWith(Sink.ignore).futureValue
          }
          caught.cause.fold(fail("Expected TestFailedException with a cause"))(cause =>
            cause shouldBe a[RuntimeException]
          )

          capture.hasMessageContaining("Stream error") should be(true)
        } finally
          system.terminate()
      }
    }

    "reject invalid event ID with validation error" in {
      val invalidEventId = "not-a-uuid"
      val service        = new MockEventStreamService()
      val controller     = new StreamController(stubControllerComponents(), service)

      val webSocket = controller.streamEvent(invalidEventId)
      whenReady(webSocket.apply(FakeRequest())) { result =>
        result.isLeft should be(true)
        val errorResult = result.left.toOption.fold(fail("Expected Left but got Right"))(identity)

        errorResult.header.status should be(400)
      }
    }

    "accept valid UUID event ID" in {
      val validEventId = "550e8400-e29b-41d4-a716-446655440000"
      val service      = new MockEventStreamService()
      val controller   = new StreamController(stubControllerComponents(), service)

      val webSocket = controller.streamEvent(validEventId)
      whenReady(webSocket.apply(FakeRequest())) { result =>
        result.isRight should be(true)
      }
    }
  }
}
