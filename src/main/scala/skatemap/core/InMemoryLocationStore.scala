package skatemap.core

import skatemap.domain.Location

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._

@Singleton
class InMemoryLocationStore @Inject() (clock: Clock) extends LocationStore {
  private val store: TrieMap[String, TrieMap[String, (Location, Instant)]] = TrieMap.empty
  private val maxAge                                                       = 30.seconds

  def put(eventId: String, location: Location): Unit = {
    val eventMap  = store.getOrElseUpdate(eventId, TrieMap.empty)
    val timestamp = Instant.ofEpochMilli(location.timestamp)
    eventMap.put(location.skaterId, (location, timestamp))
  }

  def getAll(eventId: String): Map[String, Location] =
    store.get(eventId) match {
      case Some(eventMap) => eventMap.map { case (skaterId, (location, _)) => skaterId -> location }.toMap
      case None           => Map.empty
    }

  def cleanup(): Unit = {
    val cutoff = clock.instant().minusSeconds(maxAge.toSeconds)

    store.foreachEntry { case (eventId, eventMap) =>
      eventMap.filterInPlace { case (_, (_, timestamp)) => timestamp.isAfter(cutoff) }

      if (eventMap.isEmpty) { store.remove(eventId) }
    }
  }

  def cleanupAll(): Int = {
    val cutoff       = clock.instant().minusSeconds(maxAge.toSeconds)
    var removedCount = 0

    store.foreachEntry { case (eventId, eventMap) =>
      val sizeBefore = eventMap.size
      eventMap.filterInPlace { case (_, (_, timestamp)) => timestamp.isAfter(cutoff) }
      removedCount += sizeBefore - eventMap.size

      if (eventMap.isEmpty) { store.remove(eventId) }
    }

    removedCount
  }
}
