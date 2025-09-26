package controllers

import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents, Request}

import javax.inject.{Inject, Singleton}


@Singleton
class LocationController @Inject()(val controllerComponents: ControllerComponents) extends BaseController {

  def updateLocation(skatingEventId: String, skaterId: String): Action[AnyContent] = {
    Action { implicit request: Request[AnyContent] =>
      Ok
    }
  }
}