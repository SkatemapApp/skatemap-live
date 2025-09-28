package skatemap.core

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import skatemap.domain.Location

import javax.inject.{Inject, Singleton}
import scala.collection.concurrent.TrieMap

@Singleton
class InMemoryBroadcaster @Inject() (system: ActorSystem) extends Broadcaster {

  implicit private val actorSystem: ActorSystem = system

  private[core] val hubs = TrieMap[String, (Sink[Location, NotUsed], Source[Location, NotUsed])]()

  private def getOrCreateHub(eventId: String): (Sink[Location, NotUsed], Source[Location, NotUsed]) =
    hubs.getOrElseUpdate(
      eventId,
      MergeHub
        .source[Location]
        .toMat(BroadcastHub.sink[Location](bufferSize = 128))(Keep.both)
        .run()
    )

  def publish(eventId: String, location: Location): Unit = {
    val (sink, _) = getOrCreateHub(eventId)
    Source.single(location).runWith(sink)
  }

  def subscribe(eventId: String): Source[Location, NotUsed] = {
    val (_, source) = getOrCreateHub(eventId)
    source
  }
}
