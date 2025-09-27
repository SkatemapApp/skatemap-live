package skatemap.domain

import skatemap.api.json.LocationJson._
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

    "support JSON round-trip serialization for LocationUpdate" in {
      val update = LocationUpdate("event-1", "s1", -0.1276, 51.5074)

      val json   = Json.toJson(update)
      val parsed = json.as[LocationUpdate]

      parsed shouldBe update
    }

    "support JSON round-trip serialization for Location" in {
      val location = Location("s1", -0.1276, 51.5074, System.currentTimeMillis)

      val json   = Json.toJson(location)
      val parsed = json.as[Location]

      parsed shouldBe location
    }

    "handle explicit and default timestamps in LocationUpdate" in {
      val updateWithTimestamp = LocationUpdate("event-1", "s1", -0.1276, 51.5074, 1234567890L)
      updateWithTimestamp.timestamp shouldBe 1234567890L

      val updateWithDefault = LocationUpdate("event-1", "s1", -0.1276, 51.5074)
      updateWithDefault.timestamp should be > 0L
    }
  }
}
