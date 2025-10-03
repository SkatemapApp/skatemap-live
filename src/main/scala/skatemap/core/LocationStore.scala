package skatemap.core

import skatemap.domain.Location

trait LocationStore {
  def put(eventId: String, location: Location): Unit
  def getAll(eventId: String): Map[String, Location]
  def cleanup(): Unit
  def cleanupAll(): Int
}
