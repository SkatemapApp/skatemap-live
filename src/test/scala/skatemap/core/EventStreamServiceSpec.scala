package skatemap.core

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{Format, Json}
import skatemap.domain.{Location, LocationBatch}
import skatemap.test.TestClock

import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration._

class EventStreamServiceSpec
    extends TestKit(ActorSystem("EventStreamServiceSpec"))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  implicit val defaultPatience: PatienceConfig = PatienceConfig(
    timeout = Span(3, Seconds),
    interval = Span(50, Millis)
  )

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  implicit val locationFormat: Format[Location]           = Json.format[Location]
  implicit val locationBatchFormat: Format[LocationBatch] = Json.format[LocationBatch]

  private class MockLocationStore extends LocationStore {
    private val storage = new ConcurrentHashMap[String, Map[String, Location]]()

    def put(eventId: String, location: Location): Unit = {
      val eventMap = Option(storage.get(eventId)).getOrElse(Map.empty)
      storage.put(eventId, eventMap + (location.skaterId -> location))
    }

    def getAll(eventId: String): Map[String, Location] =
      Option(storage.get(eventId)).getOrElse(Map.empty)

    def cleanup(): Unit = storage.clear()

    def cleanupAll(): Int = {
      import scala.jdk.CollectionConverters._
      val count = storage.values.asScala.map(_.size).sum
      storage.clear()
      count
    }
  }

  private class MockBroadcaster extends Broadcaster {
    def publish(eventId: String, location: Location): Unit    = ()
    def subscribe(eventId: String): Source[Location, NotUsed] = Source.empty
  }

  "EventStreamService" should {

    "create stream with empty store and empty broadcaster" in {
      val eventId     = "550e8400-e29b-41d4-a716-446655440000"
      val fixedTime   = 1234567890123L
      val store       = new MockLocationStore()
      val broadcaster = new MockBroadcaster()
      val clock       = TestClock.fixed(fixedTime)
      val service     = new EventStreamService(store, broadcaster, StreamConfig(100, 500.millis), clock)

      noException should be thrownBy service.createEventStream(eventId)
    }

    "create stream with store data and empty broadcaster" in {
      val eventId     = "550e8400-e29b-41d4-a716-446655440000"
      val location1   = Location("skater-1", 1.0, 2.0, 1000L)
      val location2   = Location("skater-2", 3.0, 4.0, 2000L)
      val fixedTime   = 1234567890123L
      val store       = new MockLocationStore()
      val broadcaster = new MockBroadcaster()
      val clock       = TestClock.fixed(fixedTime)
      val service     = new EventStreamService(store, broadcaster, StreamConfig(100, 500.millis), clock)

      store.put(eventId, location1)
      store.put(eventId, location2)

      val result = service.createEventStream(eventId).take(1).runWith(Sink.head).futureValue
      val parsed = Json.parse(result).as[LocationBatch]

      parsed.locations should contain theSameElementsAs List(location1, location2)
      parsed.serverTime shouldBe fixedTime
    }

    "create stream with empty store and broadcaster data" in {
      val eventId   = "550e8400-e29b-41d4-a716-446655440000"
      val location1 = Location("skater-1", 1.0, 2.0, 1000L)
      val location2 = Location("skater-2", 3.0, 4.0, 2000L)
      val fixedTime = 1234567890123L
      val store     = new MockLocationStore()
      val broadcaster = new MockBroadcaster() {
        override def subscribe(eventId: String): Source[Location, NotUsed] =
          Source(List(location1, location2))
      }
      val clock   = TestClock.fixed(fixedTime)
      val service = new EventStreamService(store, broadcaster, StreamConfig(100, 500.millis), clock)

      val result = service.createEventStream(eventId).take(1).runWith(Sink.head).futureValue
      val parsed = Json.parse(result).as[LocationBatch]

      parsed.locations should contain theSameElementsAs List(location1, location2)
      parsed.serverTime shouldBe fixedTime
    }

    "create stream combining store and broadcaster data" in {
      val eventId   = "550e8400-e29b-41d4-a716-446655440000"
      val location1 = Location("skater-1", 1.0, 2.0, 1000L)
      val location2 = Location("skater-2", 3.0, 4.0, 2000L)
      val location3 = Location("skater-3", 5.0, 6.0, 3000L)
      val fixedTime = 1234567890123L
      val store     = new MockLocationStore()
      val broadcaster = new MockBroadcaster() {
        override def subscribe(eventId: String): Source[Location, NotUsed] =
          Source(List(location3))
      }
      val clock   = TestClock.fixed(fixedTime)
      val service = new EventStreamService(store, broadcaster, StreamConfig(100, 500.millis), clock)

      store.put(eventId, location1)
      store.put(eventId, location2)

      val result = service.createEventStream(eventId).take(1).runWith(Sink.head).futureValue
      val parsed = Json.parse(result).as[LocationBatch]

      parsed.locations should have size 3
      parsed.locations should contain theSameElementsAs List(location1, location2, location3)
      parsed.serverTime shouldBe fixedTime
    }

    "serialize LocationBatch correctly" in {
      val eventId     = "550e8400-e29b-41d4-a716-446655440000"
      val location1   = Location("skater-1", 1.0, 2.0, 1000L)
      val fixedTime   = 1234567890123L
      val store       = new MockLocationStore()
      val broadcaster = new MockBroadcaster()
      val clock       = TestClock.fixed(fixedTime)
      val service     = new EventStreamService(store, broadcaster, StreamConfig(100, 500.millis), clock)

      store.put(eventId, location1)

      val result = service.createEventStream(eventId).take(1).runWith(Sink.head).futureValue
      val json   = Json.parse(result)

      (json \ "locations").as[List[Location]] should contain only location1
      (json \ "serverTime").as[Long] shouldBe fixedTime
    }
  }
}
