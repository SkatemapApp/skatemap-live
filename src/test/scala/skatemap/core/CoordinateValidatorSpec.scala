package skatemap.core

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import skatemap.domain.Coordinates

class CoordinateValidatorSpec extends AnyWordSpec with Matchers {

  "CoordinateValidator.validateBounds" should {

    "accept basic valid coordinates" in {
      val coords = Coordinates(0.0, 0.0)
      val result = CoordinateValidator.validateBounds(coords)

      result shouldBe Right(coords)
    }

    "accept real-world coordinates" in {
      val validCoordinates = List(
        Coordinates(-0.1276, 51.5074),  // London
        Coordinates(-74.0060, 40.7128), // New York
        Coordinates(139.6917, 35.6895)  // Tokyo
      )

      validCoordinates.foreach { coords =>
        val result = CoordinateValidator.validateBounds(coords)
        result shouldBe Right(coords)
      }
    }

    "accept exact boundary coordinates" in {
      val boundaryCoordinates = List(
        Coordinates(-180.0, -90.0),
        Coordinates(180.0, 90.0),
        Coordinates(-180.0, 90.0),
        Coordinates(180.0, -90.0),
        Coordinates(0.0, -90.0),
        Coordinates(0.0, 90.0),
        Coordinates(-180.0, 0.0),
        Coordinates(180.0, 0.0)
      )

      boundaryCoordinates.foreach { coords =>
        val result = CoordinateValidator.validateBounds(coords)
        result shouldBe Right(coords)
      }
    }

    "reject longitude outside valid range" in {
      val invalidLongitudes = List(
        Coordinates(-180.1, 0.0),
        Coordinates(180.1, 0.0),
        Coordinates(-181.0, 0.0),
        Coordinates(181.0, 0.0),
        Coordinates(-200.0, 0.0),
        Coordinates(200.0, 0.0),
        Coordinates(-360.0, 0.0),
        Coordinates(360.0, 0.0)
      )

      invalidLongitudes.foreach { coords =>
        val result = CoordinateValidator.validateBounds(coords)
        result shouldBe Left(InvalidLongitudeError(coords.longitude))
      }
    }

    "reject latitude outside valid range" in {
      val invalidLatitudes = List(
        Coordinates(0.0, -90.1),
        Coordinates(0.0, 90.1),
        Coordinates(0.0, -91.0),
        Coordinates(0.0, 91.0),
        Coordinates(0.0, -100.0),
        Coordinates(0.0, 100.0),
        Coordinates(0.0, -180.0),
        Coordinates(0.0, 180.0)
      )

      invalidLatitudes.foreach { coords =>
        val result = CoordinateValidator.validateBounds(coords)
        result shouldBe Left(InvalidLatitudeError(coords.latitude))
      }
    }

    "reject both longitude and latitude outside valid range (longitude checked first)" in {
      val invalidBoth = Coordinates(181.0, 91.0)
      val result      = CoordinateValidator.validateBounds(invalidBoth)

      result shouldBe Left(InvalidLongitudeError(181.0))
    }

    "handle edge cases with special Double values" in {
      val infinityEdgeCases = List(
        (Coordinates(Double.PositiveInfinity, 0.0), "positive infinity longitude"),
        (Coordinates(0.0, Double.PositiveInfinity), "positive infinity latitude"),
        (Coordinates(Double.NegativeInfinity, 0.0), "negative infinity longitude"),
        (Coordinates(0.0, Double.NegativeInfinity), "negative infinity latitude")
      )

      infinityEdgeCases.foreach { case (coords, description) =>
        val result = CoordinateValidator.validateBounds(coords)

        withClue(s"$description should be rejected: ") {
          result match {
            case Left(_: InvalidLongitudeError) => succeed
            case Left(_: InvalidLatitudeError)  => succeed
            case _                              => fail(s"Expected longitude or latitude error for $description")
          }
        }
      }

      val nanLongitude = Coordinates(Double.NaN, 0.0)
      val nanLatitude  = Coordinates(0.0, Double.NaN)

      val nanLongResult = CoordinateValidator.validateBounds(nanLongitude)
      val nanLatResult  = CoordinateValidator.validateBounds(nanLatitude)

      nanLongResult shouldBe Right(nanLongitude)
      nanLatResult shouldBe Right(nanLatitude)
    }

    "handle very small precision values" in {
      val highPrecisionCoordinates = List(
        Coordinates(-179.99999999999999, -89.99999999999999),
        Coordinates(179.99999999999999, 89.99999999999999),
        Coordinates(0.000000000000001, 0.000000000000001),
        Coordinates(-0.000000000000001, -0.000000000000001)
      )

      highPrecisionCoordinates.foreach { coords =>
        val result = CoordinateValidator.validateBounds(coords)
        result shouldBe Right(coords)
      }
    }

    "handle very large invalid values" in {
      val veryLargeInvalid = List(
        Coordinates(Double.MaxValue, 0.0),
        Coordinates(0.0, Double.MaxValue),
        Coordinates(Double.MinValue, 0.0),
        Coordinates(0.0, Double.MinValue)
      )

      veryLargeInvalid.foreach { coords =>
        val result = CoordinateValidator.validateBounds(coords)
        result.isLeft shouldBe true
      }
    }
  }
}
