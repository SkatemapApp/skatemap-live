package skatemap.observability

import io.opentelemetry.api.trace.{StatusCode, Tracer}
import io.opentelemetry.context.Context

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object TracedFuture {
  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  def traced[A](spanName: String)(block: => Future[A])(implicit
    tracer: Tracer,
    ec: ExecutionContext
  ): Future[A] = {
    val parentContext   = Context.current()
    val span            = tracer.spanBuilder(spanName).startSpan()
    val contextWithSpan = parentContext.`with`(span)

    def completeSpan(result: Try[A]): Try[A] = {
      result match {
        case Success(_) =>
          span.setStatus(StatusCode.OK)
        case Failure(exception) =>
          span.recordException(exception)
          span.setStatus(StatusCode.ERROR)
      }
      span.end()
      result
    }

    Future {
      contextWithSpan.makeCurrent()
    }.flatMap { scope =>
      Try(block) match {
        case Success(futureResult) =>
          futureResult.transform { result =>
            scope.close()
            completeSpan(result)
          }
        case Failure(exception) =>
          scope.close()
          Future.fromTry(completeSpan(Failure(exception)))
      }
    }
  }
}
