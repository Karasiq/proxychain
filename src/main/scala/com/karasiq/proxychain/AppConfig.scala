package com.karasiq.proxychain

import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import java.net.InetSocketAddress
import java.security.{KeyStore, SecureRandom}

import akka.http.scaladsl.{ConnectionContext, HttpsConnectionContext}
import akka.stream.TLSClientAuth
import com.typesafe.config.{Config, ConfigFactory}

import com.karasiq.fileutils.PathUtils._
import com.karasiq.networkutils.proxy.Proxy
import com.karasiq.proxy.ProxyChain
import com.karasiq.tls.TLSKeyStore
import com.karasiq.tls.x509.TrustStore

object AppConfig {
  def apply(cfg: Config): AppConfig = new AppConfig {
    override val firewall: Firewall = Firewall(cfg)

    override def proxyChainsFor(address: InetSocketAddress): Seq[Seq[Proxy]] = {
      val maxChains: Int = cfg.getInt("maxTriedChains")
      Seq.fill(maxChains)(ProxyChain.fromConfig(cfg)).distinct
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

  case class TLSConfig(keyStore: TLSKeyStore, trustStore: KeyStore, clientAuth: Boolean)

  def tlsConfig(): TLSConfig = {
    val config = AppConfig.externalConfig().getConfig("proxyChain.tls")
    val keyStore = new TLSKeyStore(TLSKeyStore.keyStore(config.getString("key-store"), config.getString("key-store-pass")), config.getString("key-store-pass"))
    val trustStore = TrustStore.fromFile(config.getString("trust-store"))
    val clientAuth = config.getBoolean("client-auth")
    TLSConfig(keyStore, trustStore, clientAuth)
  }

  def tlsContext(server: Boolean = false): HttpsConnectionContext = {
    // TODO: Auto configuration (http://doc.akka.io/docs/akka/current/scala/http/server-side-https-support.html)
    val tlsConfig = this.tlsConfig()
    val sslContext = SSLContext.getInstance("TLS")
    val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(tlsConfig.keyStore.keyStore, tlsConfig.keyStore.password.toCharArray)

    val trustManagerFactory: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    trustManagerFactory.init(tlsConfig.trustStore)
    sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, SecureRandom.getInstanceStrong)

    ConnectionContext.https(sslContext, clientAuth = if (server && tlsConfig.clientAuth) Some(TLSClientAuth.need) else None)
  }
}

trait AppConfig {
  def firewall: Firewall
  def proxyChainsFor(address: InetSocketAddress): Seq[Seq[Proxy]]
}