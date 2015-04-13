package com.karasiq.proxychain.app

import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}
import akka.io.Tcp.Bind
import akka.io.{IO, Tcp}
import akka.kernel.Bootable

class Boot extends Bootable {
  val actorSystem = ActorSystem("ProxyChain", AppConfig())

  override def startup(): Unit = {
    val cfg = AppConfig().getConfig("proxyChain")
    val (host, port) = (cfg.getString("host"), cfg.getInt("port"))

    // Start server
    val server = actorSystem.actorOf(Props[Server])
    IO(Tcp)(actorSystem).tell(Bind(server, new InetSocketAddress(host, port)), server)
  }

  override def shutdown(): Unit = {
    actorSystem.shutdown()
  }
}
