package com.karasiq.proxychain

import java.net.{InetAddress, InetSocketAddress, UnknownHostException}

import com.karasiq.networkutils.ip.Subnet
import com.typesafe.config.Config

import scala.collection.JavaConversions._
import scala.util.control

/**
 * Network filter
 */
trait Firewall {
  def connectionIsAllowed(address: InetSocketAddress): Boolean
}

private final class FirewallImpl(dnsAllowed: Boolean, allowedRanges: Seq[Subnet], blockedRanges: Seq[Subnet], allowedHosts: Seq[String], blockedHosts: Seq[String], allowedPorts: Seq[Int], blockedPorts: Seq[Int]) extends Firewall {
  private def check[T](allowed: Seq[T], blocked: Seq[T], checkFunction: T ⇒ Boolean): Boolean = {
    (allowed.isEmpty || allowed.exists(checkFunction)) && blocked.forall(b ⇒ !checkFunction(b))
  }

  @inline
  private def checkIp(address: InetSocketAddress): Boolean = {
    address match {
      case a if a.isUnresolved && dnsAllowed ⇒
        val ip = InetAddress.getByName(address.getHostString)
        control.Exception.catching(classOf[UnknownHostException]).withApply(_ ⇒ true) {
          check[Subnet](allowedRanges, blockedRanges, _.isInRange(ip))
        }

      case ip if !ip.isUnresolved ⇒
        check[Subnet](allowedRanges, blockedRanges, _.isInRange(ip.getAddress))
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

  override def connectionIsAllowed(address: InetSocketAddress): Boolean = {
    checkHost(address) && checkIp(address) && checkPort(address.getPort)
  }
}

object Firewall {
  def apply(cfg: Config): Firewall = new FirewallImpl(cfg.getBoolean("allowDNS"), cfg.getStringList("allowedRanges").map(Subnet.apply), cfg.getStringList("blockedRanges").map(Subnet.apply), cfg.getStringList("allowedHosts"), cfg.getStringList("blockedHosts"), cfg.getIntList("allowedPorts").map(_.toInt), cfg.getIntList("blockedPorts").map(_.toInt))
}