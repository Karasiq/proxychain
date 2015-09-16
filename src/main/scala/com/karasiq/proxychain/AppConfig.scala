package com.karasiq.proxychain

import java.net.InetSocketAddress

import com.karasiq.fileutils.PathUtils._
import com.karasiq.proxy.{ProxyChain, ProxyChainFactory, ProxyConnectorFactory}
import com.karasiq.tls.{TLS, TLSCertificateVerifier, TLSKeyStore}
import com.typesafe.config.{Config, ConfigFactory}

object AppConfig {
  def apply(cfg: Config): AppConfig = new AppConfig {
    override def firewall(): Firewall = Firewall(cfg)

    override def proxyChainsFor(address: InetSocketAddress): Seq[ProxyChain] = {
      def loadChain(): ProxyChain = AppConfig.proxyChainFactory().config(cfg)
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
  
  case class TLSConfig(keyStore: TLSKeyStore, verifier: TLSCertificateVerifier, keySet: TLS.KeySet, clientAuth: Boolean)
  
  def tlsConfig(): TLSConfig = {
    val config = AppConfig.externalConfig().getConfig("proxyChain.tls")

    val verifier = new TLSCertificateVerifier(TLSCertificateVerifier.trustStore(config.getString("trust-store")))
    val keyStore = new TLSKeyStore(TLSKeyStore.keyStore(config.getString("key-store"), config.getString("key-store-pass")), config.getString("key-store-pass"))
    val clientAuth = config.getBoolean("client-auth")
    val keySet = TLS.KeySet(keyStore, config.getString("key"))
    TLSConfig(keyStore, verifier, keySet, clientAuth)
  }

  def proxyConnectorFactory(): ProxyConnectorFactory = new ProxyConnectorFactory {
    private val tls = tlsConfig()

    override protected def keyStore: TLSKeyStore = {
      tls.keyStore
    }

    override protected def certificateVerifier: TLSCertificateVerifier = {
      tls.verifier
    }
  }

  def proxyChainFactory(): ProxyChainFactory = new ProxyChainFactory {
    override protected val proxyConnectorFactory: ProxyConnectorFactory = AppConfig.proxyConnectorFactory()
  }
}

trait AppConfig {
  def firewall(): Firewall
  def proxyChainsFor(address: InetSocketAddress): Seq[ProxyChain]
}