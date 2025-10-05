package skatemap.core

import org.apache.pekko.actor.ActorSystem
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import skatemap.domain.Location

import java.time.{Clock, Instant, ZoneId}
import java.util.UUID

class InMemoryBroadcasterSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem("test")

  override def afterAll(): Unit =
    system.terminate()

  "InMemoryBroadcaster" should {

    "track last accessed timestamp when hub is created" in {
      val fixedTime   = 1234567890000L
      val clock       = Clock.fixed(Instant.ofEpochMilli(fixedTime), ZoneId.systemDefault())
      val broadcaster = new InMemoryBroadcaster(system, clock)
      val eventId     = UUID.randomUUID().toString

      broadcaster.publish(eventId, Location(UUID.randomUUID().toString, 1.0, 2.0, fixedTime))

      broadcaster.hubs.contains(eventId) should be(true)
      broadcaster.hubs(eventId).lastAccessed.get() should be(fixedTime)
    }

    "update last accessed timestamp on publish" in {
      val initialTime = 1000000000000L
      val laterTime   = 2000000000000L
      val clock       = Clock.fixed(Instant.ofEpochMilli(initialTime), ZoneId.systemDefault())
      val broadcaster = new InMemoryBroadcaster(system, clock)
      val eventId     = UUID.randomUUID().toString

      broadcaster.publish(eventId, Location(UUID.randomUUID().toString, 1.0, 2.0, initialTime))
      val firstTimestamp = broadcaster.hubs(eventId).lastAccessed.get()

      broadcaster.hubs(eventId).lastAccessed.set(initialTime)

      val updatedClock = Clock.fixed(Instant.ofEpochMilli(laterTime), ZoneId.systemDefault())
      val broadcaster2 = new InMemoryBroadcaster(system, updatedClock)
      val hub          = broadcaster.hubs(eventId)
      broadcaster2.hubs.put(eventId, broadcaster2.HubData(hub.sink, hub.source, hub.lastAccessed))

      broadcaster2.publish(eventId, Location(UUID.randomUUID().toString, 3.0, 4.0, laterTime))
      val secondTimestamp = broadcaster2.hubs(eventId).lastAccessed.get()

      secondTimestamp should be > firstTimestamp
    }

    "update last accessed timestamp on subscribe" in {
      val initialTime = 1000000000000L
      val laterTime   = 2000000000000L
      val clock       = Clock.fixed(Instant.ofEpochMilli(initialTime), ZoneId.systemDefault())
      val broadcaster = new InMemoryBroadcaster(system, clock)
      val eventId     = UUID.randomUUID().toString

      broadcaster.subscribe(eventId)
      val firstTimestamp = broadcaster.hubs(eventId).lastAccessed.get()

      broadcaster.hubs(eventId).lastAccessed.set(initialTime)

      val updatedClock = Clock.fixed(Instant.ofEpochMilli(laterTime), ZoneId.systemDefault())
      val broadcaster2 = new InMemoryBroadcaster(system, updatedClock)
      val hub          = broadcaster.hubs(eventId)
      broadcaster2.hubs.put(eventId, broadcaster2.HubData(hub.sink, hub.source, hub.lastAccessed))

      broadcaster2.subscribe(eventId)
      val secondTimestamp = broadcaster2.hubs(eventId).lastAccessed.get()

      secondTimestamp should be > firstTimestamp
    }

    "cleanup unused hubs based on TTL" in {
      val fixedTime   = 1000000000000L
      val clock       = Clock.fixed(Instant.ofEpochMilli(fixedTime), ZoneId.systemDefault())
      val broadcaster = new InMemoryBroadcaster(system, clock)
      val eventId1    = UUID.randomUUID().toString
      val eventId2    = UUID.randomUUID().toString

      broadcaster.publish(eventId1, Location(UUID.randomUUID().toString, 1.0, 2.0, fixedTime))
      broadcaster.publish(eventId2, Location(UUID.randomUUID().toString, 3.0, 4.0, fixedTime))

      broadcaster.hubs.size should be(2)

      val ttlMillis    = 5000L
      val laterTime    = fixedTime + ttlMillis + 1000L
      val laterClock   = Clock.fixed(Instant.ofEpochMilli(laterTime), ZoneId.systemDefault())
      val broadcaster2 = new InMemoryBroadcaster(system, laterClock)

      broadcaster.hubs.foreachEntry { (key, hub) =>
        broadcaster2.hubs.put(key, broadcaster2.HubData(hub.sink, hub.source, hub.lastAccessed))
      }

      val removed = broadcaster2.cleanupUnusedHubs(ttlMillis)

      removed should be(2)
      broadcaster2.hubs.size should be(0)
    }

    "not cleanup recently accessed hubs" in {
      val fixedTime   = 1000000000000L
      val clock       = Clock.fixed(Instant.ofEpochMilli(fixedTime), ZoneId.systemDefault())
      val broadcaster = new InMemoryBroadcaster(system, clock)
      val eventId     = UUID.randomUUID().toString

      broadcaster.publish(eventId, Location(UUID.randomUUID().toString, 1.0, 2.0, fixedTime))

      val ttlMillis    = 5000L
      val laterTime    = fixedTime + ttlMillis - 1000L
      val laterClock   = Clock.fixed(Instant.ofEpochMilli(laterTime), ZoneId.systemDefault())
      val broadcaster2 = new InMemoryBroadcaster(system, laterClock)

      val hub = broadcaster.hubs(eventId)
      broadcaster2.hubs.put(eventId, broadcaster2.HubData(hub.sink, hub.source, hub.lastAccessed))

      val removed = broadcaster2.cleanupUnusedHubs(ttlMillis)

      removed should be(0)
      broadcaster2.hubs.size should be(1)
    }

    "return count of removed hubs" in {
      val fixedTime   = 1000000000000L
      val clock       = Clock.fixed(Instant.ofEpochMilli(fixedTime), ZoneId.systemDefault())
      val broadcaster = new InMemoryBroadcaster(system, clock)

      val eventIds = (1 to 5).map(_ => UUID.randomUUID().toString)
      eventIds.foreach { eventId =>
        broadcaster.publish(eventId, Location(UUID.randomUUID().toString, 1.0, 2.0, fixedTime))
      }

      broadcaster.hubs.size should be(5)

      val ttlMillis    = 1000L
      val laterTime    = fixedTime + ttlMillis + 1L
      val laterClock   = Clock.fixed(Instant.ofEpochMilli(laterTime), ZoneId.systemDefault())
      val broadcaster2 = new InMemoryBroadcaster(system, laterClock)

      broadcaster.hubs.foreachEntry { (key, hub) =>
        broadcaster2.hubs.put(key, broadcaster2.HubData(hub.sink, hub.source, hub.lastAccessed))
      }

      val removed = broadcaster2.cleanupUnusedHubs(ttlMillis)

      removed should be(5)
      broadcaster2.hubs.size should be(0)
    }
  }
}
