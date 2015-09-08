package com.karasiq.proxychain.app

import java.net.InetSocketAddress
import java.nio.channels.{ServerSocketChannel, SocketChannel}
import java.util.concurrent.Executors

import akka.actor.{Actor, ActorLogging, Props, Stash}
import akka.io.Tcp
import com.karasiq.networkutils.SocketChannelWrapper
import com.karasiq.tls.{TLS, TLSKeyStore}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, control}

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

// TLS tamper
private[app] final class TLSServer(address: InetSocketAddress, cfg: AppConfig) extends Actor with ActorLogging {
  import akka.io.Tcp._
  val serverSocket = ServerSocketChannel.open()
  private case class Accepted(socket: SocketChannel)

  val keyStore = new TLSKeyStore()
  val keyName = AppConfig.externalConfig().getString("proxyChain.tls-key")

  assert(keyStore.contains(keyName), "ProxyChain server TLS key not found")
  val certificate = keyStore.getCertificateChain(keyName)
  val key = keyStore.getKey(keyName)

  private val acceptor = ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    super.preStart()
    serverSocket.bind(address)
    acceptor.execute(new Runnable {
      override def run(): Unit = {
        while (serverSocket.isOpen) {
          self ! Accepted(serverSocket.accept())
        }
      }
    })
    log.info("TLS-proxy server running on {}", address)
  }


  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    super.postStop()
    serverSocket.close()
    acceptor.shutdown()
  }

  def receive = {
    case Accepted(socket) ⇒ // New connection accepted
      val handler = context.actorOf(Props(classOf[Handler], cfg))
      val catcher = control.Exception.allCatch.withApply { exc ⇒
        context.stop(handler)
        socket.close()
        if (log.isDebugEnabled) log.error(exc, "TLS server error")
      }

      catcher {
        val tlsSocket = TLS.serverWrapper(socket, certificate, key)

        val tlsTamper = context.actorOf(Props(new Actor with Stash {
          private implicit val writeEc = ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())

          @throws[Exception](classOf[Exception])
          override def preStart(): Unit = {
            super.preStart()
            SocketChannelWrapper.register(tlsSocket, self)
            context.watch(handler)
          }


          override def postStop(): Unit = {
            writeEc.shutdown()
            SocketChannelWrapper.unregister(tlsSocket)
            tlsSocket.close()
            super.postStop()
          }

          def onClose: Receive = {
            case c @ Tcp.Closed ⇒
              handler ! c
              context.stop(self)

            case c @ Tcp.Close ⇒
              sender() ! ConfirmedClosed
              context.stop(self)
          }

          def readSuspended: Receive = {
            case Received(data) ⇒
              stash()
          }

          def readResumed: Receive = {
            case r @ Received(data) ⇒
              handler ! r
          }

          def streaming: Receive = {
            case SuspendReading ⇒
              context.become(onClose.orElse(readSuspended).orElse(streaming))

            case ResumeReading ⇒
              unstashAll()
              context.become(onClose.orElse(readResumed).orElse(streaming))

            case w @ Write(data, ack) ⇒
              val writer = sender()
              Future(tlsSocket.write(data.toByteBuffer)).onComplete {
                case Success(size) ⇒
                  if (ack != Tcp.NoAck) writer ! ack

                case Failure(exc) ⇒
                  writer ! CommandFailed(w)
              }

            case event: Tcp.Event ⇒
              handler ! event
          }

          override def receive: Receive = onClose.orElse(readResumed).orElse(streaming)
        }))
      }
  }
}