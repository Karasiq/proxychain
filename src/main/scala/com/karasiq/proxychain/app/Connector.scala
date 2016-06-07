package com.karasiq.proxychain.app

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Tcp.OutgoingConnection
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.karasiq.proxy.server.ProxyConnectionRequest
import com.karasiq.proxy.{ProxyChain, ProxyException}
import com.karasiq.proxychain.AppConfig

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps
import scala.util.Try

private [app] object Connector {
  def apply(cfg: AppConfig)(implicit as: ActorSystem, am: ActorMaterializer) = new Connector(cfg)
}

private[app] class Connector(cfg: AppConfig)(implicit actorSystem: ActorSystem, actorMaterializer: ActorMaterializer) {
  import actorSystem.dispatcher
  private val log = Logging(actorSystem, "Proxy")

  def connect(request: ProxyConnectionRequest, clientAddress: InetSocketAddress): Future[(OutgoingConnection, Flow[ByteString, ByteString, NotUsed])] = {
    val chains = cfg.proxyChainsFor(request.address)
    log.debug("Trying connect to {} through chains: {}", request.address, chains)

    val promise = Promise[(OutgoingConnection, Flow[ByteString, ByteString, NotUsed])]
    val tlsContext = Try(AppConfig.tlsContext()).toOption
    val futures = chains.map { chain ⇒
      val ((proxyInput, (connFuture, proxyFuture)), proxyOutput) = Source.asSubscriber[ByteString]
        .initialTimeout(30 seconds)
        .idleTimeout(5 minutes)
        .viaMat(ProxyChain.connect(request.address, chain, tlsContext))(Keep.both)
        .toMat(Sink.asPublisher[ByteString](fanout = false))(Keep.both)
        .run()

      (for (_ ← proxyFuture; proxyConnection ← connFuture) yield {
        if (promise.trySuccess((proxyConnection, Flow.fromSinkAndSource(Sink.fromSubscriber(proxyInput), Source.fromPublisher(proxyOutput))))) {
          if (chain.isEmpty) log.warning("Proxy chain is not defined, direct connection to {} opened", request.address)
          else log.info("Opened connection through proxy chain {} to {}", chain.mkString("[", " -> ", "]"), request.address)
        }
        Done
      }).recover { case _ ⇒ Done }
    }

    Future.sequence(futures).onComplete { completed ⇒
      promise.tryFailure(new ProxyException(s"Connection to ${request.address} through chains $chains failed"))
    }
    promise.future
  }
}
