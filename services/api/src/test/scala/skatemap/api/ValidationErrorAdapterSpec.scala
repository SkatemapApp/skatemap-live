package skatemap.api

import skatemap.core._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

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
      val error  = InvalidLongitudeError(200.0)
      val result = ValidationErrorAdapter.toJsonResponse(error)

      result.header.status shouldBe 400

      error.details should be(defined)
      val Some(details) = error.details
      details should contain key "field"
      details should contain key "value"
      details should contain key "constraint"

      details("field") shouldBe "coordinates[0]"
      details("value") shouldBe 200.0
      details("constraint") shouldBe "range(-180.0, 180.0)"
    }

  }
}
