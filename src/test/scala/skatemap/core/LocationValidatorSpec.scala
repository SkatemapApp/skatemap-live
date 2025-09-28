package skatemap.core

import skatemap.domain.LocationUpdate
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class LocationValidatorSpec extends AnyWordSpec with Matchers {

  private val validEventId  = UUID.randomUUID().toString
  private val validSkaterId = UUID.randomUUID().toString

  "LocationValidator.validate" should {

    "create valid LocationUpdate with all correct inputs" in {
      val coordinates = Some(Array(0.0, 50.0))
      val timestamp   = 1000L

      val result = LocationValidator.validate(validEventId, validSkaterId, coordinates, timestamp)

      result should matchPattern {
        case Right(LocationUpdate(`validEventId`, `validSkaterId`, 0.0, 50.0, `timestamp`)) =>
      }
    }

    "preserve timestamp in result" in {
      val coordinates = Some(Array(0.0, 50.0))
      val timestamp   = 1640995200000L

      val result = LocationValidator.validate(validEventId, validSkaterId, coordinates, timestamp)

      result.map(_.timestamp) shouldBe Right(timestamp)
    }

    "reject invalid event ID" in {
      val coordinates = Some(Array(0.0, 50.0))

      val result = LocationValidator.validate("invalid-uuid", validSkaterId, coordinates, 1000L)

      result shouldBe Left(InvalidSkatingEventIdError())
    }

    "reject invalid skater ID" in {
      val coordinates = Some(Array(0.0, 50.0))

      val result = LocationValidator.validate(validEventId, "invalid-uuid", coordinates, 1000L)

      result shouldBe Left(InvalidSkaterIdError())
    }

    "reject missing coordinates" in {
      val result = LocationValidator.validate(validEventId, validSkaterId, None, 1000L)

      result shouldBe Left(MissingCoordinatesError())
    }

    "reject coordinates with wrong array length" in {
      val wrongLengthCases = List(
        Some(Array.empty[Double]),
        Some(Array(0.0)),
        Some(Array(0.0, 50.0, 100.0))
      )

      wrongLengthCases.foreach { coordinates =>
        val result = LocationValidator.validate(validEventId, validSkaterId, coordinates, 1000L)
        result shouldBe Left(InvalidCoordinatesLengthError())
      }
    }
  }
}
