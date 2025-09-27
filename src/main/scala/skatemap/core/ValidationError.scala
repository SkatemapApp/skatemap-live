package skatemap.core

sealed trait ValidationError {
  def code: String

  def message: String

  def field: Option[String] = None

  def details: Option[Map[String, Any]] = None
}

final case class InvalidSkatingEventIdError() extends ValidationError {
  val code    = "INVALID_SKATING_EVENT_ID"
  val message = "Skating event ID must be a valid UUID"
}

final case class InvalidSkaterIdError() extends ValidationError {
  val code    = "INVALID_SKATER_ID"
  val message = "Skater ID must be a valid UUID"
}

final case class InvalidLongitudeError(value: Double) extends ValidationError {
  val code                           = "INVALID_LONGITUDE"
  val message                        = "Longitude must be between -180.0 and 180.0"
  override val field: Option[String] = Some("coordinates[0]")
  override val details: Option[Map[String, Any]] = Some(
    Map(
      "field"      -> "coordinates[0]",
      "value"      -> value,
      "constraint" -> "range(-180.0, 180.0)"
    )
  )
}

final case class InvalidLatitudeError(value: Double) extends ValidationError {
  val code                           = "INVALID_LATITUDE"
  val message                        = "Latitude must be between -90.0 and 90.0"
  override val field: Option[String] = Some("coordinates[1]")
  override val details: Option[Map[String, Any]] = Some(
    Map(
      "field"      -> "coordinates[1]",
      "value"      -> value,
      "constraint" -> "range(-90.0, 90.0)"
    )
  )
}

final case class InvalidJsonError() extends ValidationError {
  val code    = "INVALID_JSON"
  val message = "Request body must be valid JSON"
}

final case class MissingCoordinatesError() extends ValidationError {
  val code    = "MISSING_COORDINATES"
  val message = "Request must contain 'coordinates' field with array of numbers"
}

final case class InvalidCoordinatesLengthError() extends ValidationError {
  val code    = "INVALID_COORDINATES_LENGTH"
  val message = "Coordinates array must contain exactly 2 numbers [longitude, latitude]"
}

final case class TestErrorWithMixedTypes() extends ValidationError {
  val code    = "TEST_ERROR"
  val message = "Test error with mixed types"
  override val details: Option[Map[String, Any]] = Some(
    Map(
      "stringField" -> "test string",
      "doubleField" -> 42.5,
      "intField"    -> 123,
      "boolField"   -> true,
      "longField"   -> 999L,
      "floatField"  -> 3.14f
    )
  )
}
