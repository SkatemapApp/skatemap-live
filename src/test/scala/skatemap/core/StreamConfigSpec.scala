package skatemap.core

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class StreamConfigSpec extends AnyWordSpec with Matchers {

  "StreamConfig" should {

    "create instance with positive batch size" in {
      val config = StreamConfig(50, 250.millis)

      config.batchSize shouldBe 50
    }

    "create instance with positive batch interval" in {
      val config = StreamConfig(100, 1.second)

      config.batchInterval shouldBe 1.second
    }

    "support different time units for batch interval" in {
      val millisConfig  = StreamConfig(100, 500.millis)
      val secondsConfig = StreamConfig(100, 2.seconds)

      millisConfig.batchInterval shouldBe 500.millis
      secondsConfig.batchInterval shouldBe 2.seconds
    }

    "allow various batch sizes" in {
      val small  = StreamConfig(1, 100.millis)
      val medium = StreamConfig(100, 500.millis)
      val large  = StreamConfig(1000, 1.second)

      small.batchSize shouldBe 1
      medium.batchSize shouldBe 100
      large.batchSize shouldBe 1000
    }

    "represent batch interval correctly in different units" in {
      val config = StreamConfig(100, 500.millis)

      config.batchInterval.toMillis shouldBe 500
      config.batchInterval shouldBe 0.5.seconds
    }

  }
}
