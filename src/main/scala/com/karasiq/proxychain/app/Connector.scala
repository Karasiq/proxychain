package com.karasiq.proxychain.app

import java.net.InetSocketAddress

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.scaladsl.Tcp.OutgoingConnection
import akka.util.ByteString

import com.karasiq.proxy.{ProxyChain, ProxyException}
import com.karasiq.proxy.server.ProxyConnectionRequest
import com.karasiq.proxychain.AppConfig

private [app] object Connector {
  def apply(cfg: AppConfig)(implicit as: ActorSystem, am: Materializer) = {
    new Connector(cfg)
  }
}

private[app] class Connector(cfg: AppConfig)(implicit actorSystem: ActorSystem, materializer: Materializer) {
  import actorSystem.dispatcher
  private[this] val log = Logging(actorSystem, "Proxy")

  def connect(request: ProxyConnectionRequest, clientAddress: InetSocketAddress): Future[(OutgoingConnection, Flow[ByteString, ByteString, NotUsed])] = {
    if (!cfg.firewall.connectionIsAllowed(clientAddress, request.address)) {
      return Future.failed(new ProxyException("Connection rejected"))
    }

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
