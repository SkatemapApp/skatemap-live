package skatemap.api.json

import skatemap.domain.{Location, LocationBatch, LocationUpdate}
import play.api.libs.json._

object LocationJson {

  implicit val locationFormat: Format[Location] = new Format[Location] {
    def reads(json: JsValue): JsResult[Location] =
      for {
        skaterId  <- (json \ "skaterId").validate[String]
        latitude  <- (json \ "latitude").validate[Double]
        longitude <- (json \ "longitude").validate[Double]
        timestamp <- (json \ "timestamp").validate[Long]
      } yield Location(skaterId, longitude, latitude, timestamp)

    def writes(location: Location): JsValue = Json.obj(
      "skaterId"  -> location.skaterId,
      "latitude"  -> location.latitude,
      "longitude" -> location.longitude,
      "timestamp" -> location.timestamp
    )
  }

  implicit val locationUpdateFormat: Format[LocationUpdate] = new Format[LocationUpdate] {
    def reads(json: JsValue): JsResult[LocationUpdate] =
      for {
        eventId   <- (json \ "eventId").validate[String]
        skaterId  <- (json \ "skaterId").validate[String]
        longitude <- (json \ "longitude").validate[Double]
        latitude  <- (json \ "latitude").validate[Double]
        timestamp <- (json \ "timestamp").validateOpt[Long].map(_.getOrElse(System.currentTimeMillis))
      } yield LocationUpdate(eventId, skaterId, longitude, latitude, timestamp)

    def writes(update: LocationUpdate): JsValue = Json.obj(
      "eventId"   -> update.eventId,
      "skaterId"  -> update.skaterId,
      "longitude" -> update.longitude,
      "latitude"  -> update.latitude,
      "timestamp" -> update.timestamp
    )
  }

  implicit val locationWrites: Writes[Location]           = Json.writes[Location]
  implicit val locationBatchWrites: Writes[LocationBatch] = Json.writes[LocationBatch]
}
