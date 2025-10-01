package skatemap.api

import org.slf4j.{Logger, LoggerFactory, MDC}
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents}
import skatemap.core.{Broadcaster, LocationStore, LocationValidator}
import skatemap.domain.Location

import javax.inject.{Inject, Singleton}

@Singleton
class LocationController @Inject() (
  val controllerComponents: ControllerComponents,
  store: LocationStore,
  broadcaster: Broadcaster
) extends BaseController {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private object MdcKeys {
    val EventId  = "eventId"
    val SkaterId = "skaterId"
    val Action   = "action"
  }

  def updateLocation(skatingEventId: String, skaterId: String): Action[AnyContent] =
    Action { implicit request =>
      MDC.put(MdcKeys.EventId, skatingEventId)
      MDC.put(MdcKeys.SkaterId, skaterId)
      MDC.put(MdcKeys.Action, "updateLocation")

      try {
        logger.info("Received location update request for event={}, skater={}", skatingEventId, skaterId)

        val coordinates = request.body.asJson.flatMap(json => (json \ "coordinates").asOpt[Array[Double]])

        LocationValidator.validate(skatingEventId, skaterId, coordinates, System.currentTimeMillis) match {
          case Left(error) =>
            logger.warn("Validation failed: {} - {}", error.code, error.message)
            ValidationErrorAdapter.toJsonResponse(error)
          case Right(locationUpdate) =>
            val location = Location(
              locationUpdate.skaterId,
              locationUpdate.longitude,
              locationUpdate.latitude,
              locationUpdate.timestamp
            )
            store.put(skatingEventId, location)
            broadcaster.publish(skatingEventId, location)
            Accepted
        }
      } finally {
        MDC.remove(MdcKeys.EventId)
        MDC.remove(MdcKeys.SkaterId)
        MDC.remove(MdcKeys.Action)
      }
    }
}
