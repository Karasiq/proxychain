package com.karasiq.proxychain.app

import java.net.InetSocketAddress

import com.karasiq.fileutils.PathUtils._
import com.karasiq.proxychain.ProxyChain
import com.typesafe.config.{Config, ConfigFactory}

private[app] object AppConfig {
  def apply(cfg: Config): AppConfig = new AppConfig {
    override def firewall(): Firewall = Firewall(cfg)

    override def proxyChainsFor(address: InetSocketAddress): Seq[ProxyChain] = {
      def loadChain(): ProxyChain = ProxyChain.config(cfg)
      val maxChains: Int = cfg.getInt("maxTriedChains")

      Seq.fill(maxChains)(loadChain()).distinct
    }
  }

  def externalConfig(): Config = {
    val file = asPath(ConfigFactory.load().getString("proxyChain.external"))
    if (file.isRegularFile) {
      ConfigFactory.parseFile(file.toFile)
        .withFallback(ConfigFactory.load())
    } else {
      ConfigFactory.load()
    }
  }

  def apply(): AppConfig = apply(externalConfig().getConfig("proxyChain"))
}

trait AppConfig {
  def firewall(): Firewall
  def proxyChainsFor(address: InetSocketAddress): Seq[ProxyChain]
}