package skatemap.core

import skatemap.domain.LocationUpdate
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class LocationValidatorSpec extends AnyWordSpec with Matchers {

  "LocationValidator" should {

    "successfully validate a valid location update" in {
      val eventId     = UUID.randomUUID().toString
      val skaterId    = UUID.randomUUID().toString
      val coordinates = Some(Array(0.0, 50.0))

      val result = LocationValidator.validate(eventId, skaterId, coordinates, 1000L)

      result should matchPattern { case Right(LocationUpdate(`eventId`, `skaterId`, 0.0, 50.0, _)) =>
      }
    }

    "validate boundary coordinate values" in {
      val eventId  = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString

      val boundaryCases = List(
        (Some(Array(-180.0, -90.0)), -180.0, -90.0),
        (Some(Array(180.0, 90.0)), 180.0, 90.0),
        (Some(Array(0.0, 0.0)), 0.0, 0.0)
      )

      boundaryCases.foreach { case (coordinates, expectedLon, expectedLat) =>
        val result = LocationValidator.validate(eventId, skaterId, coordinates, 1000L)
        result should matchPattern {
          case Right(LocationUpdate(`eventId`, `skaterId`, `expectedLon`, `expectedLat`, _)) =>
        }
      }
    }

    "reject invalid skating event ID" in {
      val skaterId    = UUID.randomUUID().toString
      val coordinates = Some(Array(0.0, 50.0))

      val result = LocationValidator.validate("invalid-uuid", skaterId, coordinates, 3000L)

      result shouldBe Left(InvalidSkatingEventIdError())
    }

    "reject invalid skater ID" in {
      val eventId     = UUID.randomUUID().toString
      val coordinates = Some(Array(0.0, 50.0))

      val result = LocationValidator.validate(eventId, "invalid-uuid", coordinates, 4000L)

      result shouldBe Left(InvalidSkaterIdError())
    }

    "reject invalid longitude values" in {
      val eventId  = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString

      val invalidLongitudeCases = List(
        (Some(Array(181.0, 50.0)), 181.0),
        (Some(Array(-181.0, 50.0)), -181.0),
        (Some(Array(200.0, 50.0)), 200.0)
      )

      invalidLongitudeCases.foreach { case (coordinates, invalidLon) =>
        val result = LocationValidator.validate(eventId, skaterId, coordinates, 1000L)
        result shouldBe Left(InvalidLongitudeError(invalidLon))
      }
    }

    "reject invalid latitude values" in {
      val eventId  = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString

      val invalidLatitudeCases = List(
        (Some(Array(0.0, 91.0)), 91.0),
        (Some(Array(0.0, -91.0)), -91.0),
        (Some(Array(0.0, 100.0)), 100.0)
      )

      invalidLatitudeCases.foreach { case (coordinates, invalidLat) =>
        val result = LocationValidator.validate(eventId, skaterId, coordinates, 1000L)
        result shouldBe Left(InvalidLatitudeError(invalidLat))
      }
    }

    "reject missing coordinates" in {
      val eventId  = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString

      val result = LocationValidator.validate(eventId, skaterId, None, 5000L)

      result shouldBe Left(MissingCoordinatesError())
    }

    "reject coordinates array with wrong length" in {
      val eventId  = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString

      val wrongLengthCases = List(
        Some(Array(0.0)),
        Some(Array(0.0, 50.0, 100.0)),
        Some(Array.empty[Double])
      )

      wrongLengthCases.foreach { coordinates =>
        val result = LocationValidator.validate(eventId, skaterId, coordinates, 1000L)
        result shouldBe Left(InvalidCoordinatesLengthError())
      }
    }

    "test field method on validation errors" in {
      val longError          = InvalidLongitudeError(200.0)
      val latError           = InvalidLatitudeError(100.0)
      val missingCoordsError = MissingCoordinatesError()
      val invalidLengthError = InvalidCoordinatesLengthError()

      longError.field shouldBe Some("coordinates[0]")
      latError.field shouldBe Some("coordinates[1]")
      missingCoordsError.field shouldBe None
      invalidLengthError.field shouldBe None
    }

  }
}
