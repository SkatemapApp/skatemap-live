package controllers

import org.scalatestplus.play.PlaySpec
import play.api.http.Status.OK
import play.api.test.Helpers.status

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.test.Helpers._


class LocationControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  "The controller" should {
    "process the location update successfully" in {
      val controller = new LocationController(stubControllerComponents())
      val result = controller.update().apply(FakeRequest(POST, "/locations"))

      status(result) mustBe OK
    }
  }
}