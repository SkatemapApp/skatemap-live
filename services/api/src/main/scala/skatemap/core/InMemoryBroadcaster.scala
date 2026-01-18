package skatemap.core

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{
  KillSwitches,
  OverflowStrategy,
  QueueOfferResult,
  StreamDetachedException,
  UniqueKillSwitch
}
import org.apache.pekko.stream.scaladsl.{BroadcastHub, Keep, Source, SourceQueueWithComplete}
import org.slf4j.{Logger, LoggerFactory}
import skatemap.domain.Location

import java.time.Clock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.{Inject, Singleton}
import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InMemoryBroadcaster @Inject() (system: ActorSystem, clock: Clock, config: HubConfig) extends Broadcaster {

  implicit private val actorSystem: ActorSystem = system
  implicit private val ec: ExecutionContext     = system.dispatcher
  private val logger: Logger                    = LoggerFactory.getLogger(getClass)

  private[core] case class HubData(
    queue: SourceQueueWithComplete[Location],
    source: Source[Location, NotUsed],
    killSwitch: UniqueKillSwitch,
    lastAccessed: AtomicLong
  )

  private[core] val hubs = TrieMap[String, HubData]()

  private def getOrCreateHub(eventId: String): HubData = {
    val hubData = hubs.getOrElseUpdate(
      eventId, {
        val ((queue, killSwitch), source) = Source
          .queue[Location](config.bufferSize, OverflowStrategy.dropHead)
          .viaMat(KillSwitches.single)(Keep.both)
          .toMat(BroadcastHub.sink[Location](bufferSize = config.bufferSize))(Keep.both)
          .run()
        HubData(queue, source, killSwitch, new AtomicLong(clock.millis()))
      }
    )
    hubData.lastAccessed.set(clock.millis())
    hubData
  }

  def publish(eventId: String, location: Location): Future[Unit] = {
    val hubData = getOrCreateHub(eventId)
    hubData.queue
      .offer(location)
      .map {
        case QueueOfferResult.Enqueued    => ()
        case QueueOfferResult.Dropped     => logger.warn("Location dropped for event {} due to queue overflow", eventId)
        case QueueOfferResult.QueueClosed => logger.warn("Location dropped for event {} because queue closed", eventId)
        case QueueOfferResult.Failure(cause) => logger.error("Failed to offer location for event {}", eventId, cause)
      }
      .recover { case _: StreamDetachedException =>
        logger.error("Failed to offer location for event {}", eventId)
      }
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
      hubs.remove(key, hubData)
      hubData.killSwitch.shutdown()
    }
    toRemove.size
  }
}
