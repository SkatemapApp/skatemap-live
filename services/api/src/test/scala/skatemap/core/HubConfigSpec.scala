package skatemap.core

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class HubConfigSpec extends AnyWordSpec with Matchers {

  "HubConfig" should {

    "be created with valid values" in {
      val config = HubConfig(ttl = 300.seconds, cleanupInterval = 60.seconds, bufferSize = 128)

      config.ttl should be(300.seconds)
      config.cleanupInterval should be(60.seconds)
      config.bufferSize should be(128)
    }

    "support different time units" in {
      val config = HubConfig(ttl = 5.minutes, cleanupInterval = 1.minute, bufferSize = 256)

      config.ttl should be(300.seconds)
      config.cleanupInterval should be(60.seconds)
      config.bufferSize should be(256)
    }
  }
}
