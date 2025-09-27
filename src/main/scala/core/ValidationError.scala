package core

sealed trait ValidationError {
  def code: String
  def message: String
  def field: Option[String]             = None
  def details: Option[Map[String, Any]] = None
}

case class InvalidSkatingEventIdError() extends ValidationError {
  val code    = "INVALID_SKATING_EVENT_ID"
  val message = "Skating event ID must be a valid UUID"
}

case class InvalidSkaterIdError() extends ValidationError {
  val code    = "INVALID_SKATER_ID"
  val message = "Skater ID must be a valid UUID"
}

case class InvalidLongitudeError(value: Double) extends ValidationError {
  val code           = "INVALID_LONGITUDE"
  val message        = "Longitude must be between -180.0 and 180.0"
  override val field = Some("coordinates[0]")
  override val details = Some(
    Map(
      "field"      -> "coordinates[0]",
      "value"      -> value,
      "constraint" -> "range(-180.0, 180.0)"
    )
  )
}

case class InvalidLatitudeError(value: Double) extends ValidationError {
  val code           = "INVALID_LATITUDE"
  val message        = "Latitude must be between -90.0 and 90.0"
  override val field = Some("coordinates[1]")
  override val details = Some(
    Map(
      "field"      -> "coordinates[1]",
      "value"      -> value,
      "constraint" -> "range(-90.0, 90.0)"
    )
  )
}

case class InvalidJsonError() extends ValidationError {
  val code    = "INVALID_JSON"
  val message = "Request body must be valid JSON"
}

case class MissingCoordinatesError() extends ValidationError {
  val code    = "MISSING_COORDINATES"
  val message = "Request must contain 'coordinates' field with array of numbers"
}

case class InvalidCoordinatesLengthError() extends ValidationError {
  val code    = "INVALID_COORDINATES_LENGTH"
  val message = "Coordinates array must contain exactly 2 numbers [longitude, latitude]"
}
