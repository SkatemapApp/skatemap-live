package core

import scala.util

trait JsonCodec[T] {
  def encode(value: T): String
  def decode(json: String): Either[String, T]
}

object JsonCodec {

  implicit val locationCodec: JsonCodec[Location] = new JsonCodec[Location] {
    def encode(location: Location): String =
      s"""{"skaterId":"${location.skaterId}","latitude":${location.latitude},"longitude":${location.longitude},"timestamp":${location.timestamp}}"""

    def decode(json: String): Either[String, Location] =
      try {
        val skaterIdPattern  = """"skaterId"\s*:\s*"([^"]+)"""".r
        val latitudePattern  = """"latitude"\s*:\s*([^,}\s]+)""".r
        val longitudePattern = """"longitude"\s*:\s*([^,}\s]+)""".r
        val timestampPattern = """"timestamp"\s*:\s*([^,}\s]+)""".r

        for {
          skaterId     <- skaterIdPattern.findFirstMatchIn(json).map(_.group(1)).toRight("Missing skaterId")
          latitudeStr  <- latitudePattern.findFirstMatchIn(json).map(_.group(1)).toRight("Missing latitude")
          longitudeStr <- longitudePattern.findFirstMatchIn(json).map(_.group(1)).toRight("Missing longitude")
          timestampStr <- timestampPattern.findFirstMatchIn(json).map(_.group(1)).toRight("Missing timestamp")
          latitude     <- util.Try(latitudeStr.toDouble).toEither.left.map(_ => "Invalid latitude")
          longitude    <- util.Try(longitudeStr.toDouble).toEither.left.map(_ => "Invalid longitude")
          timestamp    <- util.Try(timestampStr.toLong).toEither.left.map(_ => "Invalid timestamp")
        } yield Location(skaterId, latitude, longitude, timestamp)
      } catch {
        case _: Exception => Left("Invalid JSON format")
      }
  }

  implicit val locationUpdateCodec: JsonCodec[LocationUpdate] = new JsonCodec[LocationUpdate] {
    def encode(update: LocationUpdate): String =
      s"""{"eventId":"${update.eventId}","skaterId":"${update.skaterId}","longitude":${update.longitude},"latitude":${update.latitude},"timestamp":${update.timestamp}}"""

    def decode(json: String): Either[String, LocationUpdate] =
      try {
        val eventIdPattern   = """"eventId"\s*:\s*"([^"]+)"""".r
        val skaterIdPattern  = """"skaterId"\s*:\s*"([^"]+)"""".r
        val longitudePattern = """"longitude"\s*:\s*([^,}\s]+)""".r
        val latitudePattern  = """"latitude"\s*:\s*([^,}\s]+)""".r
        val timestampPattern = """"timestamp"\s*:\s*([^,}\s]+)""".r

        for {
          eventId      <- eventIdPattern.findFirstMatchIn(json).map(_.group(1)).toRight("Missing eventId")
          skaterId     <- skaterIdPattern.findFirstMatchIn(json).map(_.group(1)).toRight("Missing skaterId")
          longitudeStr <- longitudePattern.findFirstMatchIn(json).map(_.group(1)).toRight("Missing longitude")
          latitudeStr  <- latitudePattern.findFirstMatchIn(json).map(_.group(1)).toRight("Missing latitude")
          longitude    <- util.Try(longitudeStr.toDouble).toEither.left.map(_ => "Invalid longitude")
          latitude     <- util.Try(latitudeStr.toDouble).toEither.left.map(_ => "Invalid latitude")
          timestamp <- timestampPattern
            .findFirstMatchIn(json)
            .map(_.group(1))
            .map(s => util.Try(s.toLong).toEither.left.map(_ => "Invalid timestamp"))
            .getOrElse(Right(System.currentTimeMillis))
        } yield LocationUpdate(eventId, skaterId, longitude, latitude, timestamp)
      } catch {
        case _: Exception => Left("Invalid JSON format")
      }
  }
}
