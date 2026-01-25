package skatemap.api

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.testkit.scaladsl.TestSink
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice._
import play.api.libs.json.Json
import skatemap.core.{Broadcaster, EventStreamService, LocationStore}
import skatemap.domain.Location

import scala.concurrent.duration._

class StreamControllerIntegrationSpec extends PlaySpec with GuiceOneAppPerSuite with ScalaFutures {

  implicit lazy val system: ActorSystem        = app.actorSystem
  implicit lazy val materializer: Materializer = app.materializer

  "WebSocket streaming integration" should {

    "establish connection and receive initial data from store" in {
      val store              = app.injector.instanceOf[LocationStore]
      val eventStreamService = app.injector.instanceOf[EventStreamService]
      val eventId            = "test-event-1"
      val location           = Location("skater-1", -0.1278, 51.5074, 1000L)
      store.put(eventId, location)

      val testSink = eventStreamService
        .createEventStream(eventId)
        .runWith(TestSink.probe[String])

      testSink.request(1)
      val message = testSink.expectNext(3.seconds)
      message must include("skater-1")
      message must include("51.5074")
      message must include("-0.1278")

      testSink.cancel()
    }

    "receive real-time updates through broadcaster" in {
      val store              = app.injector.instanceOf[LocationStore]
      val broadcaster        = app.injector.instanceOf[Broadcaster]
      val eventStreamService = app.injector.instanceOf[EventStreamService]
      val eventId            = "live-event-2"

      val testSink = eventStreamService
        .createEventStream(eventId)
        .runWith(TestSink.probe[String])

      testSink.request(1)
      testSink.expectNoMessage(100.millis)

      val newLocation = Location("skater-2", -1.1278, 52.5074, 2000L)
      store.put(eventId, newLocation)
      broadcaster.publish(eventId, newLocation).futureValue

      val updateMessage = testSink.expectNext(2.seconds)
      updateMessage must include("skater-2")
      updateMessage must include("52.5074")
      updateMessage must include("-1.1278")

      testSink.cancel()
    }

    "handle multiple concurrent connections to same event" in {
      val store              = app.injector.instanceOf[LocationStore]
      val broadcaster        = app.injector.instanceOf[Broadcaster]
      val eventStreamService = app.injector.instanceOf[EventStreamService]
      val eventId            = "multi-event-3"
      val location           = Location("skater-3", -2.2426, 53.4808, 3000L)
      store.put(eventId, location)

      val testSink1 = eventStreamService
        .createEventStream(eventId)
        .runWith(TestSink.probe[String])
      val testSink2 = eventStreamService
        .createEventStream(eventId)
        .runWith(TestSink.probe[String])

      testSink1.request(2)
      testSink2.request(2)

      val message1 = testSink1.expectNext(3.seconds)
      val message2 = testSink2.expectNext(3.seconds)
      message1 must include("skater-3")
      message2 must include("skater-3")

      val newLocation = Location("skater-4", -1.6178, 54.9783, 4000L)
      store.put(eventId, newLocation)
      broadcaster.publish(eventId, newLocation).futureValue

      val update1 = testSink1.expectNext(2.seconds)
      val update2 = testSink2.expectNext(2.seconds)

      update1 must include("skater-4")
      update2 must include("skater-4")

      testSink1.cancel()
      testSink2.cancel()
    }

    "isolate events between different event IDs" in {
      val store              = app.injector.instanceOf[LocationStore]
      val eventStreamService = app.injector.instanceOf[EventStreamService]
      val eventIdA           = "event-a-4"
      val eventIdB           = "event-b-4"

      store.put(eventIdA, Location("skater-a", -1.0, 50.0, 5000L))
      store.put(eventIdB, Location("skater-b", -2.0, 51.0, 6000L))

      val testSinkA = eventStreamService
        .createEventStream(eventIdA)
        .runWith(TestSink.probe[String])
      val testSinkB = eventStreamService
        .createEventStream(eventIdB)
        .runWith(TestSink.probe[String])

      testSinkA.request(1)
      testSinkB.request(1)

      val messageA = testSinkA.expectNext(3.seconds)
      val messageB = testSinkB.expectNext(3.seconds)
      messageA must include("skater-a")
      messageA must not include "skater-b"
      messageB must include("skater-b")
      messageB must not include "skater-a"

      testSinkA.cancel()
      testSinkB.cancel()
    }

  }

  "EventStreamService streaming" should {

    "handle empty events gracefully" in {
      val eventStreamService = app.injector.instanceOf[EventStreamService]
      val eventId            = "empty-event-5"

      val testSink = eventStreamService
        .createEventStream(eventId)
        .runWith(TestSink.probe[String])

      testSink.request(1)
      testSink.expectNoMessage(500.millis)
      testSink.cancel()
    }

    "handle stream cancellation properly" in {
      val store              = app.injector.instanceOf[LocationStore]
      val eventStreamService = app.injector.instanceOf[EventStreamService]
      val eventId            = "disconnect-test-6"
      store.put(eventId, Location("skater-disconnect", -1.0, 50.0, 7000L))

      val testSink = eventStreamService
        .createEventStream(eventId)
        .runWith(TestSink.probe[String])

      testSink.request(1)
      testSink.expectNext(3.seconds)
      testSink.cancel()
    }

  }

  "Error handling and resilience" should {

    "handle EventStreamService failures gracefully" in {
      val eventStreamService = app.injector.instanceOf[EventStreamService]
      val eventId            = "error-test-7"

      val testSink = eventStreamService
        .createEventStream(eventId)
        .runWith(TestSink.probe[String])

      testSink.request(1)

      testSink.expectNoMessage(500.millis)
      testSink.cancel()
    }

    "handle high-frequency updates without overwhelming" in {
      val store              = app.injector.instanceOf[LocationStore]
      val eventStreamService = app.injector.instanceOf[EventStreamService]
      val eventId            = "backpressure-test-8"

      val locations = (1 to 10).map { i =>
        Location(s"rapid-skater-${i.toString}", i.toDouble, (i + 1).toDouble, 8000L + i)
      }
      locations.foreach(store.put(eventId, _))

      val testSink = eventStreamService
        .createEventStream(eventId)
        .runWith(TestSink.probe[String])

      testSink.request(1)

      val batchedMessage = testSink.expectNext(2.seconds)
      batchedMessage must include("rapid-skater")

      batchedMessage must include("rapid-skater-1")
      batchedMessage must include("rapid-skater-10")

      val json = Json.parse(batchedMessage)
      (json \ "serverTime").isDefined mustBe true
      (json \ "serverTime").as[Long] must be > 0L

      testSink.cancel()
    }

  }
}
