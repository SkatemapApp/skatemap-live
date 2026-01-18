package skatemap.test

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import skatemap.core.Broadcaster
import skatemap.domain.Location

import scala.concurrent.Future

class StubBroadcaster extends Broadcaster {
  def publish(eventId: String, location: Location): Future[Unit] = Future.successful(())
  def subscribe(eventId: String): Source[Location, NotUsed]      = Source.empty
}
