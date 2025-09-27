package adapters.play

import core._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class ValidationErrorAdapterSpec extends AnyWordSpec with Matchers {

  "ValidationErrorAdapter" should {

    "convert basic error to JSON response" in {
      val error  = InvalidJsonError()
      val result = ValidationErrorAdapter.toJsonResponse(error)

      result.header.status shouldBe 400
    }

    "convert error with details to JSON response" in {
      val error  = InvalidLongitudeError(200.0)
      val result = ValidationErrorAdapter.toJsonResponse(error)

      result.header.status shouldBe 400
    }

    "handle various types in details map" in {
      val error  = TestErrorWithMixedTypes()
      val result = ValidationErrorAdapter.toJsonResponse(error)

      result.header.status shouldBe 400
    }
  }
}
