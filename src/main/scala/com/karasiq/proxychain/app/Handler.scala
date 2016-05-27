package com.karasiq.proxychain.app

import java.io.IOException
import java.net.InetSocketAddress

import akka.Done
import akka.actor.SupervisorStrategy.{Resume, Stop}
import akka.actor._
import akka.io.Tcp
import akka.io.Tcp._
import akka.stream.scaladsl.Tcp.OutgoingConnection
import akka.stream.scaladsl.{Concat, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, ClosedShape}
import akka.util.ByteString
import com.karasiq.networkutils.http.HttpStatus
import com.karasiq.networkutils.http.headers.HttpHeader
import com.karasiq.networkutils.url.URLParser
import com.karasiq.parsers.ParserException
import com.karasiq.parsers.http.{HttpConnect, HttpMethod, HttpRequest, HttpResponse}
import com.karasiq.parsers.socks.SocksClient._
import com.karasiq.parsers.socks.SocksServer._
import com.karasiq.proxy.{ProxyChain, ProxyException}
import com.karasiq.proxychain.{AppConfig, Firewall}
import org.apache.commons.io.IOUtils
import org.reactivestreams.{Publisher, Subscriber}

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try, control}

/**
 * Proxy connection handler
 */
class Handler(cfg: AppConfig, clientAddress: InetSocketAddress) extends Actor with ActorLogging {
  import context.{dispatcher, system}

  implicit val actorMaterializer = ActorMaterializer(ActorMaterializerSettings(system))(system)
  val bufferSize = 8192
  var buffer = ByteString.empty
  var authenticated = false
  val firewall: Firewall = cfg.firewall()

  private def write(connection: ActorRef, bytes: ByteString) = connection ! Write(bytes)

  private def openConnection(connection: ActorRef, address: InetSocketAddress): Future[(OutgoingConnection, Subscriber[ByteString], Publisher[ByteString])] = {
    val chains = cfg.proxyChainsFor(address)
    log.debug("Trying connect to {} through chains: {}", address, chains)

    val promise = Promise[(OutgoingConnection, Subscriber[ByteString], Publisher[ByteString])]
    val futures = chains.map { chain ⇒
      val ((proxyInput, (connFuture, proxyFuture)), proxyOutput) = Source.asSubscriber[ByteString]
        .idleTimeout(30 seconds)
        .viaMat(ProxyChain.connect(address, chain:_*))(Keep.both)
        .toMat(Sink.asPublisher[ByteString](fanout = false))(Keep.both)
        .run()

      for (_ ← proxyFuture; proxyConnection ← connFuture) {
        if (promise.trySuccess((proxyConnection, proxyInput, proxyOutput))) {
          if (chain.isEmpty) log.warning("Proxy chain not defined, direct connection to {} opened", address)
          else log.info("Opened connection through proxy chain {} to {}", chain.mkString("[", " -> ", "]"), address)
        }
      }
      proxyFuture
    }

    Future.sequence(futures.map(_.recover { case _ ⇒ Done })).onComplete { completed ⇒
      promise.tryFailure(new ProxyException("Connection failed"))
    }
    promise.future
  }

  private def onClose: Receive = {
    case _: ConnectionClosed | Terminated(_) | ReceiveTimeout ⇒
      log.debug("Handler connection closed")
      context.stop(self)
  }

  private def becomeConnected(connection: ActorRef, proxyInput: Subscriber[ByteString], proxyOutput: Publisher[ByteString]): Unit = {
    val bufferRest = buffer
    buffer = ByteString.empty
    val graph = RunnableGraph.fromGraph(GraphDSL.create(Source.actorPublisher[ByteString](StreamBuffer.props(connection))) { implicit builder ⇒ fromClientTail ⇒
      import GraphDSL.Implicits._

      val asWrite = builder.add(Flow[ByteString].map(Write(_)))

      val fromClientHead = builder.add(Source.single(bufferRest))
      val fromClient = builder.add(Concat[ByteString](2))
      val fromProxy = builder.add(Source.fromPublisher(proxyOutput))
      val toClient = builder.add(Sink.actorRef[Write](connection, Close))
      val toProxy = builder.add(Sink.fromSubscriber(proxyInput))

      fromClientHead ~> fromClient.in(0)
      fromClientTail ~> fromClient.in(1)
      fromClient.out ~> toProxy
      fromProxy.out ~> asWrite ~> toClient
      ClosedShape
    })

    val writer = graph.run()

    def stream: Receive = {
      case e: Tcp.Event ⇒
        writer.forward(e)
    }

    context.setReceiveTimeout(60 seconds)
    context.become(stream.orElse(onClose))
  }

  def writeBuffer(data: ByteString)(f: PartialFunction[ByteString, Unit]): Unit = {
    assert(buffer.length <= bufferSize, "Buffer overflow")
    buffer ++= data
    f(buffer)
  }

