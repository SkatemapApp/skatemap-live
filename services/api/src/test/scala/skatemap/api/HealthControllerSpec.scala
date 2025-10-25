package skatemap.api

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice._
import play.api.http.Status.OK
import play.api.test.Helpers._
import play.api.test._

class HealthControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  private def createController() = new skatemap.api.HealthController(
    stubControllerComponents()
  )

  "HealthController.health" should {

    "return OK status" in {
      val controller = createController()
      val request    = FakeRequest(GET, "/health")

      val result = controller.health.apply(request)

      status(result) mustBe OK
    }

    "return empty response body" in {
      val controller = createController()
      val request    = FakeRequest(GET, "/health")

      val result = controller.health.apply(request)

      contentAsString(result) mustBe ""
    }

    "handle requests through routing" in {
      val request = FakeRequest(GET, "/health")

      val result = route(app, request).fold(fail("Route not found"))(identity)

      status(result) mustBe OK
    }
  }
}
