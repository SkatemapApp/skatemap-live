package controllers

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.test.Helpers._

import scala.concurrent.Future

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 *
 * For more information, see https://www.playframework.com/documentation/latest/ScalaTestingWithScalaTest
 */
class HomeControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  private implicit val actorSystem: ActorSystem = ActorSystem()
  private implicit val materializer: ActorMaterializer = ActorMaterializer()

  "HomeController GET" should {

    "render the index page from a new instance of controller" in {
      val controller = new HomeController(stubControllerComponents())
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
    }

    "render the index page from the application" in {
      val controller = inject[HomeController]
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
    }

    "render the index page from the router" in {
      val request = FakeRequest(GET, "/")
      val home = route(app, request)

      status(home) mustBe OK
    }

    "update the location from a new instance of controller" in {
      val skatingEventId = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString
      val controller = new HomeController(stubControllerComponents())
      val home = controller.index().apply(FakeRequest(PUT, s"/skatingEvents/$skatingEventId/skaters/$skaterId")
        .withBody(s"""{"latlng": [0.0, 50.0]}"""))

      status(home) mustBe OK
    }

    "update the location from the application" in {
      val skatingEventId = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString
      val controller = inject[HomeController]
      val home = controller.index().apply(FakeRequest(PUT, s"/skatingEvents/$skatingEventId/skaters/$skaterId")
        .withBody(s"""{"latlng": [0.0, 50.0]}"""))

      status(home) mustBe OK
    }

    "update the location from the router" in {
      val skatingEventId = UUID.randomUUID().toString
      val skaterId = UUID.randomUUID().toString
      val request = FakeRequest(PUT, s"/skatingEvents/$skatingEventId/skaters/$skaterId")
        .withBody(s"""{"latlng": [0.0, 50.0]}""")
      val home = route(app, request).get

      status(home) mustBe OK
    }
  }
}
