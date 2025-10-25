package skatemap.test

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import skatemap.core.Broadcaster
import skatemap.domain.Location

class StubBroadcaster extends Broadcaster {
  def publish(eventId: String, location: Location): Unit    = ()
  def subscribe(eventId: String): Source[Location, NotUsed] = Source.empty
}
