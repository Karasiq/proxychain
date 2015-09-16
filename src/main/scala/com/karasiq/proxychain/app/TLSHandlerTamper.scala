package com.karasiq.proxychain.app

import java.nio.channels.SocketChannel

import akka.actor._
import akka.io.Tcp
import akka.io.Tcp._
import com.karasiq.networkutils.SocketChannelWrapper
import org.apache.commons.io.IOUtils

import scala.util.control

final class TLSHandlerTamper(tlsSocket: SocketChannel) extends Actor with ActorLogging with Stash {
  private var handler: Option[ActorRef] = None
  private val writer: ActorRef = context.actorOf(Props(SocketChannelWrapper.writer(tlsSocket)), "tlsTamperWriter")

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    val catcher = control.Exception.allCatch.withApply { exc ⇒
      log.error(exc, "TLS initialization error")
      self ! ErrorClosed
    }

    catcher {
      super.preStart()
      SocketChannelWrapper.register(tlsSocket, self)
    }
  }

  override def postStop(): Unit = {
    log.debug("TLS tamper stopped: {}", tlsSocket)
    SocketChannelWrapper.unregister(tlsSocket)
    IOUtils.closeQuietly(tlsSocket)
    super.postStop()
  }

  def onClose: Receive = {
    case c @ Tcp.Closed ⇒
      handler.foreach(_ ! c)
      context.stop(self)

    case c @ Tcp.Close ⇒
      sender() ! ConfirmedClosed
      context.stop(self)

    case Terminated(_) ⇒
      context.stop(self)

    case Register(newHandler, _, _) ⇒
      handler.foreach(context.unwatch)
      context.watch(newHandler)
      handler = Some(newHandler)
      self ! ResumeReading
  }

  def readSuspended: Receive = {
    case Received(data) ⇒
      stash()
  }

  def readResumed: Receive = {
    case r @ Received(data) ⇒
      handler.foreach(_ ! r)
  }

  def streaming: Receive = {
    case SuspendReading ⇒
      context.become(onClose.orElse(readSuspended).orElse(streaming))

    case ResumeReading ⇒
      unstashAll()
      context.become(onClose.orElse(readResumed).orElse(streaming))

    case w: Write ⇒
      writer ! w

    case event: Tcp.Event ⇒
      handler.foreach(_ ! event)
  }

  override def receive: Receive = onClose.orElse(readSuspended).orElse(streaming)
}
