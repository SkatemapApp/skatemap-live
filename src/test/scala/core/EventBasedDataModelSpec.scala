package core

import adapters.playhttp.LocationJsonFormats._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

class EventBasedDataModelSpec extends AnyWordSpec with Matchers {

  "Event-based data models" should {

    "support Play JSON serialization with timestamps" in {
      val update = LocationUpdate("event-1", "s1", -0.1276, 51.5074)
      val json   = Json.toJson(update)
      val parsed = json.as[LocationUpdate]

      parsed.eventId shouldBe update.eventId
      parsed.skaterId shouldBe update.skaterId
      parsed.longitude shouldBe update.longitude
      parsed.latitude shouldBe update.latitude
    }

    "support framework-agnostic JSON serialization" in {
      val update = LocationUpdate("event-1", "s1", -0.1276, 51.5074)

      val json   = JsonCodec.locationUpdateCodec.encode(update)
      val parsed = JsonCodec.locationUpdateCodec.decode(json)

      parsed shouldBe Right(update)
    }

    "handle Location model with timestamps" in {
      val location = Location("s1", -0.1276, 51.5074, System.currentTimeMillis)

      val json   = JsonCodec.locationCodec.encode(location)
      val parsed = JsonCodec.locationCodec.decode(json)

      parsed shouldBe Right(location)
    }

    "handle explicit and default timestamps in LocationUpdate" in {
      val updateWithTimestamp = LocationUpdate("event-1", "s1", -0.1276, 51.5074, 1234567890L)
      updateWithTimestamp.timestamp shouldBe 1234567890L

      val updateWithDefault = LocationUpdate("event-1", "s1", -0.1276, 51.5074)
      updateWithDefault.timestamp should be > 0L
    }
  }
}
