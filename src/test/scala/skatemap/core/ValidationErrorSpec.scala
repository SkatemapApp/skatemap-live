package skatemap.core

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ValidationErrorSpec extends AnyWordSpec with Matchers {

  "InvalidSkatingEventIdError" should {

    "have correct code and message" in {
      val error = InvalidSkatingEventIdError()

      error.code shouldBe "INVALID_SKATING_EVENT_ID"
      error.message shouldBe "Skating event ID must be a valid UUID"
    }

    "have no field or details" in {
      val error = InvalidSkatingEventIdError()

      error.field shouldBe None
      error.details shouldBe None
    }
  }

  "InvalidSkaterIdError" should {

    "have correct code and message" in {
      val error = InvalidSkaterIdError()

      error.code shouldBe "INVALID_SKATER_ID"
      error.message shouldBe "Skater ID must be a valid UUID"
    }

    "have no field or details" in {
      val error = InvalidSkaterIdError()

      error.field shouldBe None
      error.details shouldBe None
    }
  }

  "InvalidLongitudeError" should {

    "have correct code and message" in {
      val error = InvalidLongitudeError(200.0)

      error.code shouldBe "INVALID_LONGITUDE"
      error.message shouldBe "Longitude must be between -180.0 and 180.0"
    }

    "have correct field mapping" in {
      val error = InvalidLongitudeError(200.0)

      error.field shouldBe Some("coordinates[0]")
    }

    "have correct details with value and constraint" in {
      val error = InvalidLongitudeError(200.0)

      error.details shouldBe defined
      val Some(details) = error.details

      details should contain key "field"
      details should contain key "value"
      details should contain key "constraint"

      details("field") shouldBe "coordinates[0]"
      details("value") shouldBe 200.0
      details("constraint") shouldBe "range(-180.0, 180.0)"
    }

    "handle different longitude values in details" in {
      val testValues = List(-181.0, 181.0, 300.0, -300.0)

      testValues.foreach { value =>
        val error         = InvalidLongitudeError(value)
        val Some(details) = error.details
        details("value") shouldBe value
      }
    }
  }

  "InvalidLatitudeError" should {

    "have correct code and message" in {
      val error = InvalidLatitudeError(100.0)

      error.code shouldBe "INVALID_LATITUDE"
      error.message shouldBe "Latitude must be between -90.0 and 90.0"
    }

    "have correct field mapping" in {
      val error = InvalidLatitudeError(100.0)

      error.field shouldBe Some("coordinates[1]")
    }

    "have correct details with value and constraint" in {
      val error = InvalidLatitudeError(100.0)

      error.details shouldBe defined
      val Some(details) = error.details

      details should contain key "field"
      details should contain key "value"
      details should contain key "constraint"

      details("field") shouldBe "coordinates[1]"
      details("value") shouldBe 100.0
      details("constraint") shouldBe "range(-90.0, 90.0)"
    }

    "handle different latitude values in details" in {
      val testValues = List(-91.0, 91.0, 150.0, -150.0)

      testValues.foreach { value =>
        val error         = InvalidLatitudeError(value)
        val Some(details) = error.details
        details("value") shouldBe value
      }
    }
  }

  "InvalidJsonError" should {

    "have correct code and message" in {
      val error = InvalidJsonError()

      error.code shouldBe "INVALID_JSON"
      error.message shouldBe "Request body must be valid JSON"
    }

    "have no field or details" in {
      val error = InvalidJsonError()

      error.field shouldBe None
      error.details shouldBe None
    }
  }

  "MissingCoordinatesError" should {

    "have correct code and message" in {
      val error = MissingCoordinatesError()

      error.code shouldBe "MISSING_COORDINATES"
      error.message shouldBe "Request must contain 'coordinates' field with array of numbers"
    }

    "have no field or details" in {
      val error = MissingCoordinatesError()

      error.field shouldBe None
      error.details shouldBe None
    }
  }

  "InvalidCoordinatesLengthError" should {

    "have correct code and message" in {
      val error = InvalidCoordinatesLengthError()

      error.code shouldBe "INVALID_COORDINATES_LENGTH"
      error.message shouldBe "Coordinates array must contain exactly 2 numbers [longitude, latitude]"
    }

    "have no field or details" in {
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
