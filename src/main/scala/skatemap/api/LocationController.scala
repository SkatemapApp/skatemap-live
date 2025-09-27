package skatemap.api

import skatemap.core.{IngestService, LocationValidator}
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents}

import javax.inject.{Inject, Singleton}

@Singleton
class LocationController @Inject() (val controllerComponents: ControllerComponents) extends BaseController {

  def updateLocation(skatingEventId: String, skaterId: String): Action[AnyContent] =
    Action { implicit request =>
      val coordinates = request.body.asJson.flatMap { json =>
        (json \ "coordinates").asOpt[Array[Double]]
      }

      LocationValidator.validate(skatingEventId, skaterId, coordinates) match {
        case Left(error) => ValidationErrorAdapter.toJsonResponse(error)
        case Right(locationUpdate) =>
          IngestService.handle(locationUpdate)
          Accepted
      }
    }
}
