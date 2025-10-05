package skatemap.core

import org.apache.pekko.actor.ActorSystem
import play.api.inject.ApplicationLifecycle
import play.api.Logger

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class CleanupService @Inject() (
  store: LocationStore,
  config: CleanupConfig,
  actorSystem: ActorSystem,
  lifecycle: ApplicationLifecycle,
  ec: ExecutionContext
) {
  implicit val executionContext: ExecutionContext = ec

  private val logger = Logger(this.getClass)

  logger.info(
    s"CleanupService initialised with initialDelay=${config.initialDelay.toString()}, " +
      s"interval=${config.interval.toString()}"
  )

  private val cancellable = actorSystem.scheduler.scheduleWithFixedDelay(
    initialDelay = config.initialDelay,
    delay = config.interval
  ) { () =>
    scala.util.Try(store.cleanupAll()) match {
      case Success(count) =>
        logger.info(s"Cleanup completed: removed ${count.toString()} locations")
      case Failure(error) =>
        logger.error(s"Cleanup failed: ${error.getMessage}", error)
    }
  }

  lifecycle.addStopHook { () =>
    logger.info("Stopping CleanupService")
    Future.successful(cancellable.cancel())
  }
}
