package skatemap.core

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class UuidValidatorSpec extends AnyWordSpec with Matchers {

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
}
