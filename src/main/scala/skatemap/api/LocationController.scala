package skatemap.api

import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents}
import skatemap.core.LocationValidator

import javax.inject.{Inject, Singleton}

@Singleton
class LocationController @Inject() (val controllerComponents: ControllerComponents) extends BaseController {

  def updateLocation(skatingEventId: String, skaterId: String): Action[AnyContent] =
    Action { implicit request =>
      val coordinates = request.body.asJson.flatMap(json => (json \ "coordinates").asOpt[Array[Double]])

      LocationValidator.validate(skatingEventId, skaterId, coordinates, System.currentTimeMillis) match {
        case Left(error) => ValidationErrorAdapter.toJsonResponse(error)
        case Right(_)    => Accepted
      }
    }
}
