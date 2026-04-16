package skatemap.observability

import io.opentelemetry.api.trace.{StatusCode, Tracer}
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Null",
    "org.wartremover.warts.GlobalExecutionContext",
    "org.wartremover.warts.Var",
    "org.wartremover.warts.Throw",
    "org.wartremover.warts.IterableOps"
  )
)
class TracedFutureSpec extends AnyFlatSpec with Matchers with ScalaFutures with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global

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

  private def getFinishedSpans: Seq[SpanData] =
    spanExporter.getFinishedSpanItems.asScala.toSeq

  "TracedFuture.traced" should "end span with OK status when Future succeeds" in {
    val result = TracedFuture.traced("test-span") {
      Future.successful(42)
    }

    whenReady(result) { value =>
      value shouldBe 42

      val spans = getFinishedSpans
      spans should have size 1

      val span = spans.head
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

      val spans = getFinishedSpans
      spans should have size 1

      val span = spans.head
      span.getName shouldBe "failing-span"
      span.getStatus.getStatusCode shouldBe StatusCode.ERROR

      val events = span.getEvents.asScala
      events should have size 1
      events.head.getName shouldBe "exception"
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

      val spans = getFinishedSpans
      spans should have size 2

      val Seq(childSpanData, parentSpanData) = spans
      childSpanData.getName shouldBe "child-span"
      childSpanData.getParentSpanContext.getSpanId shouldBe parentSpanData.getSpanId
    }
  }

  it should "record exception when Future creation throws synchronously" in {
    val testException = new RuntimeException("creation failed")

    val result = TracedFuture.traced("sync-failure") {
      throw testException
    }

    whenReady(result.failed) { exception =>
      exception shouldBe testException

      val spans = getFinishedSpans
      spans should have size 1

      val span = spans.head
      span.getName shouldBe "sync-failure"
      span.getStatus.getStatusCode shouldBe StatusCode.ERROR

      val events = span.getEvents.asScala
      events should have size 1
      events.head.getName shouldBe "exception"
    }
  }

  it should "restore calling thread context after traced Future completes" in {
    val rootSpan  = tracer.spanBuilder("root").startSpan()
    val rootScope = rootSpan.makeCurrent()

    val future1 = TracedFuture.traced("operation-1") {
      Future.successful("done1")
    }

    Await.result(future1, 1.second)

    val future2 = TracedFuture.traced("operation-2") {
      Future.successful("done2")
    }

    Await.result(future2, 1.second)

    rootScope.close()
    rootSpan.end()

    val spans = getFinishedSpans
    spans should have size 3

    val Seq(op1, op2, root) = spans

    op1.getParentSpanContext.getSpanId shouldBe root.getSpanId
    op2.getParentSpanContext.getSpanId shouldBe root.getSpanId

    op2.getParentSpanContext.getSpanId should not be op1.getSpanId
  }
}
