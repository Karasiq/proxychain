package com.karasiq.proxychain.script

import com.karasiq.networkutils.proxy.Proxy
import com.karasiq.proxy.ProxyChain

/**
 * Chain scope wrapper
 * @param proxies Proxy list
 * @param hops Number of hops
 */
private[script] final class ChainSelector(proxies: Seq[Proxy], hops: Int) {
  def select(): Seq[Proxy] = {
    ProxyChain.createChain(proxies, randomize = true, hops)
  }
}

private[script] object ChainSelector {
  def apply(proxies: AnyRef, hops: Int): ChainSelector = new ChainSelector(Conversions.asProxySeq(proxies), hops)
}
