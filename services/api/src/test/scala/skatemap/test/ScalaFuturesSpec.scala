package skatemap.test

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}

/**
 * Shared trait providing ScalaFutures configuration for test specs.
 *
 * This trait provides a standardized PatienceConfig for all test specs using ScalaFutures, reducing duplication and
 * ensuring consistency across the test suite.
 */
trait ScalaFuturesSpec extends ScalaFutures {

  implicit val defaultPatience: PatienceConfig = PatienceConfig(
    timeout = Span(3, Seconds),
    interval = Span(50, Millis)
  )
}
