package skatemap.api

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.FakeRequest
import play.api.test.Helpers.stubControllerComponents
import skatemap.core.{Broadcaster, EventStreamService, LocationStore, StreamConfig}
import skatemap.domain.Location

class StreamControllerSpec extends AnyWordSpec with Matchers {

  private class MockLocationStore extends LocationStore {
    def put(eventId: String, location: Location): Unit = ()
    def getAll(eventId: String): Map[String, Location] = Map.empty
    def cleanup(): Unit                                = ()
    def cleanupAll(): Unit                             = ()
  }

  private class MockBroadcaster extends Broadcaster {
    def publish(eventId: String, location: Location): Unit    = ()
    def subscribe(eventId: String): Source[Location, NotUsed] = Source.empty
  }

  private class MockEventStreamService
      extends EventStreamService(new MockLocationStore(), new MockBroadcaster(), StreamConfig.default) {
    override def createEventStream(eventId: String): Source[String, NotUsed] =
      Source.single("test-data")
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

      val event1 = "event-1"
      val event2 = "event-2"

      val webSocket1 = controller.streamEvent(event1)
      val webSocket2 = controller.streamEvent(event2)

      webSocket1.getClass.getName should include("WebSocket")
      webSocket2.getClass.getName should include("WebSocket")
    }
  }
}
