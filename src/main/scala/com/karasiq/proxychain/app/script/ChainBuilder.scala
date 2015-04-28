package com.karasiq.proxychain.app.script

import com.karasiq.proxychain.ProxyChain

/**
 * Proxy chain builder
 */
private[script] object ChainBuilder {
  import Conversions._

  def chains(chains: AnyRef): Seq[ProxyChain] = {
    asSeq(chains).map(ch ⇒ ProxyChain(asProxySeq(ch):_*))
  }

  def chain(chain: AnyRef): Seq[ProxyChain] = {
    Seq(ProxyChain(asProxySeq(chain):_*))
  }

  def chainsFrom(maxChains: Int, entry: AnyRef, middle: AnyRef, exit: AnyRef): Seq[ProxyChain] = {
    Seq.fill(maxChains)(asProxySeq(entry) ++ asProxySeq(middle) ++ asProxySeq(exit))
      .map(ProxyChain.apply).distinct
  }

  def hops(proxies: AnyRef, hops: Int): ChainSelector = {
    ChainSelector(proxies, hops)
  }
}
