package skatemap.core

import org.scalacheck.Gen
import org.scalacheck.Prop.forAll
import org.scalatestplus.scalacheck.Checkers
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.{Locale, UUID}

class UuidValidatorSpec extends AnyWordSpec with Matchers with Checkers {

  "UuidValidator.validateEventId" should {

    "accept valid UUID string" in {
      val validUuid = "550e8400-e29b-41d4-a716-446655440000"
      val result    = UuidValidator.validateEventId(validUuid)

      result shouldBe Right(UUID.fromString(validUuid))
    }

    "accept UUID with different formats" in {
      val validUuids = List(
        "550e8400-e29b-41d4-a716-446655440000",
        "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
        "00000000-0000-0000-0000-000000000000"
      )

      validUuids.foreach { uuid =>
        val result = UuidValidator.validateEventId(uuid)
        result shouldBe Right(UUID.fromString(uuid))
      }
    }

    "reject invalid UUID formats" in {
      val invalidUuids = List(
        "invalid-uuid",
        "550e8400-e29b-41d4-a716",
        "550e8400-e29b-41d4-a716-446655440000-extra",
        "550e8400e29b41d4a716446655440000",
        "",
        "not-a-uuid-at-all"
      )

      invalidUuids.foreach { invalidUuid =>
        val result = UuidValidator.validateEventId(invalidUuid)
        result shouldBe Left(InvalidSkatingEventIdError())
      }
    }
  }

  "UuidValidator.validateSkaterId" should {

    "accept valid UUID string" in {
      val validUuid = "550e8400-e29b-41d4-a716-446655440001"
      val result    = UuidValidator.validateSkaterId(validUuid)

      result shouldBe Right(UUID.fromString(validUuid))
    }

    "accept UUID with different formats" in {
      val validUuids = List(
        "550e8400-e29b-41d4-a716-446655440001",
        "6ba7b810-9dad-11d1-80b4-00c04fd430c9",
        "11111111-1111-1111-1111-111111111111"
      )

      validUuids.foreach { uuid =>
        val result = UuidValidator.validateSkaterId(uuid)
        result shouldBe Right(UUID.fromString(uuid))
      }
    }

    "reject invalid UUID formats" in {
      val invalidUuids = List(
        "invalid-uuid",
        "550e8400-e29b-41d4-a716",
        "550e8400-e29b-41d4-a716-446655440001-extra",
        "550e8400e29b41d4a716446655440001",
        "",
        "not-a-uuid-at-all"
      )

      invalidUuids.foreach { invalidUuid =>
        val result = UuidValidator.validateSkaterId(invalidUuid)
        result shouldBe Left(InvalidSkaterIdError())
      }
    }
  }

  "UuidValidator validation methods" should {

    "return different error types for event ID vs skater ID" in {
      val invalidUuid = "invalid"

      val eventResult  = UuidValidator.validateEventId(invalidUuid)
      val skaterResult = UuidValidator.validateSkaterId(invalidUuid)

      eventResult shouldBe Left(InvalidSkatingEventIdError())
      skaterResult shouldBe Left(InvalidSkaterIdError())

      eventResult should not equal skaterResult
    }
  }

  "UuidValidator (property-based)" should {

    def isValidUuid(s: String): Boolean =
      try {
        UUID.fromString(s)
        true
      } catch {
        case _: IllegalArgumentException => false
      }

    val validUuidGen: Gen[UUID]         = Gen.uuid
    val validUuidStringGen: Gen[String] = validUuidGen.map(_.toString)

    val invalidUuidGen: Gen[String] = Gen.oneOf(
      Gen.alphaNumStr.suchThat(s => s.nonEmpty && s.length < 36 && !isValidUuid(s)),
      Gen.listOfN(40, Gen.alphaNumChar).map(_.mkString),
      Gen.const(""),
      Gen.const("not-a-uuid"),
      Gen.const("550e8400-e29b-41d4-a716"),
      Gen.const("550e8400e29b41d4a716446655440000"),
      Gen.const("550e8400-e29b-41d4-a716-446655440000-extra"),
      Gen.listOfN(36, Gen.oneOf('x', '/', '!', ' ', '@')).map(_.mkString)
    )

    "accept all valid UUID strings for validateEventId" in {
      check(forAll(validUuidStringGen) { uuidString =>
        UuidValidator.validateEventId(uuidString) match {
          case Right(uuid) => uuid.toString === uuidString
          case Left(_)     => false
        }
      })
    }

    "accept all valid UUID strings for validateSkaterId" in {
      check(forAll(validUuidStringGen) { uuidString =>
        UuidValidator.validateSkaterId(uuidString) match {
          case Right(uuid) => uuid.toString === uuidString
          case Left(_)     => false
        }
      })
    }

    "reject all invalid UUID strings for validateEventId with correct error type" in {
      check(forAll(invalidUuidGen) { invalidString =>
        UuidValidator.validateEventId(invalidString) match {
          case Left(_: InvalidSkatingEventIdError) => true
          case _                                   => false
        }
      })
    }

    "reject all invalid UUID strings for validateSkaterId with correct error type" in {
      check(forAll(invalidUuidGen) { invalidString =>
        UuidValidator.validateSkaterId(invalidString) match {
          case Left(_: InvalidSkaterIdError) => true
          case _                             => false
        }
      })
    }

    "return consistent error types for the same invalid input" in {
      check(forAll(invalidUuidGen) { invalidString =>
        val eventError  = UuidValidator.validateEventId(invalidString)
        val skaterError = UuidValidator.validateSkaterId(invalidString)

        (eventError, skaterError) match {
          case (Left(_: InvalidSkatingEventIdError), Left(_: InvalidSkaterIdError)) => true
          case _                                                                    => false
        }
      })
    }

    "reject UUIDs with leading whitespace" in {
      check(forAll(validUuidStringGen) { uuidString =>
        val withWhitespace = " " + uuidString
        UuidValidator.validateEventId(withWhitespace) match {
          case Left(_: InvalidSkatingEventIdError) => true
          case _                                   => false
        }
      })
    }

    "reject UUIDs with trailing whitespace" in {
      check(forAll(validUuidStringGen) { uuidString =>
        val withWhitespace = uuidString + " "
        UuidValidator.validateSkaterId(withWhitespace) match {
          case Left(_: InvalidSkaterIdError) => true
          case _                             => false
        }
      })
    }

    "accept uppercase UUIDs" in {
      check(forAll(validUuidStringGen) { uuidString =>
        val uppercaseUuid = uuidString.toUpperCase(Locale.ROOT)
        UuidValidator.validateEventId(uppercaseUuid).isRight
      })
    }
  }
}
