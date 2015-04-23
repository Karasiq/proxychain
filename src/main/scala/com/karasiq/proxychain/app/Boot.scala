package com.karasiq.proxychain.app

import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}
import akka.io.Tcp.Bind
import akka.io.{IO, Tcp}
import akka.kernel.Bootable
import com.karasiq.fileutils.PathUtils._
import com.karasiq.proxychain.app.script.ScriptEngine

final class Boot extends Bootable {
  val actorSystem = ActorSystem("ProxyChain", AppConfig.externalConfig())

  override def startup(): Unit = {
    val cfg = AppConfig.externalConfig().getConfig("proxyChain")
    val (host, port) = (cfg.getString("host"), cfg.getInt("port"))

    val config: AppConfig = asPath(cfg.getString("script")) match {
      case script if script.isRegularFile ⇒
        actorSystem.log.debug("Using script: {}", script)
        ScriptEngine.asConfig(script)

      case _ ⇒
        AppConfig(cfg) // Default
    }

    // Start server
    val server = actorSystem.actorOf(Props(classOf[Server], config))
    IO(Tcp)(actorSystem).tell(Bind(server, new InetSocketAddress(host, port)), server)
  }

  override def shutdown(): Unit = {
    actorSystem.shutdown()
  }
}
