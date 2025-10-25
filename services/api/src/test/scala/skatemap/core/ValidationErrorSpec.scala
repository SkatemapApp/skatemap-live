package skatemap.core

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ValidationErrorSpec extends AnyWordSpec with Matchers {

  "InvalidSkatingEventIdError" should {

    "be validation error with no details" in {
      val error = InvalidSkatingEventIdError()

      error.field shouldBe None
      error.details shouldBe None
    }
  }

  "InvalidSkaterIdError" should {

    "be validation error with no details" in {
      val error = InvalidSkaterIdError()

      error.field shouldBe None
      error.details shouldBe None
    }
  }

  "InvalidLongitudeError" should {

    "provide field mapping and details" in {
      val error = InvalidLongitudeError(200.0)

      error.field shouldBe Some("coordinates[0]")
      error.details shouldBe defined

      val Some(details) = error.details
      details("value") shouldBe 200.0
    }
  }

  "InvalidLatitudeError" should {

    "provide field mapping and details" in {
      val error = InvalidLatitudeError(100.0)

      error.field shouldBe Some("coordinates[1]")
      error.details shouldBe defined

      val Some(details) = error.details
      details("value") shouldBe 100.0
    }
  }

  "InvalidJsonError" should {

    "be validation error with no details" in {
      val error = InvalidJsonError()

      error.field shouldBe None
      error.details shouldBe None
    }
  }

  "MissingCoordinatesError" should {

    "be validation error with no details" in {
      val error = MissingCoordinatesError()

      error.field shouldBe None
      error.details shouldBe None
    }
  }

  "InvalidCoordinatesLengthError" should {

    "be validation error with no details" in {
      val error = InvalidCoordinatesLengthError()

      error.field shouldBe None
      error.details shouldBe None
    }
  }

  "ValidationError equality" should {

    "be equal for same error types with same values" in {
      val error1 = InvalidLongitudeError(200.0)
      val error2 = InvalidLongitudeError(200.0)

      error1 shouldBe error2
    }

    "be different for same error types with different values" in {
      val error1 = InvalidLongitudeError(200.0)
      val error2 = InvalidLongitudeError(250.0)

      error1 should not be error2
    }

  }
}
