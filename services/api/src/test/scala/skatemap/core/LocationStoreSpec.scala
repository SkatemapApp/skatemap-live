package skatemap.core

import skatemap.domain.Location
import skatemap.test.TestClock
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._

class LocationStoreSpec extends AnyWordSpec with Matchers {

  private val fixedClock = TestClock.fixed(50000L)
  private val config     = LocationConfig(ttl = 30.seconds)

  "InMemoryLocationStore" should {

    "store and retrieve single location" in {
      val store    = new InMemoryLocationStore(fixedClock, config)
      val eventId  = "event-1"
      val location = Location("skater-1", 45.0, -122.0, 1000L)

      store.put(eventId, location)
      val retrieved = store.getAll(eventId)

      retrieved should contain("skater-1" -> location)
    }

    "return empty map for non-existent event" in {
      val store = new InMemoryLocationStore(fixedClock, config)

      val retrieved = store.getAll("non-existent")

      retrieved shouldBe empty
    }

    "update existing skater location" in {
      val store     = new InMemoryLocationStore(fixedClock, config)
      val eventId   = "event-1"
      val skaterId  = "skater-1"
      val location1 = Location(skaterId, 45.0, -122.0, 1000L)
      val location2 = Location(skaterId, 46.0, -123.0, 2000L)

      store.put(eventId, location1)
      store.put(eventId, location2)

      val retrieved = store.getAll(eventId)
      retrieved should contain only (skaterId -> location2)
    }

    "isolate locations between different events" in {
      val store     = new InMemoryLocationStore(fixedClock, config)
      val location1 = Location("skater-1", 45.0, -122.0, 1000L)
      val location2 = Location("skater-2", 46.0, -123.0, 1000L)

      store.put("event-1", location1)
      store.put("event-2", location2)

      val event1Locations = store.getAll("event-1")
      val event2Locations = store.getAll("event-2")

      event1Locations should contain only ("skater-1" -> location1)
      event2Locations should contain only ("skater-2" -> location2)
    }

    "cleanup locations older than 30 seconds" in {
      val store           = new InMemoryLocationStore(fixedClock, config)
      val eventId         = "event-1"
      val oldTimestamp    = 10000L
      val recentTimestamp = 40000L

      val oldLocation    = Location("skater-old", 45.0, -122.0, oldTimestamp)
      val recentLocation = Location("skater-recent", 46.0, -123.0, recentTimestamp)

      store.put(eventId, oldLocation)
      store.put(eventId, recentLocation)

      store.cleanup()

      val remaining = store.getAll(eventId)
      remaining should contain only ("skater-recent" -> recentLocation)
    }

    "remove empty events after cleanup" in {
      val store        = new InMemoryLocationStore(fixedClock, config)
      val eventId      = "event-1"
      val oldTimestamp = 10000L
      val oldLocation  = Location("skater-old", 45.0, -122.0, oldTimestamp)

      store.put(eventId, oldLocation)
      store.cleanup()

      val remaining = store.getAll(eventId)
      remaining shouldBe empty
    }

    "cleanupAll should remove stale locations from all events" in {
      val store           = new InMemoryLocationStore(fixedClock, config)
      val event1          = "event-1"
      val event2          = "event-2"
      val oldTimestamp    = 10000L
      val recentTimestamp = 40000L

      val oldLocation    = Location("skater-old", 45.0, -122.0, oldTimestamp)
      val recentLocation = Location("skater-recent", 46.0, -123.0, recentTimestamp)

      store.put(event1, oldLocation)
      store.put(event1, recentLocation)
      store.put(event2, oldLocation)

      store.cleanupAll()

      val event1Remaining = store.getAll(event1)
      val event2Remaining = store.getAll(event2)

      event1Remaining should contain only ("skater-recent" -> recentLocation)
      event2Remaining shouldBe empty
    }

  }
}