  private val droppedHeaders = AppConfig.externalConfig().getStringList("proxyChain.http.dropHeaders").toSet
  private def cleanHeaders(headers: Seq[HttpHeader]): Seq[HttpHeader] = {
    val ch = headers.filterNot(h ⇒ droppedHeaders.contains(h.name))
    log.debug("Headers dropped: {}", headers.diff(ch).mkString(", "))
    ch
  }

  private def connectThen(address: InetSocketAddress)(onComplete: Try[(OutgoingConnection, Subscriber[ByteString], Publisher[ByteString])] ⇒ Unit): Unit = {
    val connection = sender()
    val ctx = context
    connection ! SuspendReading
    context.become(onClose)
    openConnection(connection, address).onComplete {
      case f @ Failure(e) ⇒
        log.error(e, "Connection through proxy chains failed")
        onComplete(f)
        ctx.become(onClose)

      case s @ Success((proxyConnection, input, output)) ⇒
        onComplete(s)
        becomeConnected(connection, input, output)
        connection ! ResumeReading
    }
  }

  private def dummyPage(): ByteString = {
    val resource = getClass.getClassLoader.getResourceAsStream("proxychain-dummy.html")
    control.Exception.allCatch.andFinally(IOUtils.closeQuietly(resource)) {
      ByteString(IOUtils.toString(resource, "UTF-8"), "UTF-8")
    }
  }

  private def waitConnection: Receive = {
    // SOCKS
    case Received(data) ⇒
      writeBuffer(data) {
        case ConnectionRequest((socksVersion, command, address, userId), rest) ⇒
          authenticated = true
          buffer = ByteString(rest:_*)
          val connection = sender()
          context.watch(connection)
          if (command != Command.TcpConnection) {
            // Command not supported
            val code = if (socksVersion == SocksVersion.SocksV5) Codes.Socks5.COMMAND_NOT_SUPPORTED else Codes.failure(socksVersion)
            write(connection, ConnectionStatusResponse(socksVersion, None, code))
            connection ! Close
          } else if (firewall.connectionIsAllowed(clientAddress, address)) {
            log.info("{} connection request: {}", socksVersion, address)
            connectThen(address) {
              case Failure(e) ⇒
                write(connection, ConnectionStatusResponse(socksVersion, None, Codes.failure(socksVersion)))
                connection ! Close

              case Success((proxyConnection, _, _)) ⇒
                write(connection, ConnectionStatusResponse(socksVersion, Some(proxyConnection.localAddress), Codes.success(socksVersion)))
            }
          } else {
            log.warning("{} connection from {} rejected: {}", socksVersion, clientAddress, address)
            val code = if (socksVersion == SocksVersion.SocksV5) Codes.Socks5.CONN_NOT_ALLOWED else Codes.failure(socksVersion)
            write(connection, ConnectionStatusResponse(socksVersion, None, code))
            connection ! Close
          }

        case AuthRequest(methods, rest) if !authenticated ⇒
          buffer = ByteString(rest:_*)
          val connection = sender()
          if (methods.contains(AuthMethod.NoAuth)) {
            authenticated = true
            write(connection, AuthMethodResponse(AuthMethod.NoAuth))
          } else {
            log.error("No valid authentication methods provided")
            write(connection, AuthMethodResponse.notSupported)
            connection ! Close
          }

        case HttpRequest((method, url, headers), rest) ⇒
          buffer = ByteString(rest:_*)
          val connection = sender()
          val address = HttpConnect.addressOf(url)

          if (address.getHostString.isEmpty) { // Plain HTTP request
            write(connection, HttpResponse(HttpStatus(404, "Not Found"), Nil) ++ dummyPage())
            connection ! Close
          } else if (firewall.connectionIsAllowed(clientAddress, address)) {
            log.info("HTTP connection request: {}", address)
            connectThen(address) {
              case Failure(e) ⇒
                write(connection, HttpResponse(HttpStatus(400, "Bad Request"), Nil) ++ ByteString("Connection failed"))
                connection ! Close

              case Success((_, input, output)) ⇒
                method match {
                  case HttpMethod.CONNECT ⇒ // HTTPS
                    write(connection, HttpResponse(HttpStatus(200, "Connection established"), Seq(HttpHeader("Host", address.toString))))

                  case _ ⇒ // Plain HTTP
                    val relative = URLParser.withDefaultProtocol(url).getFile
                    log.debug("HTTP request: {}", relative)
                    buffer ++= HttpRequest((method, relative, cleanHeaders(headers)))
                }
            }
          } else {
            log.warning("HTTP connection from {} rejected: {}", clientAddress, address)
            write(connection, HttpResponse(HttpStatus(403, "Forbidden"), Nil) ++ ByteString("Connection not allowed"))
            connection ! Close
          }
      }
  }

  override def receive = waitConnection.orElse(onClose)

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _: ParserException ⇒ Resume // Invalid request
    case _: IOException ⇒ Stop // Connection error
  }
}
