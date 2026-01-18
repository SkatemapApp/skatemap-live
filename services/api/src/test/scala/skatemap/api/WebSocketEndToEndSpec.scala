package skatemap.api

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.testkit.scaladsl.TestSink
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice._
import play.api.libs.json.{JsValue, Json}
import skatemap.core.{Broadcaster, EventStreamService, LocationStore}
import skatemap.domain.Location

class WebSocketEndToEndSpec extends PlaySpec with GuiceOneAppPerSuite with ScalaFutures {

  implicit lazy val system: ActorSystem        = app.actorSystem
  implicit lazy val materializer: Materializer = app.materializer

  "Complete WebSocket pipeline" should {

    "flow: store.put() → broadcaster.publish() → eventStreamService → stream" in {
      val store              = app.injector.instanceOf[LocationStore]
      val broadcaster        = app.injector.instanceOf[Broadcaster]
      val eventStreamService = app.injector.instanceOf[EventStreamService]
      val eventId            = "pipeline-event-1"
      val location1          = Location("skater-pipeline-1", -0.1278, 51.5074, 1000L)
      val location2          = Location("skater-pipeline-2", -1.1278, 52.5074, 2000L)

      store.put(eventId, location1)

      val testSink = eventStreamService
        .createEventStream(eventId)
        .runWith(TestSink.probe[String])

      testSink.request(2)

      val initialMessage = testSink.expectNext()
      initialMessage must include("skater-pipeline-1")
      initialMessage must include("51.5074")
      initialMessage must include("-0.1278")

      store.put(eventId, location2)
      broadcaster.publish(eventId, location2).futureValue

      val updateMessage = testSink.expectNext()
      updateMessage must include("skater-pipeline-2")
      updateMessage must include("52.5074")
      updateMessage must include("-1.1278")

      testSink.cancel()
    }

    "verify LocationBatch JSON serialisation format" in {
      val store              = app.injector.instanceOf[LocationStore]
      val eventStreamService = app.injector.instanceOf[EventStreamService]
      val eventId            = "json-event-2"
      val location           = Location("json-test-skater", -2.2426, 53.4808, 3000L)
      store.put(eventId, location)

      val testSink = eventStreamService
        .createEventStream(eventId)
        .runWith(TestSink.probe[String])

      testSink.request(1)
      val message = testSink.expectNext()
      val json    = Json.parse(message)

      (json \ "locations").isDefined mustBe true
      val locations = (json \ "locations").as[List[JsValue]]
      locations must have size 1

      locations.headOption match {
        case Some(locationJson) =>
          (locationJson \ "skaterId").as[String] mustBe "json-test-skater"
          (locationJson \ "latitude").as[Double] mustBe 53.4808
          (locationJson \ "longitude").as[Double] mustBe -2.2426
        case None => fail("Expected at least one location")
      }

      (json \ "serverTime").isDefined mustBe true
      (json \ "serverTime").as[Long] must be > 0L

      testSink.cancel()
    }

    "handle batching of multiple location updates" in {
      val store              = app.injector.instanceOf[LocationStore]
      val eventStreamService = app.injector.instanceOf[EventStreamService]
      val eventId            = "batch-event-3"

      val timestamp = 4000L
      val locations = List(
        Location("batch-skater-1", -1.0, 50.0, timestamp),
        Location("batch-skater-2", -2.0, 51.0, timestamp),
        Location("batch-skater-3", -3.0, 52.0, timestamp)
      )

      locations.foreach(store.put(eventId, _))

      val testSink = eventStreamService
        .createEventStream(eventId)
        .runWith(TestSink.probe[String])

      testSink.request(1)
      val message           = testSink.expectNext()
      val json              = Json.parse(message)
      val receivedLocations = (json \ "locations").as[List[JsValue]]

      receivedLocations must have size 3
      receivedLocations.map(loc => (loc \ "skaterId").as[String]) must contain allOf (
        "batch-skater-1",
        "batch-skater-2",
        "batch-skater-3"
      )

      testSink.cancel()
    }

    "maintain event isolation in complete pipeline" in {
      val store              = app.injector.instanceOf[LocationStore]
      val broadcaster        = app.injector.instanceOf[Broadcaster]
      val eventStreamService = app.injector.instanceOf[EventStreamService]
      val eventId1           = "isolation-event-1-4"
      val eventId2           = "isolation-event-2-4"

      val timestamp = 5000L
      store.put(eventId1, Location("skater-iso-1", -1.0, 50.0, timestamp))
      store.put(eventId2, Location("skater-iso-2", -2.0, 51.0, timestamp))

      val testSink1 = eventStreamService
        .createEventStream(eventId1)
        .runWith(TestSink.probe[String])
      val testSink2 = eventStreamService
        .createEventStream(eventId2)
        .runWith(TestSink.probe[String])

      testSink1.request(2)
      testSink2.request(2)

      val message1 = testSink1.expectNext()
      val message2 = testSink2.expectNext()
      message1 must include("skater-iso-1")
      message1 must not include "skater-iso-2"
      message2 must include("skater-iso-2")
      message2 must not include "skater-iso-1"

      broadcaster.publish(eventId1, Location("skater-iso-update-1", -5.0, 55.0, timestamp)).futureValue
      broadcaster.publish(eventId2, Location("skater-iso-update-2", -6.0, 56.0, timestamp)).futureValue

      val update1 = testSink1.expectNext()
      val update2 = testSink2.expectNext()

      update1 must include("skater-iso-update-1")
      update1 must not include "skater-iso-update-2"
      update2 must include("skater-iso-update-2")
      update2 must not include "skater-iso-update-1"

      testSink1.cancel()
      testSink2.cancel()
    }

  }
}
