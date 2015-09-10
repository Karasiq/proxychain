package com.karasiq.proxychain.app

import java.net.InetSocketAddress
import java.nio.channels.{ServerSocketChannel, SocketChannel}
import java.util.concurrent.Executors

import akka.actor._
import akka.event.Logging
import com.karasiq.tls.{TLS, TLSCertificateVerifier, TLSKeyStore, TLSServerWrapper}

import scala.concurrent.{ExecutionContext, Future, Promise}
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
      val handler = context.actorOf(Props(classOf[Handler], cfg))
      val catcher = control.Exception.allCatch.withApply { exc ⇒
        context.stop(handler)
        socket.close()
      }

      catcher {
        val log = Logging(context.system, handler)

        val tlsSocket = Promise[SocketChannel]()
        tlsSocket.future.onFailure {
          case exc ⇒
            log.error(exc, "Error opening TLS socket")
            handler ! ErrorClosed
        }

        val serverWrapper = new TLSServerWrapper(keySet, clientAuth, new TLSCertificateVerifier()) {
          override protected def onInfo(message: String): Unit = {
            log.debug(message)
          }

          override protected def onHandshakeFinished(): Unit = {
            log.debug("TLS handhake finished")
            tlsSocket.future.onSuccess { case socket: SocketChannel ⇒
              val actor = context.actorOf(Props(classOf[TLSHandlerTamper], socket))
              actor ! Register(handler)
            }
          }

          override protected def onError(message: String, exc: Throwable): Unit = {
            log.error(exc, message)
          }
        }

        tlsSocket.completeWith(Future {
          serverWrapper(socket)
        })
      }
  }
}