package skatemap.core

import org.apache.pekko.actor.ActorSystem
import play.api.inject.ApplicationLifecycle

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

@Singleton
class CleanupService @Inject() (
  store: LocationStore,
  actorSystem: ActorSystem,
  lifecycle: ApplicationLifecycle,
  ec: ExecutionContext
) {
  implicit val executionContext: ExecutionContext = ec

  private val cancellable = actorSystem.scheduler.scheduleWithFixedDelay(
    initialDelay = 10.seconds,
    delay = 10.seconds
  )(() => store.cleanupAll())

  lifecycle.addStopHook(() => Future.successful(cancellable.cancel()))
}
