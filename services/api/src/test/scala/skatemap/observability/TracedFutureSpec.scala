package skatemap.observability

// import io.opentelemetry.api.trace.{SpanKind, StatusCode, Tracer}
import io.opentelemetry.api.trace.{StatusCode, Tracer}
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
// import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.{ExecutionContext, Future}
// import scala.concurrent.ExecutionContext.Implicits.global
// import scala.util.{Failure, Success}

class TracedFutureSpec extends AnyFlatSpec with Matchers with ScalaFutures with BeforeAndAfterEach {

  implicit val ex: ExecutionContext = ExecutionContext.global

  var spanExporter: InMemorySpanExporter = _
  var tracerProvider: SdkTracerProvider  = _
  implicit var tracer: Tracer            = _

  override def beforeEach(): Unit = {
    spanExporter = InMemorySpanExporter.create()
    tracerProvider = SdkTracerProvider
      .builder()
      .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
      .build()
    tracer = tracerProvider.get("test-tracer")
  }

  override def afterEach(): Unit = {
    tracerProvider.close()
    spanExporter.reset()
  }

  "TracedFuture.traced" should "end span with OK status when Future succeeds" in {
    val result = TracedFuture.traced("test-span") {
      Future.successful(42)
    }

    whenReady(result) { value =>
      value shouldBe 42

      val spans = spanExporter.getFinishedSpanItems
      spans.size() shouldBe 1

      val span = spans.get(0)
      span.getName shouldBe "test-span"
      span.getStatus.getStatusCode shouldBe StatusCode.OK
    }
  }

  it should "record exception and end span with ERROR status when Future fails" in {
    val testException = new RuntimeException("test failure")

    val result = TracedFuture.traced("failing-span") {
      Future.failed(testException)
    }

    whenReady(result.failed) { exception =>
      exception shouldBe testException

      val spans = spanExporter.getFinishedSpanItems
      spans.size() shouldBe 1

      val span = spans.get(0)
      span.getName shouldBe "failing-span"
      span.getStatus.getStatusCode shouldBe StatusCode.ERROR

      val events = span.getEvents
      events.size() shouldBe 1
      events.get(0).getName shouldBe "exception"
    }
  }

  it should "maintain parent-child span relationships across Future boundaries" in {
    val parentSpan  = tracer.spanBuilder("parent-span").startSpan()
    val parentScope = parentSpan.makeCurrent()

    val result = TracedFuture.traced("child-span") {
      Future.successful("done")
    }

    whenReady(result) { _ =>
      parentScope.close()
      parentSpan.end()

      val spans = spanExporter.getFinishedSpanItems
      spans.size() shouldBe 2

      val childSpanData  = spans.get(0)
      val parentSpanData = spans.get(1)

      childSpanData.getName shouldBe "child-span"
      childSpanData.getParentSpanContext.getSpanId shouldBe parentSpanData.getSpanId
    }
  }
}
