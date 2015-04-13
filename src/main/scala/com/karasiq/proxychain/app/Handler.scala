package com.karasiq.proxychain.app

import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.SocketChannel

import akka.actor.SupervisorStrategy.{Resume, Stop}
import akka.actor._
import akka.io.Tcp._
import akka.util.ByteString
import com.karasiq.networkutils.SocketChannelWrapper
import com.karasiq.networkutils.SocketChannelWrapper.ReadWriteOps
import com.karasiq.networkutils.http.HttpStatus
import com.karasiq.networkutils.http.headers.HttpHeader
import com.karasiq.networkutils.url.URLParser
import com.karasiq.parsers.ParserException
import com.karasiq.parsers.http.{HttpConnect, HttpMethod, HttpRequest, HttpResponse}
import com.karasiq.parsers.socks.SocksClient._
import com.karasiq.parsers.socks.SocksServer._
import com.karasiq.proxy.ProxyException
import com.karasiq.proxychain.ProxyChain
import org.apache.commons.io.IOUtils

import scala.collection.JavaConversions._
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
 * SOCKS5 connection handler
 */
class Handler extends Actor with ActorLogging {
  import context.dispatcher

  private val firewall = Firewall(AppConfig().getConfig("proxyChain"))

  private val socket: Promise[SocketChannel] = Promise() // Connection through proxy chain

  private def write(connection: ActorRef, bytes: Seq[Byte]) = connection ! Write(ByteString(bytes.toArray))

  private def openConnection(address: InetSocketAddress): Future[SocketChannel] = {
    val cfg = AppConfig().getConfig("proxyChain")

    def loadChain(): ProxyChain = ProxyChain.config(cfg)
    val maxChains: Int = cfg.getInt("maxTriedChains")

    val chains = Seq.fill(maxChains)(loadChain()).distinct
    log.debug("Trying connect to {} through chains: {}", address, chains)

    val futures = chains.map { chain ⇒
      val future = Future(chain.connection(address))
      future.onComplete {
        case Success(sc) ⇒
          if (socket.trySuccess(sc)) {
            if (chain.proxies.isEmpty) log.warning("Proxy chain not defined, direct connection to {} opened", address)
            else log.info("Opened connection through proxy chain {} to {}", chain, address)
          } else {
            IOUtils.closeQuietly(sc)
          }
        case Failure(exc) ⇒
          if (log.isDebugEnabled) log.error(exc, "Connect through {} to {} failed: {}", chain, address)
      }
      future
    }

    Future.sequence(futures).onComplete {
      case _ ⇒
        if (socket.tryFailure(new ProxyException("Connection through all proxy chains failed"))) {
          log.error("Proxy chained connection to {} failed", address)
        }
    }

    socket.future
  }

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    // Close connection if opened
    if (!socket.tryFailure(new IllegalStateException("Actor already stopped"))) socket.future.onSuccess {
      case socketChannel ⇒
        log.debug("Closing proxy chain connection: {}", socketChannel)
        SocketChannelWrapper.unregister(socketChannel)
        IOUtils.closeQuietly(socketChannel)
    }

