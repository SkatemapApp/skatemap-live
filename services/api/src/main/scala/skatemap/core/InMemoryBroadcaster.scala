package skatemap.core

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{KillSwitches, UniqueKillSwitch}
import org.apache.pekko.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import skatemap.domain.Location

import java.time.Clock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.{Inject, Singleton}
import scala.collection.concurrent.TrieMap

@Singleton
class InMemoryBroadcaster @Inject() (system: ActorSystem, clock: Clock, config: HubConfig) extends Broadcaster {

  implicit private val actorSystem: ActorSystem = system

  private[core] case class HubData(
    sink: Sink[Location, NotUsed],
    source: Source[Location, NotUsed],
    killSwitch: UniqueKillSwitch,
    lastAccessed: AtomicLong
  )

  private[core] val hubs = TrieMap[String, HubData]()

  private def getOrCreateHub(eventId: String): HubData = {
    val hubData = hubs.getOrElseUpdate(
      eventId, {
        val ((sink, killSwitch), source) = MergeHub
          .source[Location]
          .viaMat(KillSwitches.single)(Keep.both)
          .toMat(BroadcastHub.sink[Location](bufferSize = config.bufferSize))(Keep.both)
          .run()
        HubData(sink, source, killSwitch, new AtomicLong(clock.millis()))
      }
    )
    hubData.lastAccessed.set(clock.millis())
    hubData
  }

  def publish(eventId: String, location: Location): Unit = {
    val hubData = getOrCreateHub(eventId)
    Source.single(location).runWith(hubData.sink)
  }

  def subscribe(eventId: String): Source[Location, NotUsed] = {
    val hubData = getOrCreateHub(eventId)
    hubData.source
  }

  def cleanupUnusedHubs(ttlMillis: Long): Int = {
    val now       = clock.millis()
    val threshold = now - ttlMillis
    val toRemove  = hubs.filter { case (_, hubData) => hubData.lastAccessed.get() < threshold }.toList
    toRemove.foreach { case (key, hubData) =>
      if (hubs.remove(key, hubData)) {
        hubData.killSwitch.shutdown()
      }
    }
    toRemove.size
  }
}
