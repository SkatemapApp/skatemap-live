package skatemap.core

import skatemap.domain.Location
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.util.concurrent.{CountDownLatch, Executors}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class LocationStoreSpec extends AnyFlatSpec with Matchers {

  "LocationStore" should "store and retrieve locations by event" in {
    val store    = new InMemoryLocationStore()
    val eventId  = "event-1"
    val location = Location("skater-1", 45.0, -122.0, System.currentTimeMillis)

    store.put(eventId, location)
    val retrieved = store.getAll(eventId)

    retrieved should contain("skater-1" -> location)
  }

  it should "isolate locations between different events" in {
    val store     = new InMemoryLocationStore()
    val event1    = "event-1"
    val event2    = "event-2"
    val location1 = Location("skater-1", 45.0, -122.0, System.currentTimeMillis)
    val location2 = Location("skater-2", 46.0, -123.0, System.currentTimeMillis)

    store.put(event1, location1)
    store.put(event2, location2)

    val event1Locations = store.getAll(event1)
    val event2Locations = store.getAll(event2)

    event1Locations should contain only ("skater-1" -> location1)
    event2Locations should contain only ("skater-2" -> location2)
  }

  it should "update existing skater location in same event" in {
    val store     = new InMemoryLocationStore()
    val eventId   = "event-1"
    val skaterId  = "skater-1"
    val location1 = Location(skaterId, 45.0, -122.0, System.currentTimeMillis)
    val location2 = Location(skaterId, 46.0, -123.0, System.currentTimeMillis + 1000)

    store.put(eventId, location1)
    store.put(eventId, location2)

    val retrieved = store.getAll(eventId)
    retrieved should contain only (skaterId -> location2)
  }

  it should "return empty map for non-existent event" in {
    val store     = new InMemoryLocationStore()
    val retrieved = store.getAll("non-existent")
    retrieved shouldBe empty
  }

  it should "cleanup locations older than 30 seconds" in {
    val store           = new InMemoryLocationStore()
    val eventId         = "event-1"
    val oldTimestamp    = System.currentTimeMillis - 31000
    val recentTimestamp = System.currentTimeMillis

    val oldLocation    = Location("skater-old", 45.0, -122.0, oldTimestamp)
    val recentLocation = Location("skater-recent", 46.0, -123.0, recentTimestamp)

    store.put(eventId, oldLocation)
    store.put(eventId, recentLocation)

    store.cleanup()

    val remaining = store.getAll(eventId)
    remaining should contain only ("skater-recent" -> recentLocation)
  }

  it should "remove empty events after cleanup" in {
    val store        = new InMemoryLocationStore()
    val eventId      = "event-1"
    val oldTimestamp = System.currentTimeMillis - 31000
    val oldLocation  = Location("skater-old", 45.0, -122.0, oldTimestamp)

    store.put(eventId, oldLocation)
    store.cleanup()

    val remaining = store.getAll(eventId)
    remaining shouldBe empty
  }

  it should "handle concurrent operations safely" in {
    val store                         = new InMemoryLocationStore()
    val eventId                       = "event-1"
    val numThreads                    = 10
    val numOperationsPerThread        = 100
    val executor                      = Executors.newFixedThreadPool(numThreads)
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(executor)
    val latch                         = new CountDownLatch(numThreads)

    (1 to numThreads).foreach { threadId =>
      Future {
        try {
          (1 to numOperationsPerThread).foreach { opId =>
            val skaterId = s"skater-${threadId.toString}-${opId.toString}"
            val location = Location(
              skaterId,
              Random.nextDouble() * 180 - 90,
              Random.nextDouble() * 360 - 180,
              System.currentTimeMillis
            )
            store.put(eventId, location)
            store.getAll(eventId)
            if (opId % 10 === 0) store.cleanup()
          }
        } finally
          latch.countDown()
      }
    }

    latch.await()

    val finalLocations = store.getAll(eventId)
    finalLocations.size should be <= (numThreads * numOperationsPerThread)

    executor.shutdown()
  }

  it should "preserve recent locations during concurrent cleanup" in {
    val store          = new InMemoryLocationStore()
    val eventId        = "event-1"
    val recentLocation = Location("skater-1", 45.0, -122.0, System.currentTimeMillis)

    store.put(eventId, recentLocation)

    val numCleanupThreads             = 5
    val executor                      = Executors.newFixedThreadPool(numCleanupThreads)
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(executor)

    val cleanupFutures = (1 to numCleanupThreads).map { _ =>
      Future(store.cleanup())
    }

    Future.sequence(cleanupFutures).map { _ =>
      val remaining = store.getAll(eventId)
      remaining should contain("skater-1" -> recentLocation)
    }

    executor.shutdown()
  }
}
