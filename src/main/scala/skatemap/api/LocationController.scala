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

  def updateLocation(skatingEventId: String, skaterId: String): Action[AnyContent] =
    Action { implicit request =>
      MDC.put("eventId", skatingEventId)
      MDC.put("skaterId", skaterId)
      MDC.put("action", "updateLocation")

      try {
        logger.info(s"Received location update request for event=$skatingEventId, skater=$skaterId")

        val coordinates = request.body.asJson.flatMap(json => (json \ "coordinates").asOpt[Array[Double]])

        LocationValidator.validate(skatingEventId, skaterId, coordinates, System.currentTimeMillis) match {
          case Left(error) =>
            logger.warn(s"Validation failed: ${error.code} - ${error.message}")
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
        MDC.remove("eventId")
        MDC.remove("skaterId")
        MDC.remove("action")
      }
    }
}
