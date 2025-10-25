package skatemap.core

import org.apache.pekko.actor.ActorSystem
import play.api.inject.ApplicationLifecycle
import play.api.Logger

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class BroadcasterCleanupService @Inject() (
  broadcaster: InMemoryBroadcaster,
  config: HubConfig,
  actorSystem: ActorSystem,
  lifecycle: ApplicationLifecycle,
  ec: ExecutionContext
) {
  implicit val executionContext: ExecutionContext = ec

  private val logger = Logger(this.getClass)

  logger.info(
    s"BroadcasterCleanupService initialised with ttl=${config.ttl.toString()}, " +
      s"cleanupInterval=${config.cleanupInterval.toString()}"
  )

  private val cancellable = actorSystem.scheduler.scheduleWithFixedDelay(
    initialDelay = config.cleanupInterval,
    delay = config.cleanupInterval
  ) { () =>
    scala.util.Try(broadcaster.cleanupUnusedHubs(config.ttl.toMillis)) match {
      case Success(count) =>
        if (count > 0) {
          logger.info(s"Hub cleanup completed: removed ${count.toString()} hubs")
        }
      case Failure(error) =>
        logger.error(s"Hub cleanup failed: ${error.getMessage}", error)
    }
  }

  lifecycle.addStopHook { () =>
    logger.info("Stopping BroadcasterCleanupService")
    Future.successful(cancellable.cancel())
  }
}
