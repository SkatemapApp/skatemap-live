package controllers

import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents}
import services.LocationValidationService

import javax.inject.{Inject, Singleton}

@Singleton
class LocationController @Inject() (
  val controllerComponents: ControllerComponents,
  locationValidationService: LocationValidationService
) extends BaseController {

  def updateLocation(skatingEventId: String, skaterId: String): Action[AnyContent] =
    Action { implicit request =>
      locationValidationService.validateLocationUpdateRequest(skatingEventId, skaterId, request) match {
        case Left(error) => error.toJsonResponse
        case Right(_)    => Ok
      }
    }
}
