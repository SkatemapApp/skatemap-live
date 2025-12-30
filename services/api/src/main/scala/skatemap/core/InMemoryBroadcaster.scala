package skatemap.core

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{BoundedSourceQueue, KillSwitches, QueueOfferResult, UniqueKillSwitch}
import org.apache.pekko.stream.scaladsl.{BroadcastHub, Keep, Source}
import org.slf4j.{Logger, LoggerFactory}
import skatemap.domain.Location

import java.time.Clock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.{Inject, Singleton}
import scala.collection.concurrent.TrieMap

@Singleton
class InMemoryBroadcaster @Inject() (system: ActorSystem, clock: Clock, config: HubConfig) extends Broadcaster {

  implicit private val actorSystem: ActorSystem = system
  private val logger: Logger                    = LoggerFactory.getLogger(getClass)

  private[core] case class HubData(
    queue: BoundedSourceQueue[Location],
    source: Source[Location, NotUsed],
    killSwitch: UniqueKillSwitch,
    lastAccessed: AtomicLong
  )

  private[core] val hubs = TrieMap[String, HubData]()

  private def getOrCreateHub(eventId: String): HubData = {
    val hubData = hubs.getOrElseUpdate(
      eventId, {
        val ((queue, killSwitch), source) = Source
          .queue[Location](config.bufferSize)
          .viaMat(KillSwitches.single)(Keep.both)
          .toMat(BroadcastHub.sink[Location](bufferSize = config.bufferSize))(Keep.both)
          .run()
        HubData(queue, source, killSwitch, new AtomicLong(clock.millis()))
      }
    )
    hubData.lastAccessed.set(clock.millis())
    hubData
  }

  def publish(eventId: String, location: Location): Unit = {
    val hubData = getOrCreateHub(eventId)
    hubData.queue.offer(location) match {
      case QueueOfferResult.Dropped     => logger.warn("Location dropped for event {} due to queue overflow", eventId)
      case QueueOfferResult.QueueClosed => logger.warn("Location dropped for event {} because queue closed", eventId)
      case _                            => ()
    }
  }

  def subscribe(eventId: String): Source[Location, NotUsed] = {
    val hubData = getOrCreateHub(eventId)
    hubData.source
  }

  def cleanupUnusedHubs(ttlMillis: Long): Int = {
    val now       = clock.millis()
    val threshold = now - ttlMillis
    val toRemove  = hubs.filter { case (_, hubData) => hubData.lastAccessed.get() < threshold }.keys.toList
    toRemove.foreach { key =>
      hubs.get(key).foreach(_.killSwitch.shutdown())
      hubs.remove(key)
    }
    toRemove.size
  }
}
