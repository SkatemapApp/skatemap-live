package skatemap.observability

import io.opentelemetry.api.trace.{StatusCode, Tracer}
import io.opentelemetry.context.Context

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object TracedFuture {
  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  def traced[A](spanName: String)(block: => Future[A])(implicit
    tracer: Tracer,
    ec: ExecutionContext
  ): Future[A] = {
    val parentContext = Context.current()
    val span          = tracer.spanBuilder(spanName).startSpan()
    val scope         = parentContext.`with`(span).makeCurrent()

    try
      block.andThen {
        case Success(_) =>
          span.setStatus(StatusCode.OK)
          span.end()
          scope.close()
        case Failure(exception) =>
          span.recordException(exception)
          span.setStatus(StatusCode.ERROR)
          span.end()
          scope.close()
      }
    catch {
      case exception: Throwable =>
        span.recordException(exception)
        span.setStatus(StatusCode.ERROR)
        span.end()
        scope.close()
        Future.failed(exception)
    }
  }
}
