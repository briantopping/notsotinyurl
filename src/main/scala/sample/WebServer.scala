package sample

import java.util.Random

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.http.scaladsl.settings
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._

final case class BackendRegistration(ids: Seq[String])

// Server definition
class WebServer extends HttpApp with Actor with ActorLogging {
  var services = Map.empty[String, ActorRef]
  val rnd      = new Random

  override def routes: Route = {
    implicit val timeout: Timeout = Timeout(2.seconds)
    path("shorten") {
      get {
        parameter('key) { key =>
          services.get(key) match {
            case Some(ref) =>
              val value = ref ? GetUrl(key)
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"<h1>value for $key is $value</h1>"))
            case None      =>
              complete(StatusCodes.NotFound)
          }
        } ~
        post {
          parameter('url) { url =>
            // Todo: This should use a ShortestMailbox Router
            val ref = services.values.toVector(rnd.nextInt(services.size))
            val key = ref ? PostUrl(url)
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"<h1>key for $url is $key</h1>"))
          }
        }
      }
    }
  }

  override def receive: Receive = {
    case BackendRegistration(ids) =>
      context.watch(sender)
      ids.foreach { id =>
        services = services + (id -> sender)
      }
    case Terminated(ref)          =>
      services = services.filter {
        case (_, r) if ref == r => false
        case _                  => true
      }
  }

  override def preStart(): Unit

  = {
    val system = context.system
    val cluster = Cluster(system)
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])

    // Todo: this method blocks, waiting for keyboard input. Not production ready
    startServer("localhost", 8080, settings.ServerSettings(system.settings.config), system)
    system.terminate()
  }
}

object WebServer {
  def props(): Props = Props[WebServer]
}