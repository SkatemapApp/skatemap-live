package skatemap.api

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
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
    logger.info("WebSocket connection established for event={}", eventId)
    val outgoing = createErrorHandledStream(eventId)
    Flow.fromSinkAndSource(Sink.ignore, outgoing)
  }

  private[api] def createErrorHandledStream(eventId: String): Source[String, NotUsed] =
    eventStreamService
      .createEventStream(eventId)
      .mapError { case ex: Throwable =>
        logger.error("Stream error for event={}: {}", eventId, ex.getMessage, ex)
        ex
      }
}
