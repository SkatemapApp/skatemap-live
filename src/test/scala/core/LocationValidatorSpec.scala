package core

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class LocationValidatorSpec extends AnyWordSpec with Matchers {

  "LocationValidator" should {

    "successfully validate a valid location update" in {
      val eventId   = UUID.randomUUID().toString
      val skaterId  = UUID.randomUUID().toString
      val validJson = """{"coordinates": [0.0, 50.0]}"""

      val result = LocationValidator.validate(eventId, skaterId, validJson)

      result shouldBe Right(LocationUpdate(eventId, skaterId, 0.0, 50.0))
    }

    "validate boundary coordinate values" in {
      val eventId  = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString

      val boundaryCases = List(
        ("""{"coordinates": [-180.0, -90.0]}""", -180.0, -90.0),
        ("""{"coordinates": [180.0, 90.0]}""", 180.0, 90.0),
        ("""{"coordinates": [0.0, 0.0]}""", 0.0, 0.0)
      )

      boundaryCases.foreach { case (json, expectedLon, expectedLat) =>
        val result = LocationValidator.validate(eventId, skaterId, json)
        result shouldBe Right(LocationUpdate(eventId, skaterId, expectedLon, expectedLat))
      }
    }

    "reject invalid skating event ID" in {
      val skaterId  = UUID.randomUUID().toString
      val validJson = """{"coordinates": [0.0, 50.0]}"""

      val result = LocationValidator.validate("invalid-uuid", skaterId, validJson)

      result shouldBe Left(InvalidSkatingEventIdError())
    }

    "reject invalid skater ID" in {
      val eventId   = UUID.randomUUID().toString
      val validJson = """{"coordinates": [0.0, 50.0]}"""

      val result = LocationValidator.validate(eventId, "invalid-uuid", validJson)

      result shouldBe Left(InvalidSkaterIdError())
    }

    "reject invalid longitude values" in {
      val eventId  = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString

      val invalidLongitudeCases = List(
        ("""{"coordinates": [181.0, 50.0]}""", 181.0),
        ("""{"coordinates": [-181.0, 50.0]}""", -181.0),
        ("""{"coordinates": [200.0, 50.0]}""", 200.0)
      )

      invalidLongitudeCases.foreach { case (json, invalidLon) =>
        val result = LocationValidator.validate(eventId, skaterId, json)
        result shouldBe Left(InvalidLongitudeError(invalidLon))
      }
    }

    "reject invalid latitude values" in {
      val eventId  = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString

      val invalidLatitudeCases = List(
        ("""{"coordinates": [0.0, 91.0]}""", 91.0),
        ("""{"coordinates": [0.0, -91.0]}""", -91.0),
        ("""{"coordinates": [0.0, 100.0]}""", 100.0)
      )

      invalidLatitudeCases.foreach { case (json, invalidLat) =>
        val result = LocationValidator.validate(eventId, skaterId, json)
        result shouldBe Left(InvalidLatitudeError(invalidLat))
      }
    }

    "reject malformed JSON" in {
      val eventId  = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString

      val malformedJsonCases = List(
        "not json",
        "{invalid json}",
        "[1,2,3]",
        ""
      )

      malformedJsonCases.foreach { json =>
        val result = LocationValidator.validate(eventId, skaterId, json)
        result match {
          case Left(error) => error shouldBe a[InvalidJsonError]
          case Right(_)    => fail(s"Expected validation to fail for JSON: $json")
        }
      }
    }

    "reject missing coordinates field" in {
      val eventId                = UUID.randomUUID().toString
      val skaterId               = UUID.randomUUID().toString
      val jsonWithoutCoordinates = """{"location": [0.0, 50.0]}"""

      val result = LocationValidator.validate(eventId, skaterId, jsonWithoutCoordinates)

      result shouldBe Left(MissingCoordinatesError())
    }

    "reject coordinates array with wrong length" in {
      val eventId  = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString

      val wrongLengthCases = List(
        """{"coordinates": [0.0]}""",
        """{"coordinates": [0.0, 50.0, 100.0]}""",
        """{"coordinates": []}"""
      )

      wrongLengthCases.foreach { json =>
        val result = LocationValidator.validate(eventId, skaterId, json)
        result shouldBe Left(InvalidCoordinatesLengthError())
      }
    }

    "handle various JSON formatting" in {
      val eventId  = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString

      val validFormattingCases = List(
        """{"coordinates":[0.0,50.0]}""",
        """{ "coordinates" : [ 0.0 , 50.0 ] }""",
        """{"coordinates": [0, 50]}""",
        """{"coordinates": [-1.5, 45.5]}"""
      )

      validFormattingCases.foreach { json =>
        val result = LocationValidator.validate(eventId, skaterId, json)
        result shouldBe a[Right[_, _]]
      }
    }

    "test field method on validation errors" in {
      val longError  = InvalidLongitudeError(200.0)
      val latError   = InvalidLatitudeError(100.0)
      val basicError = InvalidJsonError()

      longError.field shouldBe Some("coordinates[0]")
      latError.field shouldBe Some("coordinates[1]")
      basicError.field shouldBe None
    }

    "reject coordinates field with malformed structure" in {
      val eventId  = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString

      // Test case that hits the uncovered line where coordinates field exists but array pattern doesn't match
      val malformedCoordinatesJson = """{"coordinates": "not an array"}"""

      val result = LocationValidator.validate(eventId, skaterId, malformedCoordinatesJson)

      result shouldBe Left(MissingCoordinatesError())
    }

    "handle unexpected exceptions in JSON parsing" in {
      val eventId  = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString

      // Force a NumberFormatException by having invalid number format
      val invalidNumberJson = """{"coordinates": [abc, def]}"""

      val result = LocationValidator.validate(eventId, skaterId, invalidNumberJson)

      result shouldBe Left(InvalidJsonError())
    }

  }
}
