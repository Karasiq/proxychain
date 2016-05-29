package com.karasiq.proxychain.app

import java.net.InetSocketAddress

import akka.Done
import akka.actor.{ActorRef, Status, _}
import akka.io.Tcp._
import akka.stream.TLSProtocol.{SendBytes, SessionBytes, SslTlsInbound}
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, RunnableGraph, Sink, Source, TLS}
import akka.util.ByteString
import com.karasiq.proxychain.AppConfig

import scala.concurrent.duration._
import scala.language.postfixOps

private[app] class TLSHandler(connection: ActorRef, cfg: AppConfig, clientAddress: InetSocketAddress) extends Actor with ActorLogging {
  import akka.io.Tcp.{Command, Event, Received, Write}

  var closed = false
  val handler = context.actorOf(Props(classOf[Handler], self, cfg, clientAddress))
  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(context.system))(context)
  val graph = RunnableGraph.fromGraph(GraphDSL.create(Source.actorRef[ByteString](20, OverflowStrategy.fail), Source.actorRef[ByteString](20, OverflowStrategy.fail))(Keep.both) { implicit builder ⇒ (fromConnection, fromHandler) ⇒
    import GraphDSL.Implicits._

    val tlsInbound = builder.add(Flow[SslTlsInbound].collect { case SessionBytes(_, bytes) ⇒ bytes })
    val tlsOutbound = builder.add(Flow[ByteString].map(SendBytes(_)))
    val toHandler = builder.add(Flow[ByteString].map(Received(_)).to(Sink.actorRef(handler, Done)))
    val toConnection = builder.add(Flow[ByteString].map(Write(_)).to(Sink.actorRef(connection, Close)))
    val tls = builder.add {
      val httpsContext = AppConfig.tlsContext(server = true)
      TLS(httpsContext.sslContext, httpsContext.firstSession, TLSRole.server, TLSClosing.eagerClose /*, Some(client.getHostName → client.getPort) */)
    }

    fromConnection ~> tls.in2
    tls.out2 ~> tlsInbound ~> toHandler
    fromHandler ~> tlsOutbound ~> tls.in1
    tls.out1 ~> toConnection
    ClosedShape
  })
  val (tlsRead, tlsWrite) = graph.run()

  @scala.throws[Exception](classOf[Exception])
  override def preStart() = {
    super.preStart()
    context.watch(connection)
    context.watch(handler)
    context.setReceiveTimeout(30 seconds)
  }

  @scala.throws[Exception](classOf[Exception])
  override def postStop() = {
    super.postStop()
    closeStreams()
  }

  def closeStreams(): Unit = {
    if (!closed) {
      log.debug("TLS streams closed")
      closed = true
      tlsRead ! Status.Success("Closed")
      tlsWrite ! Status.Success("Closed")
    }
  }

  def receive = {
    case _: CloseCommand | _: CommandFailed | Terminated(_) | ReceiveTimeout ⇒
      closeStreams()

    case cc: ConnectionClosed ⇒
      log.debug("TLS Handler stopped")
      handler ! cc
      context.stop(self)

    case Received(data) ⇒
      tlsRead ! data

    case Write(data, ack) ⇒
      tlsWrite ! data
      if (ack != NoAck) sender() ! ack

    case e: Event ⇒
      handler ! e

    case c: Command ⇒
      connection ! c
  }
}
