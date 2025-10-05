package skatemap.core

import org.scalacheck.Gen
import org.scalacheck.Prop.forAll
import org.scalatestplus.scalacheck.Checkers
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import skatemap.domain.Coordinates
import skatemap.core._

class CoordinateValidatorSpec extends AnyWordSpec with Matchers with Checkers {

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

      val nanCases = List(
        (Coordinates(Double.NaN, 0.0), "NaN longitude"),
        (Coordinates(0.0, Double.NaN), "NaN latitude")
      )

      nanCases.foreach { case (coords, description) =>
        val result = CoordinateValidator.validateBounds(coords)

        withClue(s"$description should be rejected: ") {
          result.isLeft shouldBe true
        }
      }
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

  "CoordinateValidator.validateBounds (property-based)" should {

    val validLongitudeGen: Gen[Double] = Gen.choose(-180.0, 180.0)
    val validLatitudeGen: Gen[Double]  = Gen.choose(-90.0, 90.0)
    val validCoordinatesGen: Gen[Coordinates] = for {
      lon <- validLongitudeGen
      lat <- validLatitudeGen
    } yield Coordinates(lon, lat)

    val invalidLongitudeGen: Gen[Double] = Gen.oneOf(
      Gen.choose(Double.MinValue, -180.1),
      Gen.choose(180.1, Double.MaxValue)
    )

    val invalidLatitudeGen: Gen[Double] = Gen.oneOf(
      Gen.choose(Double.MinValue, -90.1),
      Gen.choose(90.1, Double.MaxValue)
    )

    "accept all coordinates within valid bounds" in {
      check(forAll(validCoordinatesGen) { coords =>
        CoordinateValidator.validateBounds(coords).isRight
      })
    }

    "reject coordinates with longitude outside valid range" in {
      check(forAll(invalidLongitudeGen, validLatitudeGen) { (lon, lat) =>
        val coords = Coordinates(lon, lat)
        CoordinateValidator.validateBounds(coords) match {
          case Left(_: InvalidLongitudeError) => true
          case _                              => false
        }
      })
    }

    "reject coordinates with latitude outside valid range" in {
      check(forAll(validLongitudeGen, invalidLatitudeGen) { (lon, lat) =>
        val coords = Coordinates(lon, lat)
        CoordinateValidator.validateBounds(coords) match {
          case Left(_: InvalidLatitudeError) => true
          case _                             => false
        }
      })
    }

    "reject coordinates with both invalid longitude and latitude (longitude checked first)" in {
      check(forAll(invalidLongitudeGen, invalidLatitudeGen) { (lon, lat) =>
        val coords = Coordinates(lon, lat)
        CoordinateValidator.validateBounds(coords) match {
          case Left(_: InvalidLongitudeError) => true
          case _                              => false
        }
      })
    }

    "reject coordinates with NaN longitude" in {
      check(forAll(validLatitudeGen) { lat =>
        val coords = Coordinates(Double.NaN, lat)
        CoordinateValidator.validateBounds(coords) match {
          case Left(_: InvalidLongitudeError) => true
          case _                              => false
        }
      })
    }

    "reject coordinates with NaN latitude" in {
      check(forAll(validLongitudeGen) { lon =>
        val coords = Coordinates(lon, Double.NaN)
        CoordinateValidator.validateBounds(coords) match {
          case Left(_: InvalidLatitudeError) => true
          case _                             => false
        }
      })
    }

    "reject coordinates with infinite longitude" in {
      val infiniteLongitudeGen = Gen.oneOf(Double.PositiveInfinity, Double.NegativeInfinity)
      check(forAll(infiniteLongitudeGen, validLatitudeGen) { (lon, lat) =>
        val coords = Coordinates(lon, lat)
        CoordinateValidator.validateBounds(coords) match {
          case Left(_: InvalidLongitudeError) => true
          case _                              => false
        }
      })
    }

    "reject coordinates with infinite latitude" in {
      val infiniteLatitudeGen = Gen.oneOf(Double.PositiveInfinity, Double.NegativeInfinity)
      check(forAll(validLongitudeGen, infiniteLatitudeGen) { (lon, lat) =>
        val coords = Coordinates(lon, lat)
        CoordinateValidator.validateBounds(coords) match {
          case Left(_: InvalidLatitudeError) => true
          case _                             => false
        }
      })
    }
  }
}
