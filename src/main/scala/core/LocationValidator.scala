package core

import java.util.UUID
import scala.util.{Failure, Success, Try}

object LocationValidator {

  private val MIN_LONGITUDE = -180.0
  private val MAX_LONGITUDE = 180.0
  private val MIN_LATITUDE  = -90.0
  private val MAX_LATITUDE  = 90.0

  def validate(eventId: String, skaterId: String, coordinatesJson: String): Either[ValidationError, LocationUpdate] =
    for {
      _           <- validateUUIDs(eventId, skaterId)
      coordinates <- parseCoordinatesFromJson(coordinatesJson)
      _           <- validateCoordinateBounds(coordinates)
    } yield LocationUpdate(eventId, skaterId, coordinates.longitude, coordinates.latitude)

  private def validateUUIDs(eventId: String, skaterId: String): Either[ValidationError, Unit] =
    Try(UUID.fromString(eventId)) match {
      case Failure(_) => Left(InvalidSkatingEventIdError())
      case Success(_) =>
        Try(UUID.fromString(skaterId)) match {
          case Failure(_) => Left(InvalidSkaterIdError())
          case Success(_) => Right(())
        }
    }

  private def parseCoordinatesFromJson(jsonString: String): Either[ValidationError, Coordinates] =
    Try {
      val trimmed = jsonString.trim

      if (
        trimmed.isEmpty ||
        (!trimmed.startsWith("{") || !trimmed.endsWith("}")) &&
        !trimmed.startsWith("[")
      ) {
        throw new RuntimeException("INVALID_JSON")
      }

      if (trimmed.startsWith("[")) {
        throw new RuntimeException("INVALID_JSON")
      }

      if (!trimmed.contains(":") || !trimmed.contains("\"")) {
        throw new RuntimeException("INVALID_JSON")
      }

      val coordinatesPattern = """"coordinates"\s*:\s*\[\s*([^,\]]+)\s*,\s*([^,\]]+)\s*\]""".r

      coordinatesPattern.findFirstMatchIn(trimmed) match {
        case Some(m) =>
          val longitude = m.group(1).toDouble
          val latitude  = m.group(2).toDouble
          Coordinates(longitude, latitude)
        case None =>
          if (trimmed.contains("\"coordinates\"")) {
            val arrayPattern = """"coordinates"\s*:\s*\[([^\]]*)\]""".r
            arrayPattern.findFirstMatchIn(trimmed) match {
              case Some(arrayMatch) =>
                val arrayContent = arrayMatch.group(1).trim
                if (arrayContent.isEmpty || !arrayContent.contains(",")) {
                  throw new RuntimeException("INVALID_COORDINATES_LENGTH")
                } else {
                  throw new RuntimeException("INVALID_COORDINATES_LENGTH")
                }
              case None =>
                throw new RuntimeException("MISSING_COORDINATES")
            }
          } else {
            throw new RuntimeException("MISSING_COORDINATES")
          }
      }
    } match {
      case Success(coords) => Right(coords)
      case Failure(ex) =>
        ex.getMessage match {
          case "MISSING_COORDINATES"        => Left(MissingCoordinatesError())
          case "INVALID_COORDINATES_LENGTH" => Left(InvalidCoordinatesLengthError())
          case "INVALID_JSON"               => Left(InvalidJsonError())
          case _                            => Left(InvalidJsonError())
        }
    }

  private def validateCoordinateBounds(coordinates: Coordinates): Either[ValidationError, Unit] =
    if (coordinates.longitude < MIN_LONGITUDE || coordinates.longitude > MAX_LONGITUDE) {
      Left(InvalidLongitudeError(coordinates.longitude))
    } else if (coordinates.latitude < MIN_LATITUDE || coordinates.latitude > MAX_LATITUDE) {
      Left(InvalidLatitudeError(coordinates.latitude))
    } else {
      Right(())
    }
}
