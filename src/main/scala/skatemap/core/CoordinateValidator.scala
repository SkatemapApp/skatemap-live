package skatemap.core

import skatemap.domain.Coordinates

object CoordinateValidator {
  private val MIN_LONGITUDE = -180.0
  private val MAX_LONGITUDE = 180.0
  private val MIN_LATITUDE  = -90.0
  private val MAX_LATITUDE  = 90.0

  def validateBounds(coordinates: Coordinates): Either[ValidationError, Coordinates] =
    for {
      _ <- validateLongitude(coordinates.longitude)
      _ <- validateLatitude(coordinates.latitude)
    } yield coordinates

  private def validateLongitude(longitude: Double): Either[ValidationError, Unit] =
    if (longitude.isNaN || longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE)
      Left(InvalidLongitudeError(longitude))
    else Right(())

  private def validateLatitude(latitude: Double): Either[ValidationError, Unit] =
    if (latitude.isNaN || latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) Left(InvalidLatitudeError(latitude))
    else Right(())
}
