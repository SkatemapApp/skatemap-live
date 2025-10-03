package skatemap.infra

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class SkatemapLiveModuleSpec extends AnyWordSpec with Matchers {

  "SkatemapLiveModule.provideCleanupConfig" should {

    "use default values when config paths are not present" in {
      val config = ConfigFactory.empty()
      val module = new SkatemapLiveModule

      val cleanupConfig = module.provideCleanupConfig(config)

      cleanupConfig.initialDelay shouldBe 10.seconds
      cleanupConfig.interval shouldBe 10.seconds
    }

    "use configured initialDelaySeconds when present" in {
      val config = ConfigFactory.parseString("""
        skatemap.cleanup.initialDelaySeconds = 5
      """)
      val module = new SkatemapLiveModule

      val cleanupConfig = module.provideCleanupConfig(config)

      cleanupConfig.initialDelay shouldBe 5.seconds
      cleanupConfig.interval shouldBe 10.seconds
    }

    "use configured intervalSeconds when present" in {
      val config = ConfigFactory.parseString("""
        skatemap.cleanup.intervalSeconds = 15
      """)
      val module = new SkatemapLiveModule

      val cleanupConfig = module.provideCleanupConfig(config)

      cleanupConfig.initialDelay shouldBe 10.seconds
      cleanupConfig.interval shouldBe 15.seconds
    }

    "use both configured values when both present" in {
      val config = ConfigFactory.parseString("""
        skatemap.cleanup.initialDelaySeconds = 3
        skatemap.cleanup.intervalSeconds = 7
      """)
      val module = new SkatemapLiveModule

      val cleanupConfig = module.provideCleanupConfig(config)

      cleanupConfig.initialDelay shouldBe 3.seconds
      cleanupConfig.interval shouldBe 7.seconds
    }

    "fall back to default when initialDelaySeconds is negative" in {
      val config = ConfigFactory.parseString("""
        skatemap.cleanup.initialDelaySeconds = -5
      """)
      val module = new SkatemapLiveModule

      val cleanupConfig = module.provideCleanupConfig(config)

      cleanupConfig.initialDelay shouldBe 10.seconds
    }

    "fall back to default when intervalSeconds is zero" in {
      val config = ConfigFactory.parseString("""
        skatemap.cleanup.intervalSeconds = 0
      """)
      val module = new SkatemapLiveModule

      val cleanupConfig = module.provideCleanupConfig(config)

      cleanupConfig.interval shouldBe 10.seconds
    }

    "fall back to default when config value is not an integer" in {
      val config = ConfigFactory.parseString("""
        skatemap.cleanup.initialDelaySeconds = "invalid"
      """)
      val module = new SkatemapLiveModule

      val cleanupConfig = module.provideCleanupConfig(config)

      cleanupConfig.initialDelay shouldBe 10.seconds
    }

  }

}
