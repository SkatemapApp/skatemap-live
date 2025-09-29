package skatemap.core

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import play.api.libs.json.Json
import skatemap.api.json.LocationJson._
import skatemap.domain.{Location, LocationBatch}

import java.time.Clock
import javax.inject.{Inject, Singleton}

@Singleton
class EventStreamService @Inject() (
  store: LocationStore,
  broadcaster: Broadcaster,
  config: StreamConfig,
  clock: Clock
) {

  def createEventStream(eventId: String): Source[String, NotUsed] = {
    val initial: Source[Location, NotUsed] =
      Source(store.getAll(eventId).values.toList)

    val updates: Source[Location, NotUsed] =
      broadcaster.subscribe(eventId)

    (initial ++ updates)
      .groupedWithin(config.batchSize, config.batchInterval)
      .map(batch => LocationBatch(batch.toList, clock.millis()))
      .map(batch => Json.toJson(batch).toString)
  }
}
