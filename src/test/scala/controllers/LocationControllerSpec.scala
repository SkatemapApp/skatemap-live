package controllers

import java.util.UUID

import org.apache.pekko.stream.Materializer
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.libs.json.Json
import play.api.test.Helpers.{contentAsJson, status}
import services.LocationValidationService

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.test.Helpers._


class LocationControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  implicit lazy val materializer: Materializer = app.materializer
  
  private def createController() = new LocationController(stubControllerComponents(), new LocationValidationService())

  "LocationController" should {
    "update skater location with valid coordinates" in {
      val skatingEventId = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString
      val controller = createController()
      val result = controller.updateLocation(skatingEventId, skaterId).apply(
        FakeRequest(PUT, s"/skatingEvents/$skatingEventId/skaters/$skaterId")
          .withHeaders("Content-Type" -> "application/json")
          .withJsonBody(Json.parse("""{"coordinates": [0.0, 50.0]}"""))
      )

      status(result) mustBe OK
    }

    "update skater location from the router" in {
      val skatingEventId = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString
      val request = FakeRequest(PUT, s"/skatingEvents/$skatingEventId/skaters/$skaterId")
        .withHeaders("Content-Type" -> "application/json")
        .withJsonBody(Json.parse("""{"coordinates": [0.0, 50.0]}"""))
      val result = route(app, request).fold(fail("Route not found"))(identity)

      status(result) mustBe OK
    }

    "reject invalid skating event ID" in {
      val controller = createController()
      val result = controller.updateLocation("invalid-uuid", UUID.randomUUID().toString).apply(
        FakeRequest(PUT, "/skatingEvents/invalid-uuid/skaters/123")
          .withHeaders("Content-Type" -> "application/json")
          .withJsonBody(Json.parse("""{"coordinates": [0.0, 50.0]}"""))
      )

      status(result) mustBe BAD_REQUEST
      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "INVALID_SKATING_EVENT_ID"
    }

    "reject invalid skater ID" in {
      val controller = createController()
      val validEventId = UUID.randomUUID().toString
      val result = controller.updateLocation(validEventId, "invalid-uuid").apply(
        FakeRequest(PUT, s"/skatingEvents/$validEventId/skaters/invalid-uuid")
          .withHeaders("Content-Type" -> "application/json")
          .withJsonBody(Json.parse("""{"coordinates": [0.0, 50.0]}"""))
      )

      status(result) mustBe BAD_REQUEST
      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "INVALID_SKATER_ID"
    }

    "reject invalid longitude" in {
      val skatingEventId = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString
      val controller = createController()
      val result = controller.updateLocation(skatingEventId, skaterId).apply(
        FakeRequest(PUT, s"/skatingEvents/$skatingEventId/skaters/$skaterId")
          .withHeaders("Content-Type" -> "application/json")
          .withJsonBody(Json.parse("""{"coordinates": [181.0, 50.0]}"""))
      )

      status(result) mustBe BAD_REQUEST
    }

    "reject invalid latitude" in {
      val skatingEventId = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString
      val controller = createController()
      val result = controller.updateLocation(skatingEventId, skaterId).apply(
        FakeRequest(PUT, s"/skatingEvents/$skatingEventId/skaters/$skaterId")
          .withHeaders("Content-Type" -> "application/json")
          .withJsonBody(Json.parse("""{"coordinates": [0.0, 91.0]}"""))
      )

      status(result) mustBe BAD_REQUEST
    }

    "reject missing coordinates field" in {
      val skatingEventId = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString
      val controller = createController()
      val result = controller.updateLocation(skatingEventId, skaterId).apply(
        FakeRequest(PUT, s"/skatingEvents/$skatingEventId/skaters/$skaterId")
          .withHeaders("Content-Type" -> "application/json")
          .withJsonBody(Json.parse("""{"location": [0.0, 50.0]}"""))
      )

      status(result) mustBe BAD_REQUEST
    }

    "reject coordinates array with wrong length" in {
      val skatingEventId = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString
      val controller = createController()
      val result = controller.updateLocation(skatingEventId, skaterId).apply(
        FakeRequest(PUT, s"/skatingEvents/$skatingEventId/skaters/$skaterId")
          .withHeaders("Content-Type" -> "application/json")
          .withJsonBody(Json.parse("""{"coordinates": [0.0]}"""))
      )

      status(result) mustBe BAD_REQUEST
    }

    "reject non-JSON request body" in {
      val skatingEventId = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString
      val controller = createController()
      val result = controller.updateLocation(skatingEventId, skaterId).apply(
        FakeRequest(PUT, s"/skatingEvents/$skatingEventId/skaters/$skaterId")
          .withHeaders("Content-Type" -> "text/plain")
          .withTextBody("not json")
      )

      // Play returns 415 Unsupported Media Type for non-JSON when expecting JSON
      status(result) must (be(BAD_REQUEST) or be(415))
    }

    "accept valid longitude and latitude boundaries" in {
      val skatingEventId = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString
      val controller = createController()
      
      // Test valid boundary values
      val validCases = List(
        """{"coordinates": [-180.0, -90.0]}""", // min values
        """{"coordinates": [180.0, 90.0]}""",   // max values
        """{"coordinates": [0.0, 0.0]}"""       // zero values
      )
      
      validCases.foreach { coordinates =>
        val result = controller.updateLocation(skatingEventId, skaterId).apply(
          FakeRequest(PUT, s"/skatingEvents/$skatingEventId/skaters/$skaterId")
            .withHeaders("Content-Type" -> "application/json")
            .withJsonBody(Json.parse(coordinates))
        )
        status(result) mustBe OK
      }
    }
  }
}