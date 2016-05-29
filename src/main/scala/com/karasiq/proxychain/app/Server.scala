package com.karasiq.proxychain.app

import akka.actor._
import com.karasiq.proxychain.AppConfig

import scala.language.postfixOps

private[app] object Server {
  def props(cfg: AppConfig, tls: Boolean = false): Props = {
    Props(classOf[Server], cfg, tls)
  }
}

private[app] class Server(cfg: AppConfig, tls: Boolean) extends Actor with ActorLogging {
  import akka.io.Tcp._

  def receive = {
    case Bound(address) ⇒
      log.info("{} server running on {}", if (tls) "TLS-Proxy" else "Proxy", address)

    case CommandFailed(_: Bind) ⇒
      context.stop(self)

    case c @ Connected(remote, local) ⇒ // New connection accepted
      val connection = sender()
      val handler = context.actorOf(Props(if (tls) classOf[TLSHandler] else classOf[Handler], connection, cfg, remote))
      connection ! Register(handler)
  }
}

