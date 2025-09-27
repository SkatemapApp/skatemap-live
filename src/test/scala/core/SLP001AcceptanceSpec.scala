package core

import adapters.playhttp.LocationJsonFormats._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

class SLP001AcceptanceSpec extends AnyWordSpec with Matchers {

  "SLP-001 Acceptance Test" should {

    "pass the exact test command from SLP-001 specification" in {
      val update = LocationUpdate("event-1", "s1", -0.1276, 51.5074)
      val json   = Json.toJson(update)
      val parsed = json.as[LocationUpdate]

      parsed.eventId shouldBe update.eventId
      parsed.skaterId shouldBe update.skaterId
      parsed.longitude shouldBe update.longitude
      parsed.latitude shouldBe update.latitude
    }

    "demonstrate framework-agnostic JSON serialization" in {
      val update = LocationUpdate("event-1", "s1", -0.1276, 51.5074)

      // Using framework-agnostic JsonCodec
      val json   = JsonCodec.locationUpdateCodec.encode(update)
      val parsed = JsonCodec.locationUpdateCodec.decode(json)

      parsed shouldBe Right(update)
    }

    "demonstrate Location model usage" in {
      val location = Location("s1", 51.5074, -0.1276, System.currentTimeMillis)

      // Framework-agnostic serialization
      val json   = JsonCodec.locationCodec.encode(location)
      val parsed = JsonCodec.locationCodec.decode(json)

      parsed shouldBe Right(location)
    }

    "show timestamp handling in LocationUpdate" in {
      // With explicit timestamp
      val updateWithTimestamp = LocationUpdate("event-1", "s1", -0.1276, 51.5074, 1234567890L)
      updateWithTimestamp.timestamp shouldBe 1234567890L

      // With default timestamp
      val updateWithDefault = LocationUpdate("event-1", "s1", -0.1276, 51.5074)
      updateWithDefault.timestamp should be > 0L
    }
  }
}
