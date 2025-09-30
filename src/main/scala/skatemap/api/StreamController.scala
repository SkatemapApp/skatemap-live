package skatemap.api

import org.apache.pekko.stream.scaladsl.{Flow, Sink}
import org.slf4j.{Logger, LoggerFactory}
import play.api.mvc.{BaseController, ControllerComponents, WebSocket}
import skatemap.core.EventStreamService

import javax.inject.{Inject, Singleton}

@Singleton
class StreamController @Inject() (
  val controllerComponents: ControllerComponents,
  eventStreamService: EventStreamService
) extends BaseController {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  def streamEvent(eventId: String): WebSocket = WebSocket.accept[String, String] { _ =>
    logger.info(s"WebSocket connection established for event=$eventId")
    val outgoing = eventStreamService.createEventStream(eventId)
    Flow.fromSinkAndSource(Sink.ignore, outgoing)
  }
}
