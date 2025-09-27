package skatemap.domain

final case class LocationUpdate(eventId: String, skaterId: String, longitude: Double, latitude: Double, timestamp: Long)
