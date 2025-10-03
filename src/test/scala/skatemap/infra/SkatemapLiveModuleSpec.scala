package skatemap.infra

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class SkatemapLiveModuleSpec extends AnyWordSpec with Matchers {

  "SkatemapLiveModule.provideCleanupConfig" should {

    "fail when initialDelaySeconds config path is missing" in {
      val config = ConfigFactory.parseString("""
        skatemap.cleanup.intervalSeconds = 10
      """)
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException] {
        module.provideCleanupConfig(config)
      }
      exception.getMessage should include("Required configuration missing: skatemap.cleanup.initialDelaySeconds")
    }

    "fail when intervalSeconds config path is missing" in {
      val config = ConfigFactory.parseString("""
        skatemap.cleanup.initialDelaySeconds = 10
      """)
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException] {
        module.provideCleanupConfig(config)
      }
      exception.getMessage should include("Required configuration missing: skatemap.cleanup.intervalSeconds")
    }

    "fail when both config paths are missing" in {
      val config = ConfigFactory.empty()
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException] {
        module.provideCleanupConfig(config)
      }
      exception.getMessage should include("Required configuration missing")
    }

    "fail when initialDelaySeconds is zero" in {
      val config = ConfigFactory.parseString("""
        skatemap.cleanup.initialDelaySeconds = 0
        skatemap.cleanup.intervalSeconds = 10
      """)
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException] {
        module.provideCleanupConfig(config)
      }
      exception.getMessage should include(
        "Invalid configuration: skatemap.cleanup.initialDelaySeconds=0 (must be positive)"
      )
    }

    "fail when initialDelaySeconds is negative" in {
      val config = ConfigFactory.parseString("""
        skatemap.cleanup.initialDelaySeconds = -5
        skatemap.cleanup.intervalSeconds = 10
      """)
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException] {
        module.provideCleanupConfig(config)
      }
      exception.getMessage should include(
        "Invalid configuration: skatemap.cleanup.initialDelaySeconds=-5 (must be positive)"
      )
    }

    "fail when intervalSeconds is zero" in {
      val config = ConfigFactory.parseString("""
        skatemap.cleanup.initialDelaySeconds = 10
        skatemap.cleanup.intervalSeconds = 0
      """)
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException] {
        module.provideCleanupConfig(config)
      }
      exception.getMessage should include(
        "Invalid configuration: skatemap.cleanup.intervalSeconds=0 (must be positive)"
      )
    }

    "fail when intervalSeconds is negative" in {
      val config = ConfigFactory.parseString("""
        skatemap.cleanup.initialDelaySeconds = 10
        skatemap.cleanup.intervalSeconds = -15
      """)
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException] {
        module.provideCleanupConfig(config)
      }
      exception.getMessage should include(
        "Invalid configuration: skatemap.cleanup.intervalSeconds=-15 (must be positive)"
      )
    }

    "use configured values when both are present and positive" in {
      val config = ConfigFactory.parseString("""
        skatemap.cleanup.initialDelaySeconds = 3
        skatemap.cleanup.intervalSeconds = 7
      """)
      val module = new SkatemapLiveModule

      val cleanupConfig = module.provideCleanupConfig(config)

      cleanupConfig.initialDelay shouldBe 3.seconds
      cleanupConfig.interval shouldBe 7.seconds
    }

  }

}
