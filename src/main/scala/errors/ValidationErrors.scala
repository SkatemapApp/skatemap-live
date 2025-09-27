package errors

import play.api.libs.json._
import play.api.mvc.Result
import play.api.mvc.Results.BadRequest

sealed trait ValidationError {
  def toJsonResponse: Result
}

case class InvalidSkatingEventIdError() extends ValidationError {
  def toJsonResponse: Result = BadRequest(
    Json.obj(
      "error"   -> "INVALID_SKATING_EVENT_ID",
      "message" -> "Skating event ID must be a valid UUID"
    )
  )
}

case class InvalidSkaterIdError() extends ValidationError {
  def toJsonResponse: Result = BadRequest(
    Json.obj(
      "error"   -> "INVALID_SKATER_ID",
      "message" -> "Skater ID must be a valid UUID"
    )
  )
}

case class InvalidLongitudeError(value: Double) extends ValidationError {
  def toJsonResponse: Result = BadRequest(
    Json.obj(
      "error"   -> "INVALID_LONGITUDE",
      "message" -> "Longitude must be between -180.0 and 180.0",
      "details" -> Json.obj(
        "field"      -> "coordinates[0]",
        "value"      -> value,
        "constraint" -> "range(-180.0, 180.0)"
      )
    )
  )
}

case class InvalidLatitudeError(value: Double) extends ValidationError {
  def toJsonResponse: Result = BadRequest(
    Json.obj(
      "error"   -> "INVALID_LATITUDE",
      "message" -> "Latitude must be between -90.0 and 90.0",
      "details" -> Json.obj(
        "field"      -> "coordinates[1]",
        "value"      -> value,
        "constraint" -> "range(-90.0, 90.0)"
      )
    )
  )
}

case class InvalidJsonError() extends ValidationError {
  def toJsonResponse: Result = BadRequest(
    Json.obj(
      "error"   -> "INVALID_JSON",
      "message" -> "Request body must be valid JSON"
    )
  )
}

case class MissingCoordinatesError() extends ValidationError {
  def toJsonResponse: Result = BadRequest(
    Json.obj(
      "error"   -> "MISSING_COORDINATES",
      "message" -> "Request must contain 'coordinates' field with array of numbers"
    )
  )
}

case class InvalidCoordinatesLengthError() extends ValidationError {
  def toJsonResponse: Result = BadRequest(
    Json.obj(
      "error"   -> "INVALID_COORDINATES_LENGTH",
      "message" -> "Coordinates array must contain exactly 2 numbers [longitude, latitude]"
    )
  )
}
