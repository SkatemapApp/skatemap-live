package adapters.play

import core.ValidationError
import play.api.libs.json._
import play.api.mvc.Result
import play.api.mvc.Results.BadRequest

object ValidationErrorAdapter {

  def toJsonResponse(error: ValidationError): Result = {
    val baseJson = Json.obj(
      "error"   -> error.code,
      "message" -> error.message
    )

    val jsonWithDetails = error.details match {
      case Some(details) =>
        val detailsJson = Json.obj(
          details.map { case (key, value) =>
            val jsValue: Json.JsValueWrapper = value match {
              case s: String  => s
              case d: Double  => d
              case i: Int     => i
              case b: Boolean => b
              case _          => value.toString
            }
            key -> jsValue
          }.toSeq: _*
        )
        baseJson + ("details" -> detailsJson)
      case None => baseJson
    }

    BadRequest(jsonWithDetails)
  }
}
