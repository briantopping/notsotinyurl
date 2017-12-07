package sample

import akka.actor.{ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.typesafe.config.{Config, ConfigFactory}

object ShardingApp {
  val extractEntityId: ShardRegion.ExtractEntityId = {
    // FIXME: these need to return the ID of the shard with a regex
    case msg@GetUrl(id)  => (id.toString, msg)
    case msg@PostUrl(id) => (id.toString, msg)
  }
  val numberOfShards                               = 100
  val extractShardId : ShardRegion.ExtractShardId  = {
    // FIXME
    case PostUrl(id)                 => (id % numberOfShards).toString
    case GetUrl(id)                  => (id % numberOfShards).toString
    case ShardRegion.StartEntity(id) =>
      // StartEntity is used by remembering entities feature
      (id.toLong % numberOfShards).toString
  }
  def main(args: Array[String]): Unit = {
    if (args.isEmpty)
      startup(Seq("2551", "2552", "0"))
    else
      startup(args)
  }
  def startup(ports: Seq[String]): Unit = {
    // In a production application you wouldn't typically start multiple ActorSystem instances in the
    // same JVM, here we do it to easily demonstrate these ActorSytems (which would be in separate JVM's)
    // talking to each other.
    val services = ports.map { port =>
      // Override the configuration of the port
      val config = getConfig(port)

      // Create an Akka system
      val system = ActorSystem("ShardingSystem", config)

      // Create an actor that starts the sharding and sends random messages
      ClusterSharding(system).start(
        typeName = "Shard",
        entityProps = Props[Shard],
        settings = ClusterShardingSettings(system),
        extractEntityId = extractEntityId,
        extractShardId = extractShardId)
    }

    // create the web server with the services to dispatch to
    // FIXME the web server should take a message for shard regions when they are started
    WebServer(services)
  }

  def getConfig(port: String): Config = {
    ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)
      .withFallback(ConfigFactory.load())
  }
}

