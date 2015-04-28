package com.karasiq.proxychain.app.script

import com.karasiq.proxychain.ProxyChain

/**
 * Proxy chain builder
 */
private[script] object ChainBuilder {
  import Conversions._

  def chains(chains: AnyRef): Seq[ProxyChain] = chains match {
    case Conversions.ScalaSeq(ch @ _*) ⇒
      ch.collect {
        case Conversions.ProxySeq(proxies @ _*) ⇒
          ProxyChain(proxies:_*)
      }

    case _ ⇒
      Nil
  }

  def chain(chain: AnyRef): Seq[ProxyChain] = chain match {
    case Conversions.ProxySeq(proxies @ _*) ⇒
      Seq(ProxyChain(proxies:_*))

    case _ ⇒
      Nil
  }

  def chainsFrom(maxChains: Int, entry: AnyRef, middle: AnyRef, exit: AnyRef): Seq[ProxyChain] = {
    Seq.fill(maxChains)(asProxySeq(entry) ++ asProxySeq(middle) ++ asProxySeq(exit))
      .map(ProxyChain.apply).distinct
  }

  def hops(proxies: AnyRef, hops: Int): ChainSelector = {
    ChainSelector(proxies, hops)
  }
}
