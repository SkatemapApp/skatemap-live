package services

import errors._
import models.Coordinates
import play.api.mvc.{AnyContent, Request}
import validation.ValidationConstants

import java.util.UUID
import scala.util.{Failure, Success, Try}

class LocationValidationService {

  import ValidationConstants._

  private def validateUUIDs(skatingEventId: String, skaterId: String): Either[ValidationError, Unit] =
    Try(UUID.fromString(skatingEventId)) match {
      case Failure(_) => Left(InvalidSkatingEventIdError())
      case Success(_) =>
        Try(UUID.fromString(skaterId)) match {
          case Failure(_) => Left(InvalidSkaterIdError())
          case Success(_) => Right(())
        }
    }

  private def parseCoordinates(request: Request[AnyContent]): Either[ValidationError, Coordinates] =
    request.body.asJson match {
      case None => Left(InvalidJsonError())
      case Some(json) =>
        (json \ "coordinates").asOpt[List[Double]] match {
          case None                               => Left(MissingCoordinatesError())
          case Some(coords) if coords.length != 2 => Left(InvalidCoordinatesLengthError())
          case Some(coords)                       => Right(Coordinates(coords(0), coords(1)))
        }
    }

  private def validateCoordinates(coordinates: Coordinates): Either[ValidationError, Unit] =
    if (coordinates.longitude < MIN_LONGITUDE || coordinates.longitude > MAX_LONGITUDE) {
      Left(InvalidLongitudeError(coordinates.longitude))
    } else if (coordinates.latitude < MIN_LATITUDE || coordinates.latitude > MAX_LATITUDE) {
      Left(InvalidLatitudeError(coordinates.latitude))
    } else {
      Right(())
    }

  def validateLocationUpdateRequest(
    skatingEventId: String,
    skaterId: String,
    request: Request[AnyContent]
  ): Either[ValidationError, Unit] =
    for {
      _           <- validateUUIDs(skatingEventId, skaterId)
      coordinates <- parseCoordinates(request)
      _           <- validateCoordinates(coordinates)
    } yield ()
}
