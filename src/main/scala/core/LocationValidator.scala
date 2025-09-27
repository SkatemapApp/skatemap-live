package core

import core.validation.{CoordinateValidator, UuidValidator}
import core.json.CoordinateJsonParser
import scala.util.{Failure, Success, Try}

object LocationValidator {

  def validate(eventId: String, skaterId: String, coordinatesJson: String): Either[ValidationError, LocationUpdate] =
    for {
      _               <- validateUUIDs(eventId, skaterId)
      coordinates     <- CoordinateJsonParser.parseCoordinates(coordinatesJson)
      validatedCoords <- CoordinateValidator.validateBounds(coordinates)
    } yield LocationUpdate(
      eventId,
      skaterId,
      validatedCoords.longitude,
      validatedCoords.latitude,
      System.currentTimeMillis
    )

  private def validateUUIDs(eventId: String, skaterId: String): Either[ValidationError, Unit] =
    for {
      _ <- UuidValidator.validateEventId(eventId)
      _ <- UuidValidator.validateSkaterId(skaterId)
    } yield ()

}
