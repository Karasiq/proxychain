package com.karasiq.proxychain.app

import java.net.InetSocketAddress

import com.karasiq.fileutils.PathUtils._
import com.karasiq.proxy.ProxyChain
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
    val default = ConfigFactory.load()
    asPath(default.getString("proxyChain.external")) match {
      case file if file.isRegularFile ⇒ // Load from file
        ConfigFactory.parseFile(file.toFile)
          .withFallback(default)
          .resolve()

      case _ ⇒ // Built-in config
        default
    }
  }

  def apply(): AppConfig = apply(externalConfig().getConfig("proxyChain"))
}

trait AppConfig {
  def firewall(): Firewall
  def proxyChainsFor(address: InetSocketAddress): Seq[ProxyChain]
}