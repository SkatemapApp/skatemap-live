package skatemap.api.json

import skatemap.api.json.LocationJson._
import skatemap.domain.{Location, LocationUpdate}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

class LocationJsonFormatsSpec extends AnyWordSpec with Matchers {

  "LocationJsonFormats" should {

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

    "handle coordinate precision in Play JSON" in {
      val highPrecisionLocation = Location("skater-precision", -0.123456789, 51.987654321, 1111111111L)
      val json                  = Json.toJson(highPrecisionLocation)
      val parsed                = json.as[Location]

      parsed shouldBe highPrecisionLocation
      parsed.latitude shouldBe 51.987654321
      parsed.longitude shouldBe -0.123456789
    }

    "be compatible with existing coordinate format (SLP-001 test command)" in {
      val update = LocationUpdate("event-1", "s1", -0.1276, 51.5074)
      val json   = Json.toJson(update)
      val parsed = json.as[LocationUpdate]

      parsed.eventId shouldBe update.eventId
      parsed.skaterId shouldBe update.skaterId
      parsed.longitude shouldBe update.longitude
      parsed.latitude shouldBe update.latitude
    }
  }
}
