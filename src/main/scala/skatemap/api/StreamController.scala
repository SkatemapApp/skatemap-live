package skatemap.api

import org.apache.pekko.stream.scaladsl.{Flow, Sink}
import play.api.mvc.{BaseController, ControllerComponents, WebSocket}
import skatemap.core.EventStreamService

import javax.inject.{Inject, Singleton}

@Singleton
class StreamController @Inject() (
  val controllerComponents: ControllerComponents,
  eventStreamService: EventStreamService
) extends BaseController {

  def streamEvent(eventId: String): WebSocket = WebSocket.accept[String, String] { _ =>
    val outgoing = eventStreamService.createEventStream(eventId)
    Flow.fromSinkAndSource(Sink.ignore, outgoing)
  }
}
