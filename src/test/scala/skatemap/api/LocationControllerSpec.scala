package skatemap.api

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice._
import play.api.http.Status.{ACCEPTED, BAD_REQUEST}
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test._
import skatemap.core.{Broadcaster, LocationStore}
import skatemap.domain.Location
import skatemap.test.LogCapture

import java.util.concurrent.ConcurrentHashMap

class LocationControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  implicit lazy val materializer: Materializer = app.materializer

  private val validSkatingEventId = "550e8400-e29b-41d4-a716-446655440000"
  private val validSkaterId       = "550e8400-e29b-41d4-a716-446655440001"

  private class MockLocationStore extends LocationStore {
    private val storage = new ConcurrentHashMap[String, Map[String, Location]]()

    def put(eventId: String, location: Location): Unit = {
      val eventMap = Option(storage.get(eventId)).getOrElse(Map.empty)
      storage.put(eventId, eventMap + (location.skaterId -> location))
    }

    def getAll(eventId: String): Map[String, Location] =
      Option(storage.get(eventId)).getOrElse(Map.empty)

    def cleanup(): Unit = storage.clear()

    def cleanupAll(): Unit = storage.clear()
  }

  private class MockBroadcaster extends Broadcaster {
    def publish(eventId: String, location: Location): Unit    = ()
    def subscribe(eventId: String): Source[Location, NotUsed] = Source.empty
  }

  private def createController() = new skatemap.api.LocationController(
    stubControllerComponents(),
    new MockLocationStore(),
    new MockBroadcaster()
  )

  "LocationController.updateLocation" should {

    "accept valid location update request" in {
      val controller = createController()
      val request = FakeRequest(PUT, s"/skatingEvents/$validSkatingEventId/skaters/$validSkaterId")
        .withHeaders("Content-Type" -> "application/json")
        .withJsonBody(Json.parse("""{"coordinates": [0.0, 50.0]}"""))

      val result = controller.updateLocation(validSkatingEventId, validSkaterId).apply(request)

      status(result) mustBe ACCEPTED
    }

    "handle requests through routing" in {
      val request = FakeRequest(PUT, s"/skatingEvents/$validSkatingEventId/skaters/$validSkaterId")
        .withHeaders("Content-Type" -> "application/json")
        .withJsonBody(Json.parse("""{"coordinates": [0.0, 50.0]}"""))

      val result = route(app, request).fold(fail("Route not found"))(identity)

      status(result) mustBe ACCEPTED
    }

    "return BAD_REQUEST for validation errors" in {
      val controller = createController()
      val invalidCases = List(
        ("invalid-uuid", validSkaterId, """{"coordinates": [0.0, 50.0]}"""),
        (validSkatingEventId, "invalid-uuid", """{"coordinates": [0.0, 50.0]}"""),
        (validSkatingEventId, validSkaterId, """{"coordinates": [181.0, 50.0]}"""),
        (validSkatingEventId, validSkaterId, """{"coordinates": [0.0, 91.0]}"""),
        (validSkatingEventId, validSkaterId, """{"location": [0.0, 50.0]}"""),
        (validSkatingEventId, validSkaterId, """{"coordinates": [0.0]}""")
      )

      invalidCases.foreach { case (eventId, skaterId, body) =>
        val request = FakeRequest(PUT, s"/skatingEvents/$eventId/skaters/$skaterId")
          .withHeaders("Content-Type" -> "application/json")
          .withJsonBody(Json.parse(body))

        val result = controller.updateLocation(eventId, skaterId).apply(request)
        status(result) mustBe BAD_REQUEST
      }
    }

    "return proper content-type for error responses" in {
      val controller = createController()
      val request = FakeRequest(PUT, s"/skatingEvents/invalid-uuid/skaters/$validSkaterId")
        .withHeaders("Content-Type" -> "application/json")
        .withJsonBody(Json.parse("""{"coordinates": [0.0, 50.0]}"""))

      val result = controller.updateLocation("invalid-uuid", validSkaterId).apply(request)

      status(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("application/json")
    }

    "handle controller internal failures gracefully" in {
      val controller = createController()
      val request = FakeRequest(PUT, s"/skatingEvents/$validSkatingEventId/skaters/$validSkaterId")
        .withHeaders("Content-Type" -> "application/json")
        .withTextBody("malformed json")

      val result = controller.updateLocation(validSkatingEventId, validSkaterId).apply(request)

      status(result) mustBe BAD_REQUEST
    }

    "log incoming requests with eventId and skaterId" in {
      val result = LogCapture.withCapture("skatemap.api.LocationController") { capture =>
        val controller = createController()
        val request = FakeRequest(PUT, s"/skatingEvents/$validSkatingEventId/skaters/$validSkaterId")
          .withHeaders("Content-Type" -> "application/json")
          .withJsonBody(Json.parse("""{"coordinates": [0.0, 50.0]}"""))

        controller.updateLocation(validSkatingEventId, validSkaterId).apply(request)

        capture.hasMessageContaining("Received location update request") mustBe true
        capture.hasMessageContaining(validSkatingEventId) mustBe true
        capture.hasMessageContaining(validSkaterId) mustBe true
      }
      result mustBe defined
    }

    "log validation errors with error details" in {
      val result = LogCapture.withCapture("skatemap.api.LocationController") { capture =>
        val controller = createController()
        val request = FakeRequest(PUT, s"/skatingEvents/invalid-uuid/skaters/$validSkaterId")
          .withHeaders("Content-Type" -> "application/json")
          .withJsonBody(Json.parse("""{"coordinates": [0.0, 50.0]}"""))

        controller.updateLocation("invalid-uuid", validSkaterId).apply(request)

        capture.hasMessageContaining("Validation failed") mustBe true
        capture.hasMessageContaining("INVALID_SKATING_EVENT_ID") mustBe true
      }
      result mustBe defined
    }

    "set MDC context for request tracing" in {
      val result = LogCapture.withCapture("skatemap.api.LocationController") { capture =>
        val controller = createController()
        val request = FakeRequest(PUT, s"/skatingEvents/$validSkatingEventId/skaters/$validSkaterId")
          .withHeaders("Content-Type" -> "application/json")
          .withJsonBody(Json.parse("""{"coordinates": [0.0, 50.0]}"""))

        controller.updateLocation(validSkatingEventId, validSkaterId).apply(request)

        capture.getMdcValue("eventId") mustBe Some(validSkatingEventId)
        capture.getMdcValue("skaterId") mustBe Some(validSkaterId)
        capture.getMdcValue("action") mustBe Some("updateLocation")
      }
      result mustBe defined
    }

  }
}
