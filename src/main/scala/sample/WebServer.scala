package sample

import akka.actor.{ActorRef, ActorSystem, Terminated}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.http.scaladsl.settings
import ShardingApp

import scala.concurrent.Future

// Server definition
class WebServer(services: Seq[ActorRef]) extends HttpApp {
  override def routes: Route =
    path("hello") {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
      }
    }
}

object WebServer {
  def apply(services: Seq[ActorRef]): Future[Terminated] = {
    val config = ShardingApp.getConfig("0")
    val system = ActorSystem("ShardingSystem", config)
    val server = new WebServer(services)
    server.startServer("localhost", 8080, settings.ServerSettings(config), system)
    system.terminate()
  }
}

