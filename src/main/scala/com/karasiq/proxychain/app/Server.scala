package com.karasiq.proxychain.app

import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.{ServerSocketChannel, SocketChannel}
import java.util.concurrent.Executors

import akka.actor._
import akka.event.Logging
import akka.io.Tcp
import com.karasiq.networkutils.SocketChannelWrapper
import com.karasiq.tls.{TLS, TLSCertificateVerifier, TLSKeyStore, TLSServerWrapper}

import scala.concurrent.{ExecutionContext, Promise}
import scala.util.control

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
  val config = AppConfig.externalConfig().getConfig("proxyChain")
  val keyName = config.getString("tls-key")
  val clientAuth = config.getBoolean("tls-client-auth")
  val keySet = TLS.KeySet(keyStore, keyName)

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
      import context.dispatcher
      val tlsTamper = Promise[ActorRef]()
      val handler = context.actorOf(Props(classOf[Handler], cfg))
      val catcher = control.Exception.allCatch.withApply { exc ⇒
        if (!tlsTamper.tryFailure(exc)) {
          tlsTamper.future.onSuccess {
            case ar: ActorRef ⇒
              context.stop(ar)
          }
        }
        context.stop(handler)
        socket.close()
      }

      catcher {
        val serverWrapper = new TLSServerWrapper(keySet, clientAuth, new TLSCertificateVerifier()) {
          private val log = Logging(context.system, handler)

          override protected def onInfo(message: String): Unit = {
            log.debug(message)
          }

          override protected def onHandshakeFinished(): Unit = {
            log.debug("TLS handhake finished")
            tlsTamper.future.onSuccess {
              case tamper ⇒
                tamper ! ResumeReading
            }
          }

          override protected def onError(message: String, exc: Throwable): Unit = {
            log.error(exc, message)
          }
        }

        val tlsSocket = serverWrapper(socket)

        val tamperActor = context.actorOf(Props(new Actor with Stash {
          @throws[Exception](classOf[Exception])
          override def preStart(): Unit = {
            val catcher = control.Exception.catching(classOf[IOException]).withApply { _ ⇒
              self ! ErrorClosed
            }

            catcher {
              super.preStart()
              context.watch(handler)
              SocketChannelWrapper.register(tlsSocket, self)
            }
          }

          override def postStop(): Unit = {
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

            case Terminated(_) ⇒
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
              tlsSocket.write(data.toByteBuffer)
              if (ack != Tcp.NoAck) sender() ! ack

            case event: Tcp.Event ⇒
              handler ! event
          }

          override def receive: Receive = onClose.orElse(readSuspended).orElse(streaming)
        }))
        if (!tlsTamper.trySuccess(tamperActor)) {
          context.stop(tamperActor)
        }
      }
  }
}