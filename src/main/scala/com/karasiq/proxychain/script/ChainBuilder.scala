package com.karasiq.proxychain.script

import com.karasiq.proxy.ProxyChain
import com.karasiq.proxychain.AppConfig

/**
 * Proxy chain builder
 */
private[script] object ChainBuilder {
  import Conversions._

  private val proxyChainFactory = AppConfig.proxyChainFactory()

  def chains(chains: AnyRef): Seq[ProxyChain] = chains match {
    case Conversions.ScalaSeq(ch @ _*) ⇒
      ch.collect {
        case Conversions.ProxySeq(proxies @ _*) ⇒
          proxyChainFactory(proxies:_*)
      }

    case _ ⇒
      Nil
  }

  def chain(chain: AnyRef): Seq[ProxyChain] = chain match {
    case Conversions.ProxySeq(proxies @ _*) ⇒
      Seq(proxyChainFactory(proxies:_*))

    case _ ⇒
      Nil
  }

  def chainsFrom(maxChains: Int, entry: AnyRef, middle: AnyRef, exit: AnyRef): Seq[ProxyChain] = {
    val chains = Stream.continually(proxyChainFactory(asProxySeq(entry) ++ asProxySeq(middle) ++ asProxySeq(exit):_*))
      .take(maxChains * 10).distinct.take(maxChains)
    chains
  }

  def hops(proxies: AnyRef, hops: Int): ChainSelector = {
    ChainSelector(proxies, hops)
  }
}
