package skatemap.api

import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents}

import javax.inject.{Inject, Singleton}

@Singleton
class HealthController @Inject() (
  val controllerComponents: ControllerComponents
) extends BaseController {

  def health: Action[AnyContent] = Action {
    Ok
  }
}
