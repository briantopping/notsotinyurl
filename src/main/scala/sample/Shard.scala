package sample

import akka.actor.ReceiveTimeout
import akka.persistence.PersistentActor
import scala.concurrent.duration._

// commands
final case class PostUrl(url: String)
final case class GetUrl(key: String)

case object Stop

// events
final case class Url(url: String)

class Shard extends PersistentActor {
  import akka.cluster.sharding.ShardRegion.Passivate

  context.setReceiveTimeout(120.seconds)

  // self.path.name is the entity identifier (utf-8 URL-encoded)
  override def persistenceId: String = "Shard-" + self.path.name

  var store = Map.empty[String, String]

  def updateState(event: Url): Unit =
    store += (persistenceId + store.size -> event.url)

  override def receiveRecover: Receive = {
    case evt: Url => updateState(evt)
  }

  override def receiveCommand: Receive = {
    case PostUrl(url)   =>
      val key = persistenceId + store.size
      persist(Url(url))(updateState)
      sender() ! key
    case GetUrl(key)    => store.get(key)
    case ReceiveTimeout => context.parent ! Passivate(stopMessage = Stop)
    case Stop           => context.stop(self)
  }
}