    super.postStop()
  }

  private def onClose: Receive = {
    case _: ConnectionClosed ⇒
      log.debug("Handler connection closed")
      context.stop(self)
  }

  private def becomeConnected(conn: ActorRef, socket: SocketChannel): Unit = {
    val writer = context.actorOf(Props(SocketChannelWrapper.writer(socket)))

    val stream: Receive = {
      case Received(data) ⇒
        writer ! Write(data)
    }

    context.become(stream.orElse(onClose))
    SocketChannelWrapper.register(socket, conn)
  }

  private def waitAuthRequest: Receive = {
    case Received(AuthRequest(methods)) ⇒
      val connection = sender()
      if (methods.contains(AuthMethod.NoAuth)) {
        write(connection, AuthMethodResponse(AuthMethod.NoAuth))
        context.become(waitConnection.orElse(onClose))
      } else {
        log.error("No valid authentication methods provided")
        write(connection, AuthMethodResponse.notSupported)
        connection ! Close
      }
  }

  private def localAddressOf(a: SocketChannel): Option[InetSocketAddress] = {
    a.getLocalAddress match {
      case a: InetSocketAddress ⇒ Some(a)
      case _ ⇒ None
    }
  }

  private def cleanHeaders(headers: Seq[HttpHeader]): Seq[HttpHeader] = {
    val droppedHeaders = AppConfig().getStringList("proxyChain.http.dropHeaders").toSet
    val ch = headers.filterNot(h ⇒ droppedHeaders.contains(h.name))
    log.debug("Headers dropped: {}", headers.diff(ch).mkString(", "))
    ch
  }

  private def connectThen(address: InetSocketAddress)(onComplete: Try[SocketChannel] ⇒ Unit): Unit = {
    val connection = sender()
    val proxyChannel = openConnection(address)
    val ctx = context // context is null if calling from Future
    proxyChannel.onComplete {
      case f @ Failure(e) ⇒
        onComplete(f)
        connection ! Close
        ctx.become(onClose) // Wait for close

      case s @ Success(sc) ⇒
        log.debug("Successfully connected: {}", sc)
        onComplete(s)
        becomeConnected(connection, sc)
    }

    val writeQueue: Receive = {
      case Received(data) ⇒ // Enqueue write
        proxyChannel onSuccess {
          case sc ⇒
            sc.write(data)
        }
    }
    context.become(writeQueue.orElse(onClose))
  }

  private def waitConnection: Receive = {
    // SOCKS
    case Received(ConnectionRequest((socksVersion, command, address, userId))) ⇒
      val connection = sender()
      if (command != Command.TcpConnection) {
        // Command not supported
        val code = if (socksVersion == SocksVersion.SocksV5) Codes.Socks5.COMMAND_NOT_SUPPORTED else Codes.failure(socksVersion)
        write(connection, ConnectionStatusResponse(socksVersion, None, code))
      } else if (firewall.connectionIsAllowed(address)) {
        log.info("{} connection request: {}", socksVersion, address)
        connectThen(address) {
          case Failure(e) ⇒
            write(connection, ConnectionStatusResponse(socksVersion, None, Codes.failure(socksVersion)))

          case Success(sc) ⇒
            write(connection, ConnectionStatusResponse(socksVersion, localAddressOf(sc), Codes.success(socksVersion)))
        }
      } else {
        log.warning("{} connection rejected: {}", socksVersion, address)
        val code = if (socksVersion == SocksVersion.SocksV5) Codes.Socks5.CONN_NOT_ALLOWED else Codes.failure(socksVersion)
        write(connection, ConnectionStatusResponse(socksVersion, None, code))
        connection ! Close
      }

    // HTTP
    case Received(HttpRequest((method, url, headers))) ⇒
      val connection = sender()
      val address = HttpConnect.addressOf(url)
      if (firewall.connectionIsAllowed(address)) {
        log.info("HTTP connection request: {}", address)
        connectThen(address) {
          case Failure(e) ⇒
            write(connection, HttpResponse(HttpStatus(400, "Bad Request"), Nil) ++ ByteString("Connection failed"))

          case Success(sc) ⇒
            method match {
              case HttpMethod.CONNECT ⇒ // HTTPS
                write(connection, HttpResponse(HttpStatus(200, "Connection established"), Seq(HttpHeader("Host", address.toString))))

              case _ ⇒ // Plain HTTP
                val relative = URLParser.withDefaultProtocol(url).getFile
                log.debug("HTTP request: {}", relative)
                sc.write(HttpRequest((method, relative, cleanHeaders(headers))))
            }
        }
      } else {
        log.warning("HTTP connection rejected: {}", address)
        write(connection, HttpResponse(HttpStatus(403, "Forbidden"), Nil) ++ ByteString("Connection not allowed"))
        connection ! Close
      }
  }

  override def receive = waitAuthRequest.orElse(waitConnection).orElse(onClose).orElse {
    case Received(bs) ⇒
      log.warning("Invalid request received: {}", bs)
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _: ParserException ⇒ Resume // Invalid request
    case _: IOException ⇒ Stop // Connection error
  }
}
