package com.karasiq.proxychain.script

import com.karasiq.networkutils.proxy.Proxy
import com.karasiq.proxychain.AppConfig

/**
 * Chain scope wrapper
 * @param proxies Proxy list
 * @param hops Number of hops
 */
private[script] final class ChainSelector(proxies: Seq[Proxy], hops: Int) {
  private val proxyChainFactory = AppConfig.proxyChainFactory()

  def select(): Seq[Proxy] = {
    proxyChainFactory.chainFrom(proxies, randomize = true, hops)
  }
}

private[script] object ChainSelector {
  def apply(proxies: AnyRef, hops: Int): ChainSelector = new ChainSelector(Conversions.asProxySeq(proxies), hops)
}
