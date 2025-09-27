package core.json

import core.{Coordinates, InvalidCoordinatesLengthError, InvalidJsonError, MissingCoordinatesError, ValidationError}
import scala.util.Try

object CoordinateJsonParser {
  private val coordinatesPattern      = """"coordinates"\s*:\s*\[\s*([^,\]]+)\s*,\s*([^,\]]+)\s*\]""".r
  private val coordinatesArrayPattern = """"coordinates"\s*:\s*\[([^\]]*)\]""".r

  def parseCoordinates(jsonString: String): Either[ValidationError, Coordinates] = {
    val trimmed = jsonString.trim

    if (!isValidJsonStructure(trimmed)) {
      return Left(InvalidJsonError())
    }

    if (!trimmed.contains("\"coordinates\"")) {
      return Left(MissingCoordinatesError())
    }

    coordinatesPattern.findFirstMatchIn(trimmed) match {
      case Some(m) =>
        Try {
          val longitude = m.group(1).toDouble
          val latitude  = m.group(2).toDouble
          Coordinates(longitude, latitude)
        }.toEither.left.map(_ => InvalidJsonError())
      case None =>
        coordinatesArrayPattern.findFirstMatchIn(trimmed) match {
          case Some(arrayMatch) =>
            Left(InvalidCoordinatesLengthError())
          case None =>
            Left(MissingCoordinatesError())
        }
    }
  }

  private def isValidJsonStructure(json: String): Boolean =
    json.nonEmpty &&
      json.startsWith("{") &&
      json.endsWith("}") &&
      json.contains(":") &&
      json.contains("\"")
}
