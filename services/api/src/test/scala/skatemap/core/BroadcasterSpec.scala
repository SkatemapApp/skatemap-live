package skatemap.core

import org.apache.pekko.actor.ActorSystem
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

      val probe1 = broadcaster.subscribe(event1).runWith(TestSink.probe[Location])
      val probe2 = broadcaster.subscribe(event1).runWith(TestSink.probe[Location])

      probe1.request(2)
      probe2.request(2)
      probe1.expectNoMessage(100.millis)
      probe2.expectNoMessage(100.millis)

      broadcaster.publish(event1, location1).futureValue
      broadcaster.publish(event1, location2).futureValue

      probe1.expectNextUnordered(location1, location2)
      probe2.expectNextUnordered(location1, location2)

      probe1.cancel()
      probe2.cancel()
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

      val probe = broadcaster
        .subscribe(event1)
        .throttle(1, 100.millis)
        .runWith(TestSink.probe[Location])

      probe.request(3)
      probe.expectNoMessage(100.millis)

      (1 to 150).foreach { i =>
        broadcaster.publish(event1, location1.copy(timestamp = i)).futureValue
      }

      probe.expectNextN(3)
      probe.cancel()
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

      val probe = broadcaster.subscribe(event1).runWith(TestSink.probe[Location])
      probe.request(1)
      probe.expectNoMessage(100.millis)

      broadcaster.publish(event1, location1).futureValue

      probe.expectNext(location1)
      probe.cancel()
    }
  }
}
