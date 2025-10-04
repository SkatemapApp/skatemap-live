package skatemap.core

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class LocationConfigSpec extends AnyWordSpec with Matchers {

  "LocationConfig validation" should {

    "reject zero ttl" in {
      val exception = intercept[IllegalArgumentException] {
        LocationConfig(ttl = 0.seconds)
      }
      exception.getMessage should include("ttl must be positive")
    }

    "reject negative ttl" in {
      val exception = intercept[IllegalArgumentException] {
        LocationConfig(ttl = (-1).seconds)
      }
      exception.getMessage should include("ttl must be positive")
    }

    "accept positive values" in {
      val config = LocationConfig(ttl = 30.seconds)
      config.ttl shouldBe 30.seconds
    }

  }

}
