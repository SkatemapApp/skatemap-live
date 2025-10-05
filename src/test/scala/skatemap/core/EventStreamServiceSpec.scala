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
import skatemap.test.{StubBroadcaster, TestClock}

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

  "EventStreamService" should {

    "create stream with empty store and empty broadcaster" in {
      val eventId     = "550e8400-e29b-41d4-a716-446655440000"
      val fixedTime   = 1234567890123L
      val clock       = TestClock.fixed(fixedTime)
      val store       = new InMemoryLocationStore(clock, LocationConfig(1.hour))
      val broadcaster = new StubBroadcaster()
      val service     = new EventStreamService(store, broadcaster, StreamConfig(100, 500.millis), clock)

      noException should be thrownBy service.createEventStream(eventId)
    }

    "create stream with store data and empty broadcaster" in {
      val eventId     = "550e8400-e29b-41d4-a716-446655440000"
      val location1   = Location("skater-1", 1.0, 2.0, 1000L)
      val location2   = Location("skater-2", 3.0, 4.0, 2000L)
      val fixedTime   = 1234567890123L
      val clock       = TestClock.fixed(fixedTime)
      val store       = new InMemoryLocationStore(clock, LocationConfig(1.hour))
      val broadcaster = new StubBroadcaster()
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
      val clock     = TestClock.fixed(fixedTime)
      val store     = new InMemoryLocationStore(clock, LocationConfig(1.hour))
      val broadcaster = new StubBroadcaster() {
        override def subscribe(eventId: String): Source[Location, NotUsed] =
          Source(List(location1, location2))
      }
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
      val clock     = TestClock.fixed(fixedTime)
      val store     = new InMemoryLocationStore(clock, LocationConfig(1.hour))
      val broadcaster = new StubBroadcaster() {
        override def subscribe(eventId: String): Source[Location, NotUsed] =
          Source(List(location3))
      }
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
      val clock       = TestClock.fixed(fixedTime)
      val store       = new InMemoryLocationStore(clock, LocationConfig(1.hour))
      val broadcaster = new StubBroadcaster()
      val service     = new EventStreamService(store, broadcaster, StreamConfig(100, 500.millis), clock)

      store.put(eventId, location1)

      val result = service.createEventStream(eventId).take(1).runWith(Sink.head).futureValue
      val json   = Json.parse(result)

      (json \ "locations").as[List[Location]] should contain only location1
      (json \ "serverTime").as[Long] shouldBe fixedTime
    }
  }
}
