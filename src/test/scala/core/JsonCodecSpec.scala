package core

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JsonCodecSpec extends AnyWordSpec with Matchers {

  "LocationCodec" should {

    "encode Location to JSON" in {
      val location = Location("skater-123", 51.5074, -0.1276, 1234567890L)
      val json     = JsonCodec.locationCodec.encode(location)

      json should include("\"skaterId\":\"skater-123\"")
      json should include("\"latitude\":51.5074")
      json should include("\"longitude\":-0.1276")
      json should include("\"timestamp\":1234567890")
    }

    "decode JSON to Location" in {
      val json   = """{"skaterId":"skater-123","latitude":51.5074,"longitude":-0.1276,"timestamp":1234567890}"""
      val result = JsonCodec.locationCodec.decode(json)

      result shouldBe Right(Location("skater-123", 51.5074, -0.1276, 1234567890L))
    }

    "round-trip encode/decode Location" in {
      val original = Location("skater-456", 45.4215, 2.3490, 9876543210L)
      val encoded  = JsonCodec.locationCodec.encode(original)
      val decoded  = JsonCodec.locationCodec.decode(encoded)

      decoded shouldBe Right(original)
    }

    "handle Location JSON with different whitespace" in {
      val json =
        """{ "skaterId" : "skater-789" , "latitude" : 40.7128 , "longitude" : -74.0060 , "timestamp" : 5555555555 }"""
      val result = JsonCodec.locationCodec.decode(json)

      result shouldBe Right(Location("skater-789", 40.7128, -74.0060, 5555555555L))
    }

    "fail to decode Location JSON with missing fields" in {
      val missingSkaterIdJson  = """{"latitude":51.5074,"longitude":-0.1276,"timestamp":1234567890}"""
      val missingLatitudeJson  = """{"skaterId":"skater-123","longitude":-0.1276,"timestamp":1234567890}"""
      val missingLongitudeJson = """{"skaterId":"skater-123","latitude":51.5074,"timestamp":1234567890}"""
      val missingTimestampJson = """{"skaterId":"skater-123","latitude":51.5074,"longitude":-0.1276}"""

      JsonCodec.locationCodec.decode(missingSkaterIdJson) shouldBe a[Left[_, _]]
      JsonCodec.locationCodec.decode(missingLatitudeJson) shouldBe a[Left[_, _]]
      JsonCodec.locationCodec.decode(missingLongitudeJson) shouldBe a[Left[_, _]]
      JsonCodec.locationCodec.decode(missingTimestampJson) shouldBe a[Left[_, _]]
    }

    "fail to decode Location JSON with invalid types" in {
      val invalidLatitudeJson =
        """{"skaterId":"skater-123","latitude":"not-a-number","longitude":-0.1276,"timestamp":1234567890}"""
      val invalidLongitudeJson =
        """{"skaterId":"skater-123","latitude":51.5074,"longitude":"not-a-number","timestamp":1234567890}"""
      val invalidTimestampJson =
        """{"skaterId":"skater-123","latitude":51.5074,"longitude":-0.1276,"timestamp":"not-a-number"}"""

      JsonCodec.locationCodec.decode(invalidLatitudeJson) shouldBe a[Left[_, _]]
      JsonCodec.locationCodec.decode(invalidLongitudeJson) shouldBe a[Left[_, _]]
      JsonCodec.locationCodec.decode(invalidTimestampJson) shouldBe a[Left[_, _]]
    }

  }

  "LocationUpdateCodec" should {

    "encode LocationUpdate to JSON" in {
      val update = LocationUpdate("event-123", "skater-456", -0.1276, 51.5074, 1234567890L)
      val json   = JsonCodec.locationUpdateCodec.encode(update)

      json should include("\"eventId\":\"event-123\"")
      json should include("\"skaterId\":\"skater-456\"")
      json should include("\"longitude\":-0.1276")
      json should include("\"latitude\":51.5074")
      json should include("\"timestamp\":1234567890")
    }

    "decode JSON to LocationUpdate" in {
      val json =
        """{"eventId":"event-123","skaterId":"skater-456","longitude":-0.1276,"latitude":51.5074,"timestamp":1234567890}"""
      val result = JsonCodec.locationUpdateCodec.decode(json)

      result shouldBe Right(LocationUpdate("event-123", "skater-456", -0.1276, 51.5074, 1234567890L))
    }

    "decode JSON to LocationUpdate with default timestamp when missing" in {
      val json   = """{"eventId":"event-123","skaterId":"skater-456","longitude":-0.1276,"latitude":51.5074}"""
      val result = JsonCodec.locationUpdateCodec.decode(json)

      result should matchPattern { case Right(LocationUpdate("event-123", "skater-456", -0.1276, 51.5074, _)) =>
      }

      result.map(_.timestamp) should matchPattern {
        case Right(timestamp: Long) if timestamp > 0L =>
      }
    }

    "round-trip encode/decode LocationUpdate" in {
      val original = LocationUpdate("event-789", "skater-123", 2.3490, 45.4215, 9876543210L)
      val encoded  = JsonCodec.locationUpdateCodec.encode(original)
      val decoded  = JsonCodec.locationUpdateCodec.decode(encoded)

      decoded shouldBe Right(original)
    }

    "handle LocationUpdate JSON with different whitespace" in {
      val json =
        """{ "eventId" : "event-999" , "skaterId" : "skater-888" , "longitude" : -74.0060 , "latitude" : 40.7128 , "timestamp" : 5555555555 }"""
      val result = JsonCodec.locationUpdateCodec.decode(json)

      result shouldBe Right(LocationUpdate("event-999", "skater-888", -74.0060, 40.7128, 5555555555L))
    }

    "fail to decode LocationUpdate JSON with missing required fields" in {
      val missingEventIdJson =
        """{"skaterId":"skater-456","longitude":-0.1276,"latitude":51.5074,"timestamp":1234567890}"""
      val missingSkaterIdJson =
        """{"eventId":"event-123","longitude":-0.1276,"latitude":51.5074,"timestamp":1234567890}"""
      val missingLongitudeJson =
        """{"eventId":"event-123","skaterId":"skater-456","latitude":51.5074,"timestamp":1234567890}"""
      val missingLatitudeJson =
        """{"eventId":"event-123","skaterId":"skater-456","longitude":-0.1276,"timestamp":1234567890}"""

      JsonCodec.locationUpdateCodec.decode(missingEventIdJson) shouldBe a[Left[_, _]]
      JsonCodec.locationUpdateCodec.decode(missingSkaterIdJson) shouldBe a[Left[_, _]]
      JsonCodec.locationUpdateCodec.decode(missingLongitudeJson) shouldBe a[Left[_, _]]
      JsonCodec.locationUpdateCodec.decode(missingLatitudeJson) shouldBe a[Left[_, _]]
    }

    "fail to decode LocationUpdate JSON with invalid types" in {
      val invalidLongitudeJson =
        """{"eventId":"event-123","skaterId":"skater-456","longitude":"not-a-number","latitude":51.5074,"timestamp":1234567890}"""
      val invalidLatitudeJson =
        """{"eventId":"event-123","skaterId":"skater-456","longitude":-0.1276,"latitude":"not-a-number","timestamp":1234567890}"""
      val invalidTimestampJson =
        """{"eventId":"event-123","skaterId":"skater-456","longitude":-0.1276,"latitude":51.5074,"timestamp":"not-a-number"}"""

      JsonCodec.locationUpdateCodec.decode(invalidLongitudeJson) shouldBe a[Left[_, _]]
      JsonCodec.locationUpdateCodec.decode(invalidLatitudeJson) shouldBe a[Left[_, _]]
      JsonCodec.locationUpdateCodec.decode(invalidTimestampJson) shouldBe a[Left[_, _]]
    }

    "handle coordinate precision correctly" in {
      val highPrecisionUpdate =
        LocationUpdate("event-precision", "skater-precision", -0.123456789, 51.987654321, 1111111111L)
      val encoded = JsonCodec.locationUpdateCodec.encode(highPrecisionUpdate)
      val decoded = JsonCodec.locationUpdateCodec.decode(encoded)

      decoded shouldBe Right(highPrecisionUpdate)
    }

    "create LocationUpdate with default timestamp via case class constructor" in {
      val update = LocationUpdate("event-default", "skater-default", -1.0, 2.0)
      update.eventId shouldBe "event-default"
      update.skaterId shouldBe "skater-default"
      update.longitude shouldBe -1.0
      update.latitude shouldBe 2.0
      update.timestamp should be > 0L
    }

    "ensure TestErrorWithMixedTypes details map is fully covered" in {
      val error   = TestErrorWithMixedTypes()
      val details = error.details
      details should be(defined)

      val Some(map) = details
      map.size shouldBe 4
      map.keys should contain allOf ("stringField", "doubleField", "intField", "boolField")
      map.values should contain allOf ("test string", 42.5, 123, true)
    }
  }
}
