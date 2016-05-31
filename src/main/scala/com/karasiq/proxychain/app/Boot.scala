package com.karasiq.proxychain.app

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.scaladsl.Tcp.IncomingConnection
import akka.stream.scaladsl.{Concat, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape, scaladsl}
import akka.util.ByteString
import com.karasiq.fileutils.PathUtils._
import com.karasiq.proxy.server.{ProxyConnectionRequest, ProxyServerStage}
import com.karasiq.proxychain.AppConfig
import com.karasiq.proxychain.script.ScriptEngine
import com.typesafe.config.Config

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object Boot extends App {
  val configFile: Config = AppConfig.externalConfig()
  implicit val actorSystem: ActorSystem = ActorSystem("ProxyChain", configFile.resolve())
  implicit val actorMaterializer = ActorMaterializer()
  import actorSystem.dispatcher

  val cfg = configFile.getConfig("proxyChain")
  val host = cfg.getString("host")

  val config: AppConfig = asPath(cfg.getString("script")) match {
    case script if script.isRegularFile ⇒
      actorSystem.log.debug("Loading script: {}", script)
      val scriptEngine = new ScriptEngine(Logging.getLogger(actorSystem, "ScriptEngine"))
      scriptEngine.asConfig(script)

    case _ ⇒
      AppConfig(cfg) // Default
  }

  // Start server
  val connector = Connector(config)
  def proxyChainConnect(tcpConn: IncomingConnection, request: ProxyConnectionRequest, connection: Flow[ByteString, ByteString, _]): Unit = {
    connector.connect(request, tcpConn.remoteAddress)
      .onComplete {
        case Success((outConn, proxy)) ⇒
          val graph = RunnableGraph.fromGraph(GraphDSL.create(connection, proxy)(Keep.none) { implicit builder ⇒ (connection, proxy) ⇒
            import GraphDSL.Implicits._
            val success = builder.add(Source.single(ProxyServerStage.successResponse(request)))
            val toConnection = builder.add(Concat[ByteString]())
            connection ~> proxy
            success ~> toConnection
            proxy ~> toConnection
            toConnection ~> connection
            ClosedShape
          })
          graph.run()

        case Failure(exc) ⇒
          val source = Source
            .single(ProxyServerStage.failureResponse(request))
            .concat(Source.failed(exc))
          source.via(connection).runWith(Sink.cancelled)
      }
  }

  val port = cfg.getInt("port")
  if (port != 0) {
    scaladsl.Tcp().bind(host, port)
      .runForeach(tcpConn ⇒ tcpConn.handleWith(Flow.fromGraph(new ProxyServerStage)).foreach {
        case (request, connection) ⇒
          proxyChainConnect(tcpConn, request, connection)
      })
  }

  val tlsPort = cfg.getInt("tls.port")
  if (tlsPort != 0) {
    scaladsl.Tcp().bind(host, tlsPort)
      .runForeach(tcpConn ⇒ tcpConn.handleWith(Flow.fromGraph(ProxyServerStage.withTls(AppConfig.tlsContext(server = true)))).foreach {
        case (request, connection) ⇒
          proxyChainConnect(tcpConn, request, connection)
      })
  }

  Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
    override def run(): Unit = {
      actorSystem.log.debug("Shutting down proxychain daemon")
      Await.result(actorSystem.terminate(), 5 minutes)
    }
  }))
}
