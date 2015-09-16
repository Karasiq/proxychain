package com.karasiq.proxychain.app

import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.{ServerSocketChannel, SocketChannel}
import java.util.concurrent.Executors

import akka.actor._
import akka.event.Logging
import com.karasiq.proxychain.AppConfig
import com.karasiq.tls.TLSServerWrapper
import org.apache.commons.io.IOUtils

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, control}

class Server(cfg: AppConfig) extends Actor with ActorLogging {
  import akka.io.Tcp._

  def receive = {
    case Bound(address) ⇒
      log.info("Proxy server running on {}", address)

    case CommandFailed(_: Bind) ⇒
      context.stop(self)

    case c @ Connected(remote, local) ⇒ // New connection accepted
      val handler = context.actorOf(Props(classOf[Handler], cfg, remote))
      val connection = sender()
      connection ! Register(handler)
  }
}

// TLS tamper
class TLSServer(address: InetSocketAddress, cfg: AppConfig) extends Actor with ActorLogging {
  import akka.io.Tcp._
  val serverSocket = ServerSocketChannel.open()
  private case class Accepted(socket: SocketChannel)

  private val tlsConfig = AppConfig.tlsConfig()

  private val acceptor = ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    super.preStart()
    serverSocket.bind(address)
    acceptor.execute(new Runnable {
      override def run(): Unit = {
        control.Exception.ignoring(classOf[IOException]) {
          while (serverSocket.isOpen) {
            self ! Accepted(serverSocket.accept())
          }
        }
      }
    })
    log.info("TLS-proxy server running on {}", address)
  }


  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    super.postStop()
    IOUtils.closeQuietly(serverSocket)
    acceptor.shutdown()
  }

  def receive = {
    case Accepted(socket) ⇒ // New connection accepted
      import context.dispatcher
      val handler = context.actorOf(Props(classOf[Handler], cfg, socket.getRemoteAddress.asInstanceOf[InetSocketAddress]))
      val catcher = control.Exception.allCatch.withApply { exc ⇒
        context.stop(handler)
        IOUtils.closeQuietly(socket)
      }

      catcher {
        val log = Logging(context.system, handler)

        val tlsSocket = Promise[SocketChannel]()
        tlsSocket.future.onComplete {
          case Success(sc) ⇒
            val actor = context.actorOf(Props(classOf[TLSHandlerTamper], sc))
            actor ! Register(handler)

          case Failure(exc) ⇒
            log.error(exc, "Error opening TLS socket")
            handler ! ErrorClosed
            IOUtils.closeQuietly(socket)
        }

        val serverWrapper = new TLSServerWrapper(tlsConfig.keySet, tlsConfig.clientAuth, tlsConfig.verifier) {
          override protected def onInfo(message: String): Unit = {
            log.debug(message)
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