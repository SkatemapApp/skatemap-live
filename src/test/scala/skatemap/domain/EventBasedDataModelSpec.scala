package skatemap.domain

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EventBasedDataModelSpec extends AnyWordSpec with Matchers {

  "Location" should {

    "create with basic valid coordinates" in {
      val location = Location("skater-1", -0.1276, 51.5074, 1234567890L)

      location.skaterId shouldBe "skater-1"
      location.longitude shouldBe -0.1276
      location.latitude shouldBe 51.5074
      location.timestamp shouldBe 1234567890L
    }

    "handle boundary coordinates" in {
      val location = Location("skater-1", -180.0, -90.0, 1000L)

      location.longitude shouldBe -180.0
      location.latitude shouldBe -90.0
    }

    "handle high precision coordinates" in {
      val location = Location("skater-1", -0.123456789, 51.987654321, 1000L)

      location.longitude shouldBe -0.123456789
      location.latitude shouldBe 51.987654321
    }

    "preserve timestamp values" in {
      val timestamp = 1640995200000L
      val location  = Location("skater-1", 0.0, 0.0, timestamp)

      location.timestamp shouldBe timestamp
    }
  }

  "LocationUpdate" should {

    "create with all required fields" in {
      val update = LocationUpdate("event-1", "skater-1", -0.1276, 51.5074, 1234567890L)

      update.eventId shouldBe "event-1"
      update.skaterId shouldBe "skater-1"
      update.longitude shouldBe -0.1276
      update.latitude shouldBe 51.5074
      update.timestamp shouldBe 1234567890L
    }

    "handle boundary coordinates" in {
      val update = LocationUpdate("event-1", "skater-1", 180.0, 90.0, 1000L)

      update.longitude shouldBe 180.0
      update.latitude shouldBe 90.0
    }

    "preserve high precision values" in {
      val update = LocationUpdate("event-1", "skater-1", 179.999999999, 89.999999999, 1000L)

      update.longitude shouldBe 179.999999999
      update.latitude shouldBe 89.999999999
    }
  }

  "Coordinates" should {

    "create with longitude and latitude" in {
      val coords = Coordinates(-0.1276, 51.5074)

      coords.longitude shouldBe -0.1276
      coords.latitude shouldBe 51.5074
    }

    "handle zero coordinates" in {
      val coords = Coordinates(0.0, 0.0)

      coords.longitude shouldBe 0.0
      coords.latitude shouldBe 0.0
    }

    "handle extreme boundary values" in {
      val coords = Coordinates(-180.0, -90.0)

      coords.longitude shouldBe -180.0
      coords.latitude shouldBe -90.0
    }
  }
}
