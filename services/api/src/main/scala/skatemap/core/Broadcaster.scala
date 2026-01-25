package skatemap.core

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import skatemap.domain.Location

import scala.concurrent.Future

trait Broadcaster {
  def publish(eventId: String, location: Location): Future[Unit]
  def subscribe(eventId: String): Source[Location, NotUsed]
}
