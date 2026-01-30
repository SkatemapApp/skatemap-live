package skatemap.core

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.testkit.scaladsl.TestSink
import org.apache.pekko.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike
import skatemap.domain.Location
import skatemap.test.TestClock

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

  private val defaultConfig = HubConfig(
    ttl = 300.seconds,
    cleanupInterval = 60.seconds,
    bufferSize = 128
  )

  private def createBroadcaster(): InMemoryBroadcaster =
    new InMemoryBroadcaster(system, TestClock.fixed(1000L), defaultConfig)

  private val event1    = "event-1"
  private val event2    = "event-2"
  private val location1 = Location("skater-1", 1.0, 2.0, 1000L)
  private val location2 = Location("skater-2", 3.0, 4.0, 2000L)

  "Broadcaster" should {

    "allow multiple subscribers to receive updates from the same event" in {
      val broadcaster = createBroadcaster()

      val subscriber1Future = broadcaster.subscribe(event1).take(2).runWith(Sink.seq)
      val subscriber2Future = broadcaster.subscribe(event1).take(2).runWith(Sink.seq)

      broadcaster.publish(event1, location1).futureValue
      broadcaster.publish(event1, location2).futureValue

      val subscriber1Results = subscriber1Future.futureValue
      val subscriber2Results = subscriber2Future.futureValue

      subscriber1Results should contain theSameElementsAs Seq(location1, location2)
      subscriber2Results should contain theSameElementsAs Seq(location1, location2)
    }

    "ensure event isolation - subscribers from different events do not receive each other's updates" in {
      val broadcaster = createBroadcaster()

      val event1Probe = broadcaster.subscribe(event1).runWith(TestSink.probe[Location])
      val event2Probe = broadcaster.subscribe(event2).runWith(TestSink.probe[Location])

      event1Probe.request(1)
      event2Probe.request(1)
      event1Probe.expectNoMessage(100.millis)
      event2Probe.expectNoMessage(100.millis)

      broadcaster.publish(event1, location1).futureValue
      broadcaster.publish(event2, location2).futureValue

      event1Probe.expectNext(location1)
      event2Probe.expectNext(location2)

      event1Probe.cancel()
      event2Probe.cancel()
    }

    "handle backpressure with buffer limit" in {
      val broadcaster = createBroadcaster()

      val slowSubscriber = broadcaster
        .subscribe(event1)
        .take(3)
        .throttle(1, 100.millis)
        .runWith(Sink.seq)

      (1 to 150).foreach { i =>
        broadcaster.publish(event1, location1.copy(timestamp = i)).futureValue
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

      broadcaster.publish(event2, location1).futureValue
      broadcaster.hubs.size shouldBe 2
    }

    "support publishing to non-existent event (lazy creation)" in {
      val broadcaster = createBroadcaster()

      val subscriber = broadcaster.subscribe(event1).take(1).runWith(Sink.seq)
      broadcaster.publish(event1, location1).futureValue

      val results = subscriber.futureValue
      results should contain only location1
    }
  }
}
