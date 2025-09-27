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
      _           <- validateCoordinateBounds(coordinates._1, coordinates._2)
    } yield LocationUpdate(eventId, skaterId, coordinates._1, coordinates._2)

  private def validateUUIDs(eventId: String, skaterId: String): Either[ValidationError, Unit] =
    Try(UUID.fromString(eventId)) match {
      case Failure(_) => Left(InvalidSkatingEventIdError())
      case Success(_) =>
        Try(UUID.fromString(skaterId)) match {
          case Failure(_) => Left(InvalidSkaterIdError())
          case Success(_) => Right(())
        }
    }

  private def parseCoordinatesFromJson(jsonString: String): Either[ValidationError, (Double, Double)] =
    Try {
      val trimmed = jsonString.trim

      // First, check if it's a valid JSON structure
      if (
        trimmed.isEmpty ||
        (!trimmed.startsWith("{") || !trimmed.endsWith("}")) &&
        !trimmed.startsWith("[")
      ) {
        throw new RuntimeException("INVALID_JSON")
      }

      // If it's an array, it's invalid structure
      if (trimmed.startsWith("[")) {
        throw new RuntimeException("INVALID_JSON")
      }

      // Basic validation for JSON object structure
      if (!trimmed.contains(":") || !trimmed.contains("\"")) {
        throw new RuntimeException("INVALID_JSON")
      }

      // Look for "coordinates" field
      val coordinatesPattern = """"coordinates"\s*:\s*\[\s*([^,\]]+)\s*,\s*([^,\]]+)\s*\]""".r

      coordinatesPattern.findFirstMatchIn(trimmed) match {
        case Some(m) =>
          val longitude = m.group(1).toDouble
          val latitude  = m.group(2).toDouble
          (longitude, latitude)
        case None =>
          // Check if coordinates field exists but is malformed
          if (trimmed.contains("\"coordinates\"")) {
            // Check if it's an array with wrong length
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

  private def validateCoordinateBounds(longitude: Double, latitude: Double): Either[ValidationError, Unit] =
    if (longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
      Left(InvalidLongitudeError(longitude))
    } else if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
      Left(InvalidLatitudeError(latitude))
    } else {
      Right(())
    }
}
