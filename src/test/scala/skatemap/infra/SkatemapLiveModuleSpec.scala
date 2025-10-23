package skatemap.infra

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class SkatemapLiveModuleSpec extends AnyWordSpec with Matchers {

  "SkatemapLiveModule.provideLocationConfig" should {

    "fail when ttlSeconds config path is missing" in {
      val config = ConfigFactory.empty()
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException](module.provideLocationConfig(config))
      exception.getMessage should include("Required configuration missing: skatemap.location.ttlSeconds")
    }

    "fail when ttlSeconds is zero" in {
      val config = ConfigFactory.parseString("""skatemap.location.ttlSeconds = 0""")
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException](module.provideLocationConfig(config))
      exception.getMessage should include("Invalid configuration: skatemap.location.ttlSeconds=0 (must be positive)")
    }

    "fail when ttlSeconds is negative" in {
      val config = ConfigFactory.parseString("""skatemap.location.ttlSeconds = -30""")
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException](module.provideLocationConfig(config))
      exception.getMessage should include("Invalid configuration: skatemap.location.ttlSeconds=-30 (must be positive)")
    }

    "use configured value when present and positive" in {
      val config = ConfigFactory.parseString("""skatemap.location.ttlSeconds = 45""")
      val module = new SkatemapLiveModule

      val locationConfig = module.provideLocationConfig(config)
      locationConfig.ttl shouldBe 45.seconds
    }

    "use environment variable when set" in {
      val config = ConfigFactory
        .parseString("""skatemap.location.ttlSeconds = ${?LOCATION_TTL_SECONDS}""")
        .withFallback(ConfigFactory.parseString("""skatemap.location.ttlSeconds = 30"""))
        .withFallback(ConfigFactory.systemEnvironment())
        .resolve()
      val module = new SkatemapLiveModule

      val locationConfig = module.provideLocationConfig(config)
      locationConfig.ttl shouldBe 30.seconds
    }

  }

  "SkatemapLiveModule.provideStreamConfig" should {

    "fail when batchSize config path is missing" in {
      val config = ConfigFactory.parseString("""skatemap.stream.batchIntervalMillis = 500""")
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException] {
        module.provideStreamConfig(config)
      }
      exception.getMessage should include("Required configuration missing: skatemap.stream.batchSize")
    }

    "fail when batchIntervalMillis config path is missing" in {
      val config = ConfigFactory.parseString("""skatemap.stream.batchSize = 100""")
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException](module.provideStreamConfig(config))
      exception.getMessage should include("Required configuration missing: skatemap.stream.batchIntervalMillis")
    }

    "fail when both config paths are missing" in {
      val config = ConfigFactory.empty()
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException](module.provideStreamConfig(config))
      exception.getMessage should include("Required configuration missing")
    }

    "fail when batchSize is zero" in {
      val config = ConfigFactory.parseString("""
        skatemap.stream.batchSize = 0
        skatemap.stream.batchIntervalMillis = 500
      """)
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException] {
        module.provideStreamConfig(config)
      }
      exception.getMessage should include(
        "Invalid configuration: skatemap.stream.batchSize=0 (must be positive)"
      )
    }

    "fail when batchSize is negative" in {
      val config = ConfigFactory.parseString("""
        skatemap.stream.batchSize = -10
        skatemap.stream.batchIntervalMillis = 500
      """)
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException](module.provideStreamConfig(config))
      exception.getMessage should include(
        "Invalid configuration: skatemap.stream.batchSize=-10 (must be positive)"
      )
    }

    "fail when batchIntervalMillis is zero" in {
      val config = ConfigFactory.parseString("""
        skatemap.stream.batchSize = 100
        skatemap.stream.batchIntervalMillis = 0
      """)
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException] {
        module.provideStreamConfig(config)
      }
      exception.getMessage should include(
        "Invalid configuration: skatemap.stream.batchIntervalMillis=0 (must be positive)"
      )
    }

    "fail when batchIntervalMillis is negative" in {
      val config = ConfigFactory.parseString("""
        skatemap.stream.batchSize = 100
        skatemap.stream.batchIntervalMillis = -500
      """)
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException](module.provideStreamConfig(config))
      exception.getMessage should include(
        "Invalid configuration: skatemap.stream.batchIntervalMillis=-500 (must be positive)"
      )
    }

    "use configured values when both are present and positive" in {
      val config = ConfigFactory.parseString("""
        skatemap.stream.batchSize = 50
        skatemap.stream.batchIntervalMillis = 250
      """)
      val module = new SkatemapLiveModule

      val streamConfig = module.provideStreamConfig(config)
      streamConfig.batchSize shouldBe 50
      streamConfig.batchInterval shouldBe 250.millis
    }

  }

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

      val exception = intercept[IllegalArgumentException](module.provideCleanupConfig(config))
      exception.getMessage should include("Required configuration missing: skatemap.cleanup.intervalSeconds")
    }

    "fail when both config paths are missing" in {
      val config = ConfigFactory.empty()
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException](module.provideCleanupConfig(config))
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

      val exception = intercept[IllegalArgumentException](module.provideCleanupConfig(config))
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

      val exception = intercept[IllegalArgumentException](module.provideCleanupConfig(config))
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

      val exception = intercept[IllegalArgumentException](module.provideCleanupConfig(config))
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

  "SkatemapLiveModule.provideHubConfig" should {

    "fail when ttlSeconds config path is missing" in {
      val config = ConfigFactory.parseString("""
        skatemap.hub.cleanupIntervalSeconds = 60
        skatemap.hub.bufferSize = 128
      """)
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException](module.provideHubConfig(config))
      exception.getMessage should include("Required configuration missing: skatemap.hub.ttlSeconds")
    }

    "fail when cleanupIntervalSeconds config path is missing" in {
      val config = ConfigFactory.parseString("""
        skatemap.hub.ttlSeconds = 300
        skatemap.hub.bufferSize = 128
      """)
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException](module.provideHubConfig(config))
      exception.getMessage should include("Required configuration missing: skatemap.hub.cleanupIntervalSeconds")
    }

    "fail when both config paths are missing" in {
      val config = ConfigFactory.empty()
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException](module.provideHubConfig(config))
      exception.getMessage should include("Required configuration missing")
    }

    "fail when ttlSeconds is zero" in {
      val config = ConfigFactory.parseString("""
        skatemap.hub.ttlSeconds = 0
        skatemap.hub.cleanupIntervalSeconds = 60
        skatemap.hub.bufferSize = 128
      """)
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException](module.provideHubConfig(config))
      exception.getMessage should include(
        "Invalid configuration: skatemap.hub.ttlSeconds=0 (must be positive)"
      )
    }

    "fail when ttlSeconds is negative" in {
      val config = ConfigFactory.parseString("""
        skatemap.hub.ttlSeconds = -300
        skatemap.hub.cleanupIntervalSeconds = 60
        skatemap.hub.bufferSize = 128
      """)
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException](module.provideHubConfig(config))
      exception.getMessage should include(
        "Invalid configuration: skatemap.hub.ttlSeconds=-300 (must be positive)"
      )
    }

    "fail when cleanupIntervalSeconds is zero" in {
      val config = ConfigFactory.parseString("""
        skatemap.hub.ttlSeconds = 300
        skatemap.hub.cleanupIntervalSeconds = 0
        skatemap.hub.bufferSize = 128
      """)
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException](module.provideHubConfig(config))
      exception.getMessage should include(
        "Invalid configuration: skatemap.hub.cleanupIntervalSeconds=0 (must be positive)"
      )
    }

    "fail when cleanupIntervalSeconds is negative" in {
      val config = ConfigFactory.parseString("""
        skatemap.hub.ttlSeconds = 300
        skatemap.hub.cleanupIntervalSeconds = -60
        skatemap.hub.bufferSize = 128
      """)
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException](module.provideHubConfig(config))
      exception.getMessage should include(
        "Invalid configuration: skatemap.hub.cleanupIntervalSeconds=-60 (must be positive)"
      )
    }

    "fail when bufferSize config path is missing" in {
      val config = ConfigFactory.parseString("""
        skatemap.hub.ttlSeconds = 300
        skatemap.hub.cleanupIntervalSeconds = 60
      """)
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException](module.provideHubConfig(config))
      exception.getMessage should include("Required configuration missing: skatemap.hub.bufferSize")
    }

    "fail when bufferSize is zero" in {
      val config = ConfigFactory.parseString("""
        skatemap.hub.ttlSeconds = 300
        skatemap.hub.cleanupIntervalSeconds = 60
        skatemap.hub.bufferSize = 0
      """)
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException](module.provideHubConfig(config))
      exception.getMessage should include(
        "Invalid configuration: skatemap.hub.bufferSize=0 (must be positive)"
      )
    }

    "fail when bufferSize is negative" in {
      val config = ConfigFactory.parseString("""
        skatemap.hub.ttlSeconds = 300
        skatemap.hub.cleanupIntervalSeconds = 60
        skatemap.hub.bufferSize = -128
      """)
      val module = new SkatemapLiveModule

      val exception = intercept[IllegalArgumentException](module.provideHubConfig(config))
      exception.getMessage should include(
        "Invalid configuration: skatemap.hub.bufferSize=-128 (must be positive)"
      )
    }

    "use configured values when all are present and positive" in {
      val config = ConfigFactory.parseString("""
        skatemap.hub.ttlSeconds = 150
        skatemap.hub.cleanupIntervalSeconds = 30
        skatemap.hub.bufferSize = 256
      """)
      val module = new SkatemapLiveModule

      val hubConfig = module.provideHubConfig(config)
      hubConfig.ttl shouldBe 150.seconds
      hubConfig.cleanupInterval shouldBe 30.seconds
      hubConfig.bufferSize shouldBe 256
    }

  }

}
