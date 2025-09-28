package skatemap.core

import skatemap.domain.{Coordinates, LocationUpdate}

object LocationValidator {

  def validate(
    eventId: String,
    skaterId: String,
    coordinates: Option[Array[Double]],
    timestamp: Long
  ): Either[ValidationError, LocationUpdate] =
    for {
      _               <- validateUUIDs(eventId, skaterId)
      coordsArray     <- coordinates.toRight(MissingCoordinatesError())
      coords          <- parseCoordinatesArray(coordsArray)
      validatedCoords <- CoordinateValidator.validateBounds(coords)
    } yield LocationUpdate(eventId, skaterId, validatedCoords.longitude, validatedCoords.latitude, timestamp)

  private def parseCoordinatesArray(array: Array[Double]): Either[ValidationError, Coordinates] =
    array match {
      case Array(longitude, latitude) => Right(Coordinates(longitude, latitude))
      case _                          => Left(InvalidCoordinatesLengthError())
    }

  private def validateUUIDs(eventId: String, skaterId: String): Either[ValidationError, Unit] =
    for {
      _ <- UuidValidator.validateEventId(eventId)
      _ <- UuidValidator.validateSkaterId(skaterId)
    } yield ()

}
