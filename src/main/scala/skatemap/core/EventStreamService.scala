package skatemap.core

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import play.api.libs.json.{Json, Writes}
import skatemap.domain.Location

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration._

@Singleton
class EventStreamService @Inject() (
  store: LocationStore,
  broadcaster: Broadcaster
) {

  implicit val locationWrites: Writes[Location]           = Json.writes[Location]
  implicit val locationBatchWrites: Writes[LocationBatch] = Json.writes[LocationBatch]

  def createEventStream(eventId: String): Source[String, NotUsed] = {
    val initial: Source[Location, NotUsed] =
      Source(store.getAll(eventId).values.toList)

    val updates: Source[Location, NotUsed] =
      broadcaster.subscribe(eventId)

    (initial ++ updates)
      .groupedWithin(100, 500.millis)
      .map(batch => LocationBatch(batch.toList))
      .map(batch => Json.toJson(batch).toString)
  }
}

final case class LocationBatch(locations: List[Location])
