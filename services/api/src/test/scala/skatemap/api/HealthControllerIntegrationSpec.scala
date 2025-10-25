package skatemap.api

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice._
import play.api.http.Status.OK
import play.api.test.Helpers._
import play.api.test._

class HealthControllerIntegrationSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  "Health check endpoint" should {

    "return OK status" in {
      val request = FakeRequest(GET, "/health")

      val result = route(app, request).fold(fail("Route not found"))(identity)

      status(result) mustBe OK
    }

    "return empty response body" in {
      val request = FakeRequest(GET, "/health")

      val result = route(app, request).fold(fail("Route not found"))(identity)

      contentAsString(result) mustBe ""
    }
  }
}
