package core

import core.json.JsonFieldExtractor

trait JsonCodec[T] {
  def encode(value: T): String

  def decode(json: String): Either[String, T]
}

object JsonCodec {

  implicit val locationCodec: JsonCodec[Location] = new JsonCodec[Location] {
    def encode(location: Location): String =
      s"""{"skaterId":"${location.skaterId}","longitude":${location.longitude},"latitude":${location.latitude},"timestamp":${location.timestamp}}"""

    def decode(json: String): Either[String, Location] =
      for {
        skaterId  <- JsonFieldExtractor.extractString(json, "skaterId")
        longitude <- JsonFieldExtractor.extractDouble(json, "longitude")
        latitude  <- JsonFieldExtractor.extractDouble(json, "latitude")
        timestamp <- JsonFieldExtractor.extractLong(json, "timestamp")
      } yield Location(skaterId, longitude, latitude, timestamp)
  }

  implicit val locationUpdateCodec: JsonCodec[LocationUpdate] = new JsonCodec[LocationUpdate] {
    def encode(update: LocationUpdate): String =
      s"""{"eventId":"${update.eventId}","skaterId":"${update.skaterId}","longitude":${update.longitude},"latitude":${update.latitude},"timestamp":${update.timestamp}}"""

    def decode(json: String): Either[String, LocationUpdate] =
      for {
        eventId   <- JsonFieldExtractor.extractString(json, "eventId")
        skaterId  <- JsonFieldExtractor.extractString(json, "skaterId")
        longitude <- JsonFieldExtractor.extractDouble(json, "longitude")
        latitude  <- JsonFieldExtractor.extractDouble(json, "latitude")
        timestamp <- JsonFieldExtractor.extractOptionalLong(json, "timestamp", System.currentTimeMillis)
      } yield LocationUpdate(eventId, skaterId, longitude, latitude, timestamp)
  }
}
