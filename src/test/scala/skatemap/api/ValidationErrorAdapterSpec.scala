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
      val error  = TestErrorWithMixedTypes()
      val result = ValidationErrorAdapter.toJsonResponse(error)

      result.header.status shouldBe 400

      error.details should be(defined)
      val Some(details) = error.details
      details should contain key "stringField"
      details should contain key "doubleField"
      details should contain key "intField"
      details should contain key "boolField"

      details("stringField") shouldBe "test string"
      details("doubleField") shouldBe 42.5
      details("intField") shouldBe 123
      details("boolField") shouldBe true
      details("longField") shouldBe 999L
    }
  }
}
