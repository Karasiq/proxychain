package com.karasiq.proxychain.app.script

import com.karasiq.proxychain.ProxyChain
import jdk.nashorn.internal.objects.NativeArray

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
    Seq.fill(maxChains)(asProxySeq(entry) ++ asProxySeq(middle) ++ asProxySeq(exit)).distinct
      .map(ProxyChain.apply)
  }

  def hops(proxies: AnyRef, hops: Int): ChainSelector = {
    ChainSelector(proxies, hops)
  }

  def concat(l1: AnyRef, l2: AnyRef): NativeArray = {
    asJsArray(asSeq(l1) ++ asSeq(l2))
  }
}
