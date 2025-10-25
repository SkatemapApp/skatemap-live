package skatemap.api

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import org.slf4j.{Logger, LoggerFactory}
import play.api.mvc.{BaseController, ControllerComponents, WebSocket}
import skatemap.core.{EventStreamService, UuidValidator}

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class StreamController @Inject() (
  val controllerComponents: ControllerComponents,
  eventStreamService: EventStreamService
) extends BaseController {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  def streamEvent(eventId: String): WebSocket = WebSocket.acceptOrResult[String, String] { _ =>
    UuidValidator.validateEventId(eventId) match {
      case Right(_) =>
        logger.info("WebSocket connection established for event={}", eventId)
        val outgoing = createErrorHandledStream(eventId)
        Future.successful(Right(Flow.fromSinkAndSource(Sink.ignore, outgoing)))
      case Left(error) =>
        Future.successful(Left(ValidationErrorAdapter.toJsonResponse(error)))
    }
  }

  private[api] def createErrorHandledStream(eventId: String): Source[String, NotUsed] =
    eventStreamService
      .createEventStream(eventId)
      .mapError { case ex: Throwable =>
        logger.error("Stream error for event={}: {}", eventId, ex.getMessage, ex)
        ex
      }
}
