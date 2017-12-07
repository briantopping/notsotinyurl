package sample

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}

object ShardingApp {
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
    ports.foreach { port =>
      // Override the configuration of the port
      val config = getConfig(port, "Backend")

      // Create an Akka system
      val system = ActorSystem("ShardedSystem", config)

      Shard.startShardRegion(system)
    }

    // create the web server with the services to dispatch to
    val config = ShardingApp.getConfig("0", "Frontend")
    val system = ActorSystem("ShardedSystem", config)
    system.actorOf(WebServer.props(), "frontend")
  }

  def getConfig(port: String, role: String): Config = {
    ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)
      .withFallback(ConfigFactory.parseString(s"""akka.cluster.roles=["$role"]"""))
      .withFallback(ConfigFactory.load())
  }
}

