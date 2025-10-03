package skatemap.api

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice._
import play.api.test.Helpers._
import play.api.test._

class ErrorHandlerIntegrationSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  "Error handler" should {

    "return JSON error responses for 404 not found" in {
      val request = FakeRequest(GET, "/nonexistent-endpoint")
      val result  = route(app, request).fold(fail("Route not found"))(identity)

      status(result) mustBe NOT_FOUND
      contentType(result) mustBe Some("application/json")
    }
  }
}
