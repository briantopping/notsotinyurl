package sample

import akka.actor.{ActorRef, ActorSystem, Props, ReceiveTimeout, RootActorPath}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberUp, UnreachableMember}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
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

  val cluster = Cluster(context.system)

  override def receive: Receive = {
    super.receive orElse {
      case MemberUp(member) if member.hasRole("Frontend") =>
        context.actorSelection(RootActorPath(member.address) / "user" / "frontend") ! BackendRegistration
    }
  }

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
  }
  override def postStop(): Unit = cluster.unsubscribe(self)

}

object Shard {
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg@GetUrl(id)   => (id, msg)
    case msg@PostUrl(url) => (url, msg)
  }
  val numberOfShards                               = 100
  val extractShardId : ShardRegion.ExtractShardId  = {
    case PostUrl(url)                => (url.hashCode % numberOfShards).toString
    case GetUrl(id)                  => (id.hashCode % numberOfShards).toString
    case ShardRegion.StartEntity(id) =>
      // StartEntity is used by remembering entities feature
      (id.toLong % numberOfShards).toString
  }

  def shardFromId(id: String): Option[String] = "(\\d+)-.*".r.findFirstIn(id)
  def idFromUrl(url: String): String = s"${url.hashCode % numberOfShards}-"

  // Create an actor that starts the sharding and sends random messages
  def startShardRegion(system: ActorSystem): ActorRef = ClusterSharding(system).start(
    typeName = "Shard",
    entityProps = Props[Shard],
    settings = ClusterShardingSettings(system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId)
}