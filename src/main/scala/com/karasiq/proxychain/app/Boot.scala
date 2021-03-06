package com.karasiq.proxychain.app

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

import akka.actor.ActorSystem
import akka.event.Logging
import akka.io.Tcp.SO
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source, Tcp}
import akka.stream.scaladsl.Tcp.IncomingConnection
import akka.util.ByteString
import com.typesafe.config.Config

import com.karasiq.fileutils.PathUtils._
import com.karasiq.proxy.server.{ProxyConnectionRequest, ProxyServer}
import com.karasiq.proxychain.AppConfig
import com.karasiq.proxychain.script.ScriptEngine

object Boot extends App {
  val rootConfig: Config = AppConfig.externalConfig()
  implicit val actorSystem = ActorSystem("ProxyChain", rootConfig.resolve())
  implicit val materializer = ActorMaterializer()
  import actorSystem.dispatcher

  val cfg = rootConfig.getConfig("proxyChain")
  val host = cfg.getString("host")

  val appConfig: AppConfig = asPath(cfg.getString("script")) match {
    case script if script.isRegularFile ⇒
      actorSystem.log.debug("Loading script: {}", script)
      val scriptEngine = new ScriptEngine(Logging.getLogger(actorSystem, "ScriptEngine"))
      scriptEngine.asConfig(script)

    case _ ⇒
      AppConfig(cfg) // Default
  }

  // Start server
  val connector = Connector(appConfig)
  val log = Logging(actorSystem, "ProxyServer")

  def runViaChain(tcpConn: IncomingConnection, request: ProxyConnectionRequest, connection: Flow[ByteString, ByteString, _]): Unit = {
    log.info("{} connection request: {}", request.scheme.toUpperCase, request.address)
    connector.connect(request, tcpConn.remoteAddress)
      .onComplete {
        case Success((_, proxy)) ⇒
          ProxyServer.withSuccess(connection, request)
            .join(proxy)
            .run()

        case Failure(exc) ⇒
          Source.failed(exc)
            .via(ProxyServer.withFailure(connection, request))
            .runWith(Sink.cancelled)
      }
  }

  val port = cfg.getInt("port")
  val bufferSize = cfg.getInt("buffer-size")
  if (port != 0) {
    Tcp().bind(host, port, options = List(SO.KeepAlive(true), SO.TcpNoDelay(true)), idleTimeout = 5 minutes)
      .runForeach(tcpConn ⇒ tcpConn.handleWith(ProxyServer().buffer(bufferSize, OverflowStrategy.backpressure)).foreach {
        case (request, connection) ⇒
          runViaChain(tcpConn, request, connection)
      })
  }

  val tlsPort = cfg.getInt("tls.port")
  if (tlsPort != 0) {
    Tcp().bind(host, tlsPort, options = List(SO.KeepAlive(true), SO.TcpNoDelay(true)), idleTimeout = 5 minutes)
      .runForeach(tcpConn ⇒ tcpConn.handleWith(ProxyServer.withTls(AppConfig.tlsContext(server = true)).buffer(bufferSize, OverflowStrategy.backpressure)).foreach {
        case (request, connection) ⇒
          runViaChain(tcpConn, request, connection)
      })
  }

  Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
    override def run(): Unit = {
      actorSystem.log.debug("Shutting down proxychain daemon")
      Await.result(actorSystem.terminate(), 5 minutes)
    }
  }))
}
