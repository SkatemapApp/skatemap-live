package skatemap.api.json

import skatemap.api.json.LocationJson._
import skatemap.domain.{Location, LocationUpdate}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

class LocationJsonFormatsSpec extends AnyWordSpec with Matchers {

  "Location JSON serialization" should {

    "serialize Location to JSON and back" in {
      val location = Location("skater-123", -0.1276, 51.5074, 1234567890L)
      val json     = Json.toJson(location)
      val parsed   = json.as[Location]

      parsed shouldBe location
    }

    "serialize LocationUpdate to JSON and back" in {
      val update = LocationUpdate("event-123", "skater-456", -0.1276, 51.5074, 1234567890L)
      val json   = Json.toJson(update)
      val parsed = json.as[LocationUpdate]

      parsed shouldBe update
    }

    "handle LocationUpdate without timestamp (should use default)" in {
      val jsonWithoutTimestamp = Json.obj(
        "eventId"   -> "event-123",
        "skaterId"  -> "skater-456",
        "longitude" -> -0.1276,
        "latitude"  -> 51.5074
      )

      val parsed = jsonWithoutTimestamp.as[LocationUpdate]

      parsed.eventId shouldBe "event-123"
      parsed.skaterId shouldBe "skater-456"
      parsed.longitude shouldBe -0.1276
      parsed.latitude shouldBe 51.5074
      parsed.timestamp should be > 0L
    }

    "write Location to correct JSON structure" in {
      val location = Location("skater-789", -74.0060, 40.7128, 9876543210L)
      val json     = Json.toJson(location)

      (json \ "skaterId").as[String] shouldBe "skater-789"
      (json \ "latitude").as[Double] shouldBe 40.7128
      (json \ "longitude").as[Double] shouldBe -74.0060
      (json \ "timestamp").as[Long] shouldBe 9876543210L
    }

    "write LocationUpdate to correct JSON structure" in {
      val update = LocationUpdate("event-789", "skater-123", -74.0060, 40.7128, 5555555555L)
      val json   = Json.toJson(update)

      (json \ "eventId").as[String] shouldBe "event-789"
      (json \ "skaterId").as[String] shouldBe "skater-123"
      (json \ "longitude").as[Double] shouldBe -74.0060
      (json \ "latitude").as[Double] shouldBe 40.7128
      (json \ "timestamp").as[Long] shouldBe 5555555555L
    }

    "fail to parse Location with missing fields" in {
      val missingSkaterIdJson = Json.obj(
        "latitude"  -> 51.5074,
        "longitude" -> -0.1276,
        "timestamp" -> 1234567890L
      )

      val result = missingSkaterIdJson.validate[Location]
      result shouldBe a[JsError]
    }

    "fail to parse LocationUpdate with missing required fields" in {
      val missingEventIdJson = Json.obj(
        "skaterId"  -> "skater-456",
        "longitude" -> -0.1276,
        "latitude"  -> 51.5074,
        "timestamp" -> 1234567890L
      )

      val result = missingEventIdJson.validate[LocationUpdate]
      result shouldBe a[JsError]
    }

    "handle coordinate precision and edge cases" in {
      val edgeCases = List(
        Json.obj(
          "eventId"   -> "event-1",
          "skaterId"  -> "skater-1",
          "longitude" -> -0.123456789,
          "latitude"  -> 51.987654321,
          "timestamp" -> 1000L
        ),
        Json.obj(
          "eventId"   -> "event-2",
          "skaterId"  -> "skater-2",
          "longitude" -> JsNumber(BigDecimal("1.23456789E2")),
          "latitude"  -> JsNumber(BigDecimal("-4.56789012E1")),
          "timestamp" -> 1000L
        ),
        Json.obj(
          "eventId"   -> "event-3",
          "skaterId"  -> "skater-3",
          "longitude" -> 0.0,
          "latitude"  -> -0.0,
          "timestamp" -> 1000L
        )
      )

      edgeCases.foreach { json =>
        val parsed = json.as[LocationUpdate]
        parsed.eventId should not be empty
        parsed.skaterId should not be empty
      }
    }

    "fail to parse coordinates with invalid JSON types" in {
      val invalidTypesCases = List(
        Json.obj(
          "eventId"   -> "event-123",
          "skaterId"  -> "skater-456",
          "longitude" -> "not-a-number",
          "latitude"  -> 50.0,
          "timestamp" -> 1000L
        ),
        Json.obj(
          "eventId"   -> "event-123",
          "skaterId"  -> "skater-456",
          "longitude" -> 0.0,
          "latitude"  -> true,
          "timestamp" -> 1000L
        ),
        Json.obj(
          "eventId"   -> "event-123",
          "skaterId"  -> "skater-456",
          "longitude" -> JsNull,
          "latitude"  -> 50.0,
          "timestamp" -> 1000L
        ),
        Json.obj(
          "eventId"   -> "event-123",
          "skaterId"  -> "skater-456",
          "longitude" -> 0.0,
          "latitude"  -> JsArray(),
          "timestamp" -> 1000L
        )
      )

      invalidTypesCases.foreach { json =>
        val result = json.validate[LocationUpdate]
        result shouldBe a[JsError]
      }
    }

    "fail to parse with invalid timestamp types" in {
      val invalidTimestampCases = List(
        Json.obj(
          "eventId"   -> "event-123",
          "skaterId"  -> "skater-456",
          "longitude" -> 0.0,
          "latitude"  -> 50.0,
          "timestamp" -> "not-a-number"
        ),
        Json.obj(
          "eventId"   -> "event-123",
          "skaterId"  -> "skater-456",
          "longitude" -> 0.0,
          "latitude"  -> 50.0,
          "timestamp" -> 1.5
        ),
        Json.obj(
          "eventId"   -> "event-123",
          "skaterId"  -> "skater-456",
          "longitude" -> 0.0,
          "latitude"  -> 50.0,
          "timestamp" -> true
        )
      )

      invalidTimestampCases.foreach { json =>
        val result = json.validate[LocationUpdate]
        result shouldBe a[JsError]
      }
    }

    "fail to parse with null string fields" in {
      val nullStringCases = List(
        Json.obj(
          "eventId"   -> JsNull,
          "skaterId"  -> "skater-456",
          "longitude" -> 0.0,
          "latitude"  -> 50.0,
          "timestamp" -> 1000L
        ),
        Json.obj(
          "eventId"   -> "event-123",
          "skaterId"  -> JsNull,
          "longitude" -> 0.0,
          "latitude"  -> 50.0,
          "timestamp" -> 1000L
        )
      )

      nullStringCases.foreach { json =>
        val result = json.validate[LocationUpdate]
        result shouldBe a[JsError]
      }
    }

    "handle very large timestamp values" in {
      val largeTimestamp = Long.MaxValue
      val location       = Location("skater-123", 0.0, 50.0, largeTimestamp)
      val json           = Json.toJson(location)
      val parsed         = json.as[Location]

      parsed shouldBe location
      parsed.timestamp shouldBe largeTimestamp
    }

    "preserve coordinate values through JSON round-trip" in {
      val testCoordinate = (-179.999999999999999, 89.999999999999999)
      val location       = Location("skater-precision", testCoordinate._1, testCoordinate._2, 1000L)
      val json           = Json.toJson(location)
      val parsed         = json.as[Location]

      parsed.longitude shouldBe testCoordinate._1
      parsed.latitude shouldBe testCoordinate._2
    }

  }
}
