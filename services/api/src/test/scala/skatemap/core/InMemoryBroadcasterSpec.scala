package skatemap.core

import org.apache.pekko.actor.ActorSystem
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import skatemap.domain.Location
import skatemap.test.TestClock

import scala.concurrent.duration.DurationInt

class InMemoryBroadcasterSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem("test")

  override def afterAll(): Unit =
    system.terminate()

  private def transferHubs(from: InMemoryBroadcaster, to: InMemoryBroadcaster): Unit =
    from.hubs.foreachEntry { (key, hub) =>
      to.hubs.put(key, to.HubData(hub.sink, hub.source, hub.killSwitch, hub.lastAccessed))
    }

  private val defaultConfig = HubConfig(
    ttl = 300.seconds,
    cleanupInterval = 60.seconds,
    bufferSize = 128
  )

  "InMemoryBroadcaster" should {

    "track last accessed timestamp when hub is created" in {
      val fixedTime   = 1234567890000L
      val clock       = TestClock.fixed(fixedTime)
      val broadcaster = new InMemoryBroadcaster(system, clock, defaultConfig)
      val eventId     = "550e8400-e29b-41d4-a716-446655440000"

      broadcaster.publish(eventId, Location("550e8400-e29b-41d4-a716-446655440100", 1.0, 2.0, fixedTime))

      broadcaster.hubs.contains(eventId) should be(true)
      broadcaster.hubs(eventId).lastAccessed.get() should be(fixedTime)
    }

    "update last accessed timestamp on publish" in {
      val initialTime = 1000000000000L
      val laterTime   = 2000000000000L
      val clock       = TestClock.fixed(initialTime)
      val broadcaster = new InMemoryBroadcaster(system, clock, defaultConfig)
      val eventId     = "550e8400-e29b-41d4-a716-446655440000"

      broadcaster.publish(eventId, Location("550e8400-e29b-41d4-a716-446655440100", 1.0, 2.0, initialTime))
      val firstTimestamp = broadcaster.hubs(eventId).lastAccessed.get()

      broadcaster.hubs(eventId).lastAccessed.set(initialTime)

      val updatedClock = TestClock.fixed(laterTime)
      val broadcaster2 = new InMemoryBroadcaster(system, updatedClock, defaultConfig)
      transferHubs(broadcaster, broadcaster2)

      broadcaster2.publish(eventId, Location("550e8400-e29b-41d4-a716-446655440101", 3.0, 4.0, laterTime))
      val secondTimestamp = broadcaster2.hubs(eventId).lastAccessed.get()

      secondTimestamp should be > firstTimestamp
    }

    "update last accessed timestamp on subscribe" in {
      val initialTime = 1000000000000L
      val laterTime   = 2000000000000L
      val clock       = TestClock.fixed(initialTime)
      val broadcaster = new InMemoryBroadcaster(system, clock, defaultConfig)
      val eventId     = "550e8400-e29b-41d4-a716-446655440000"

      broadcaster.subscribe(eventId)
      val firstTimestamp = broadcaster.hubs(eventId).lastAccessed.get()

      broadcaster.hubs(eventId).lastAccessed.set(initialTime)

      val updatedClock = TestClock.fixed(laterTime)
      val broadcaster2 = new InMemoryBroadcaster(system, updatedClock, defaultConfig)
      transferHubs(broadcaster, broadcaster2)

      broadcaster2.subscribe(eventId)
      val secondTimestamp = broadcaster2.hubs(eventId).lastAccessed.get()

      secondTimestamp should be > firstTimestamp
    }

    "cleanup unused hubs based on TTL" in {
      val fixedTime   = 1000000000000L
      val clock       = TestClock.fixed(fixedTime)
      val broadcaster = new InMemoryBroadcaster(system, clock, defaultConfig)
      val eventId1    = "550e8400-e29b-41d4-a716-446655440000"
      val eventId2    = "550e8400-e29b-41d4-a716-446655440001"

      broadcaster.publish(eventId1, Location("550e8400-e29b-41d4-a716-446655440100", 1.0, 2.0, fixedTime))
      broadcaster.publish(eventId2, Location("550e8400-e29b-41d4-a716-446655440101", 3.0, 4.0, fixedTime))

      broadcaster.hubs.size should be(2)

      val ttlMillis    = 5000L
      val laterTime    = fixedTime + ttlMillis + 1000L
      val laterClock   = TestClock.fixed(laterTime)
      val broadcaster2 = new InMemoryBroadcaster(system, laterClock, defaultConfig)

      transferHubs(broadcaster, broadcaster2)

      val removed = broadcaster2.cleanupUnusedHubs(ttlMillis)

      removed should be(2)
      broadcaster2.hubs.size should be(0)
    }

    "not cleanup recently accessed hubs" in {
      val fixedTime   = 1000000000000L
      val clock       = TestClock.fixed(fixedTime)
      val broadcaster = new InMemoryBroadcaster(system, clock, defaultConfig)
      val eventId     = "550e8400-e29b-41d4-a716-446655440000"

      broadcaster.publish(eventId, Location("550e8400-e29b-41d4-a716-446655440100", 1.0, 2.0, fixedTime))

      val ttlMillis    = 5000L
      val laterTime    = fixedTime + ttlMillis - 1000L
      val laterClock   = TestClock.fixed(laterTime)
      val broadcaster2 = new InMemoryBroadcaster(system, laterClock, defaultConfig)

      transferHubs(broadcaster, broadcaster2)

      val removed = broadcaster2.cleanupUnusedHubs(ttlMillis)

      removed should be(0)
      broadcaster2.hubs.size should be(1)
    }

    "return count of removed hubs" in {
      val fixedTime   = 1000000000000L
      val clock       = TestClock.fixed(fixedTime)
      val broadcaster = new InMemoryBroadcaster(system, clock, defaultConfig)

      val eventIds = (1 to 5).map(i => s"550e8400-e29b-41d4-a716-44665544000${i.toString}")
      eventIds.zipWithIndex.foreach { case (eventId, index) =>
        val skaterId = s"550e8400-e29b-41d4-a716-44665544010${index.toString}"
        broadcaster.publish(eventId, Location(skaterId, 1.0, 2.0, fixedTime))
      }

      broadcaster.hubs.size should be(5)

      val ttlMillis    = 1000L
      val laterTime    = fixedTime + ttlMillis + 1L
      val laterClock   = TestClock.fixed(laterTime)
      val broadcaster2 = new InMemoryBroadcaster(system, laterClock, defaultConfig)

      transferHubs(broadcaster, broadcaster2)

      val removed = broadcaster2.cleanupUnusedHubs(ttlMillis)

      removed should be(5)
      broadcaster2.hubs.size should be(0)
    }

    "shutdown KillSwitch when cleaning up hubs" in {
      val fixedTime   = 1000000000000L
      val clock       = TestClock.fixed(fixedTime)
      val broadcaster = new InMemoryBroadcaster(system, clock, defaultConfig)
      val eventId     = "550e8400-e29b-41d4-a716-446655440000"

      broadcaster.publish(eventId, Location("550e8400-e29b-41d4-a716-446655440100", 1.0, 2.0, fixedTime))

      broadcaster.hubs.contains(eventId) should be(true)

      val ttlMillis    = 1000L
      val laterTime    = fixedTime + ttlMillis + 1L
      val laterClock   = TestClock.fixed(laterTime)
      val broadcaster2 = new InMemoryBroadcaster(system, laterClock, defaultConfig)

      transferHubs(broadcaster, broadcaster2)

      broadcaster2.cleanupUnusedHubs(ttlMillis)

      broadcaster2.hubs.contains(eventId) should be(false)
    }
  }
}
