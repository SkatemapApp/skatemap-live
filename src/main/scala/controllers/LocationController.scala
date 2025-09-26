package controllers

import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents, Request}

import javax.inject.{Inject, Singleton}


@Singleton
class LocationController @Inject()(val controllerComponents: ControllerComponents) extends BaseController {

  def update(): Action[AnyContent] = Action {
    implicit request: Request[AnyContent] =>
      Ok
  }
}