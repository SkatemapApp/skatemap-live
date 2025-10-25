package skatemap.api

import org.apache.pekko.stream.Materializer
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice._
import play.api.http.Status.{ACCEPTED, BAD_REQUEST}
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test._

class LocationControllerIntegrationSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  implicit lazy val materializer: Materializer = app.materializer

  private val validSkatingEventId = "550e8400-e29b-41d4-a716-446655440000"
  private val validSkaterId       = "550e8400-e29b-41d4-a716-446655440001"

  "Location update end-to-end flow" should {

    "accept valid location update and return ACCEPTED" in {
      val request = FakeRequest(PUT, s"/skatingEvents/$validSkatingEventId/skaters/$validSkaterId")
        .withHeaders("Content-Type" -> "application/json")
        .withJsonBody(Json.parse("""{"coordinates": [0.0, 50.0]}"""))

      val result = route(app, request).fold(fail("Route not found"))(identity)

      status(result) mustBe ACCEPTED
    }

    "reject invalid JSON and return BAD_REQUEST" in {
      val request = FakeRequest(PUT, s"/skatingEvents/$validSkatingEventId/skaters/$validSkaterId")
        .withHeaders("Content-Type" -> "application/json")
        .withTextBody("""{"coordinates": [invalid]}""")

      val result = route(app, request).fold(fail("Route not found"))(identity)

      status(result) mustBe BAD_REQUEST
    }

    "reject malformed request body and return BAD_REQUEST" in {
      val request = FakeRequest(PUT, s"/skatingEvents/$validSkatingEventId/skaters/$validSkaterId")
        .withHeaders("Content-Type" -> "application/json")
        .withTextBody("""not valid json""")

      val result = route(app, request).fold(fail("Route not found"))(identity)

      status(result) mustBe BAD_REQUEST
    }

    "validate path parameters before processing request body" in {
      val request = FakeRequest(PUT, s"/skatingEvents/invalid-uuid/skaters/$validSkaterId")
        .withHeaders("Content-Type" -> "application/json")
        .withJsonBody(Json.parse("""{"coordinates": [0.0, 50.0]}"""))

      val result = route(app, request).fold(fail("Route not found"))(identity)

      status(result) mustBe BAD_REQUEST
    }

    "validate coordinate bounds" in {
      val invalidCoordinates = List(
        """{"coordinates": [181.0, 50.0]}""",
        """{"coordinates": [0.0, 91.0]}""",
        """{"coordinates": [-181.0, 50.0]}""",
        """{"coordinates": [0.0, -91.0]}"""
      )

      invalidCoordinates.foreach { coordJson =>
        val request = FakeRequest(PUT, s"/skatingEvents/$validSkatingEventId/skaters/$validSkaterId")
          .withHeaders("Content-Type" -> "application/json")
          .withJsonBody(Json.parse(coordJson))

        val result = route(app, request).fold(fail("Route not found"))(identity)
        status(result) mustBe BAD_REQUEST
      }
    }

  }
}
