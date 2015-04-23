package com.karasiq.proxychain.app

import akka.actor.{Actor, ActorLogging, Props}

/**
 * SOCKS5 server
 */
private[app] final class Server(cfg: AppConfig) extends Actor with ActorLogging {
  import akka.io.Tcp._

  def receive = {
    case Bound(address) ⇒
      log.info("Proxy server running on {}", address)

    case CommandFailed(_: Bind) ⇒
      context.stop(self)

    case c @ Connected(remote, local) ⇒ // New connection accepted
      val handler = context.actorOf(Props(classOf[Handler], cfg))
      val connection = sender()
      connection ! Register(handler)
  }
}
