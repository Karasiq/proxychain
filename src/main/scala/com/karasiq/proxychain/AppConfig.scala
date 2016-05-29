package com.karasiq.proxychain

import java.net.InetSocketAddress
import java.security.SecureRandom
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import akka.http.scaladsl.{ConnectionContext, HttpsConnectionContext}
import akka.stream.TLSClientAuth
import com.karasiq.fileutils.PathUtils._
import com.karasiq.networkutils.proxy.Proxy
import com.karasiq.proxy.ProxyChain
import com.karasiq.tls.x509.{CertificateVerifier, TrustStore}
import com.karasiq.tls.{TLS, TLSKeyStore}
import com.typesafe.config.{Config, ConfigFactory}

object AppConfig {
  def apply(cfg: Config): AppConfig = new AppConfig {
    override def firewall(): Firewall = Firewall(cfg)

    override def proxyChainsFor(address: InetSocketAddress): Seq[Seq[Proxy]] = {
      def loadChain(): Seq[Proxy] = ProxyChain.chainFromConfig(cfg)
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

  case class TLSConfig(keyStore: TLSKeyStore, verifier: CertificateVerifier, keySet: TLS.KeySet, clientAuth: Boolean)

  def tlsConfig(): TLSConfig = {
    val config = AppConfig.externalConfig().getConfig("proxyChain.tls")

    val verifier = CertificateVerifier.fromTrustStore(TrustStore.fromFile(config.getString("trust-store")))
    val keyStore = new TLSKeyStore(TLSKeyStore.keyStore(config.getString("key-store"), config.getString("key-store-pass")), config.getString("key-store-pass"))
    val clientAuth = config.getBoolean("client-auth")
    val keySet = keyStore.getKeySet(config.getString("key"))
    TLSConfig(keyStore, verifier, keySet, clientAuth)
  }

  def tlsContext(server: Boolean = false): HttpsConnectionContext = {
    // TODO: Auto configuration (http://doc.akka.io/docs/akka/current/scala/http/server-side-https-support.html)
    val tlsConfig = this.tlsConfig()
    val sslContext = SSLContext.getInstance("TLS")
    val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(tlsConfig.keyStore.keyStore, tlsConfig.keyStore.password.toCharArray)

    val trustManagerFactory: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    trustManagerFactory.init(tlsConfig.keyStore.keyStore)
    sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, SecureRandom.getInstanceStrong)

    ConnectionContext.https(sslContext, clientAuth = if (server && tlsConfig.clientAuth) Some(TLSClientAuth.need) else None)
  }
}

trait AppConfig {
  def firewall(): Firewall
  def proxyChainsFor(address: InetSocketAddress): Seq[Seq[Proxy]]
}