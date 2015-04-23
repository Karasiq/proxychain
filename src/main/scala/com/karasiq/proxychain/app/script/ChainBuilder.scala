package com.karasiq.proxychain.app.script

import com.karasiq.networkutils.proxy.Proxy
import jdk.nashorn.internal.objects.NativeArray

/**
 * Proxy chain builder
 */
private[script] object ChainBuilder {
  import Conversions._

  def chainsFrom(maxChains: Int, entry: AnyRef, middle: AnyRef, exit: AnyRef): Seq[Seq[Proxy]] = {
    Seq.fill(maxChains)(asProxySeq(entry) ++ asProxySeq(middle) ++ asProxySeq(exit)).distinct
  }

  def hops(proxies: AnyRef, hops: Int): ChainSelector = {
    ChainSelector(proxies, hops)
  }

  def concat(l1: AnyRef, l2: AnyRef): NativeArray = {
    asJsArray(asSeq(l1) ++ asSeq(l2))
  }
}
