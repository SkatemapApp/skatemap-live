package controllers.play

import adapters.play.ValidationErrorAdapter
import core.LocationValidator
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents}

import javax.inject.{Inject, Singleton}

@Singleton
class LocationController @Inject() (
  val controllerComponents: ControllerComponents
) extends BaseController {

  def updateLocation(skatingEventId: String, skaterId: String): Action[AnyContent] =
    Action { implicit request =>
      // Extract JSON body as string
      val jsonString = request.body.asJson match {
        case Some(json) => json.toString()
        case None       => ""
      }

      // Use core validator
      LocationValidator.validate(skatingEventId, skaterId, jsonString) match {
        case Left(error) => ValidationErrorAdapter.toJsonResponse(error)
        case Right(_)    => Accepted
      }
    }
}
