package controllers

import java.util.UUID

import org.apache.pekko.stream.Materializer
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.OK
import play.api.test.Helpers.status

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.test.Helpers._


class LocationControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  implicit lazy val materializer: Materializer = app.materializer

  "LocationController" should {
    "update skater location from a new instance of controller" in {
      val skatingEventId = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString
      val controller = new LocationController(stubControllerComponents())
      val result = controller.updateLocation(skatingEventId, skaterId).apply(FakeRequest(PUT, s"/skatingEvents/$skatingEventId/skaters/$skaterId")
        .withBody(s"""{"latlng": [0.0, 50.0]}"""))

      status(result) mustBe OK
    }

    "update skater location from the router" in {
      val skatingEventId = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString
      val request = FakeRequest(PUT, s"/skatingEvents/$skatingEventId/skaters/$skaterId")
        .withBody(s"""{"latlng": [0.0, 50.0]}""")
      val result = route(app, request).fold(fail("Route not found"))(identity)

      status(result) mustBe OK
    }
  }
}