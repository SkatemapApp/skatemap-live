package skatemap.core

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike
import skatemap.domain.Location

import java.time.Clock
import scala.concurrent.duration._

class BroadcasterSpec
    extends TestKit(ActorSystem("BroadcasterSpec"))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  implicit val defaultPatience: PatienceConfig = PatienceConfig(
    timeout = Span(3, Seconds),
    interval = Span(50, Millis)
  )

  override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  private def createBroadcaster(): InMemoryBroadcaster = new InMemoryBroadcaster(system, Clock.systemUTC())

  private val event1    = "event-1"
  private val event2    = "event-2"
  private val location1 = Location("skater-1", 1.0, 2.0, 1000L)
  private val location2 = Location("skater-2", 3.0, 4.0, 2000L)

  "Broadcaster" should {

    "allow multiple subscribers to receive updates from the same event" in {
      val broadcaster = createBroadcaster()

      val subscriber1Future = broadcaster.subscribe(event1).take(2).runWith(Sink.seq)
      val subscriber2Future = broadcaster.subscribe(event1).take(2).runWith(Sink.seq)

      broadcaster.publish(event1, location1)
      broadcaster.publish(event1, location2)

      val subscriber1Results = subscriber1Future.futureValue
      val subscriber2Results = subscriber2Future.futureValue

      subscriber1Results should contain theSameElementsAs Seq(location1, location2)
      subscriber2Results should contain theSameElementsAs Seq(location1, location2)
    }

    "ensure event isolation - subscribers from different events do not receive each other's updates" in {
      val broadcaster = createBroadcaster()

      val event1Subscriber = broadcaster.subscribe(event1).take(1).runWith(Sink.seq)
      val event2Subscriber = broadcaster.subscribe(event2).take(1).runWith(Sink.seq)

      broadcaster.publish(event1, location1)
      broadcaster.publish(event2, location2)

      val event1Results = event1Subscriber.futureValue
      val event2Results = event2Subscriber.futureValue

      event1Results should contain only location1
      event2Results should contain only location2
    }

    "handle backpressure with buffer limit" in {
      val broadcaster = createBroadcaster()

      val slowSubscriber = broadcaster
        .subscribe(event1)
        .take(3)
        .throttle(1, 100.millis)
        .runWith(Sink.seq)

      (1 to 150).foreach { i =>
        broadcaster.publish(event1, location1.copy(timestamp = i))
      }

      val results = slowSubscriber.futureValue
      results should have size 3
    }

    "create streams lazily" in {
      val broadcaster = createBroadcaster()

      val initialHubCount = broadcaster.hubs.size
      initialHubCount shouldBe 0

      broadcaster.subscribe(event1)
      broadcaster.hubs.size shouldBe 1

      broadcaster.publish(event2, location1)
      broadcaster.hubs.size shouldBe 2
    }

    "support publishing to non-existent event (lazy creation)" in {
      val broadcaster = createBroadcaster()

      val subscriber = broadcaster.subscribe(event1).take(1).runWith(Sink.seq)
      broadcaster.publish(event1, location1)

      val results = subscriber.futureValue
      results should contain only location1
    }
  }
}
