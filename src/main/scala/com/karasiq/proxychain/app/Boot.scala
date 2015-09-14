package com.karasiq.proxychain.app

import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}
import akka.event.Logging
import akka.io.Tcp.Bind
import akka.io.{IO, Tcp}
import akka.kernel.Bootable
import com.karasiq.fileutils.PathUtils._
import com.karasiq.proxychain.AppConfig
import com.karasiq.proxychain.script.ScriptEngine
import com.typesafe.config.Config

final class Boot extends Bootable {
  val configFile: Config = AppConfig.externalConfig()
  val actorSystem: ActorSystem = ActorSystem("ProxyChain", configFile.resolve())

  override def startup(): Unit = {
    val cfg = configFile.getConfig("proxyChain")
    val host = cfg.getString("host")

    val config: AppConfig = asPath(cfg.getString("script")) match {
      case script if script.isRegularFile ⇒
        actorSystem.log.debug("Loading script: {}", script)
        val scriptEngine = new ScriptEngine(Logging.getLogger(actorSystem, "ScriptEngine"))
        scriptEngine.asConfig(script)

      case _ ⇒
        AppConfig(cfg) // Default
    }

    // Start server

    val port = cfg.getInt("port")
    if (port != 0) {
      val server = actorSystem.actorOf(Props(classOf[Server], config))
      IO(Tcp)(actorSystem).tell(Bind(server, new InetSocketAddress(host, port)), server)
    }

    val tlsPort = cfg.getInt("tls-port")
    if (tlsPort != 0) {
      val server = actorSystem.actorOf(Props(classOf[TLSServer], new InetSocketAddress(host, tlsPort), config))
    }
  }

  override def shutdown(): Unit = {
    actorSystem.shutdown()
  }
}
