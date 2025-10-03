package skatemap.core

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class CleanupConfigSpec extends AnyWordSpec with Matchers {

  "CleanupConfig.default" should {

    "provide default initialDelay of 10 seconds" in {
      CleanupConfig.default.initialDelay shouldBe 10.seconds
    }

    "provide default interval of 10 seconds" in {
      CleanupConfig.default.interval shouldBe 10.seconds
    }

  }

  "CleanupConfig validation" should {

    "reject zero initialDelay" in {
      val exception = intercept[IllegalArgumentException] {
        CleanupConfig(initialDelay = 0.seconds, interval = 10.seconds)
      }
      exception.getMessage should include("initialDelay must be positive")
    }

    "reject negative initialDelay" in {
      val exception = intercept[IllegalArgumentException] {
        CleanupConfig(initialDelay = (-1).seconds, interval = 10.seconds)
      }
      exception.getMessage should include("initialDelay must be positive")
    }

    "reject zero interval" in {
      val exception = intercept[IllegalArgumentException] {
        CleanupConfig(initialDelay = 10.seconds, interval = 0.seconds)
      }
      exception.getMessage should include("interval must be positive")
    }

    "reject negative interval" in {
      val exception = intercept[IllegalArgumentException] {
        CleanupConfig(initialDelay = 10.seconds, interval = (-1).seconds)
      }
      exception.getMessage should include("interval must be positive")
    }

    "accept positive values" in {
      val config = CleanupConfig(initialDelay = 5.seconds, interval = 15.seconds)
      config.initialDelay shouldBe 5.seconds
      config.interval shouldBe 15.seconds
    }

  }

}
