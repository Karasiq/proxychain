package com.karasiq.proxychain.script

import com.karasiq.networkutils.proxy.Proxy

/**
 * Proxy chain builder
 */
private[script] object ChainBuilder {
  import Conversions._

  def chains(chains: AnyRef): Seq[Seq[Proxy]] = chains match {
    case Conversions.ScalaSeq(ch @ _*) ⇒
      ch.collect {
        case Conversions.ProxySeq(proxies @ _*) ⇒
          proxies
      }

    case _ ⇒
      Nil
  }

  def chain(chain: AnyRef): Seq[Seq[Proxy]] = chain match {
    case Conversions.ProxySeq(proxies @ _*) ⇒
      Seq(proxies)

    case _ ⇒
      Nil
  }

  def chainsFrom(maxChains: Int, entry: AnyRef, middle: AnyRef, exit: AnyRef): Seq[Seq[Proxy]] = {
    val chains = Stream.continually(asProxySeq(entry) ++ asProxySeq(middle) ++ asProxySeq(exit))
      .take(maxChains * 10).distinct.take(maxChains)
    chains
  }

  def hops(proxies: AnyRef, hops: Int): ChainSelector = {
    ChainSelector(proxies, hops)
  }
}
