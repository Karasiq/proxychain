package com.karasiq.proxychain

import java.net.{InetAddress, InetSocketAddress, UnknownHostException}

import com.karasiq.networkutils.ip.Subnet
import com.typesafe.config.Config

import scala.collection.JavaConversions._

/**
 * Network filter
 */
trait Firewall {
  def connectionIsAllowed(clientAddress: InetSocketAddress, address: InetSocketAddress): Boolean
}

private final class FirewallImpl(dnsAllowed: Boolean, allowedRanges: Seq[Subnet], blockedRanges: Seq[Subnet], allowedHosts: Seq[String], blockedHosts: Seq[String], allowedPorts: Seq[Int], blockedPorts: Seq[Int], allowedClients: Seq[String], blockedClients: Seq[String]) extends Firewall {
  private def check[T](allowed: Seq[T], blocked: Seq[T], checkFunction: T ⇒ Boolean): Boolean = {
    (allowed.isEmpty || allowed.exists(checkFunction)) && blocked.forall(b ⇒ !checkFunction(b))
  }

  @inline
  private def checkIp(address: InetSocketAddress): Boolean = {
    address match {
      case a if a.isUnresolved && dnsAllowed ⇒
        try {
          val ip = InetAddress.getByName(address.getHostString)
          check[Subnet](allowedRanges, blockedRanges, _.isInRange(ip))
        } catch {
          case _: UnknownHostException ⇒
            true
        }

      case ip if !ip.isUnresolved ⇒
        check[Subnet](allowedRanges, blockedRanges, _.isInRange(ip.getAddress))

      case _ ⇒
        true
    }
  }

  @inline
  private def checkHost(address: InetSocketAddress): Boolean = {
    val host = if (dnsAllowed) address.getHostName else address.getHostString
    check[String](allowedHosts, blockedHosts, _ == host)
  }

  @inline
  private def checkPort(port: Int): Boolean = {
    check[Int](allowedPorts, blockedPorts, _ == port)
  }

  @inline
  private def checkClient(client: InetSocketAddress): Boolean = {
    val host = if (dnsAllowed) client.getHostName else client.getHostString
    check[String](allowedClients, blockedClients, _ == host)
  }

  override def connectionIsAllowed(clientAddress: InetSocketAddress, address: InetSocketAddress): Boolean = {
    checkClient(clientAddress) && checkPort(address.getPort) && checkIp(address) && checkHost(address)
  }
}

object Firewall {
  def apply(cfg: Config): Firewall = new FirewallImpl(cfg.getBoolean("allowDNS"), cfg.getStringList("allowedRanges").map(Subnet.apply), cfg.getStringList("blockedRanges").map(Subnet.apply), cfg.getStringList("allowedHosts"), cfg.getStringList("blockedHosts"), cfg.getIntList("allowedPorts").map(_.toInt), cfg.getIntList("blockedPorts").map(_.toInt), cfg.getStringList("allowedClients"), cfg.getStringList("blockedClients"))
}