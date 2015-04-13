package com.karasiq.parsers.http

import java.net.InetSocketAddress

import com.karasiq.networkutils.http.headers.{Host, HttpHeader}
import com.karasiq.networkutils.url.URLParser


object HttpConnect {
  private def portOption(port: Int) = Some(port).filter(_ != -1)

  def addressOf(u: String): InetSocketAddress = {
    val url = URLParser.withDefaultProtocol(u)
    val (host, port) = url.getHost → portOption(url.getPort).orElse(portOption(url.getDefaultPort)).getOrElse(80)
    InetSocketAddress.createUnresolved(host, port)
  }

  private def withHostHeader(address: InetSocketAddress, headers: Seq[HttpHeader]) = {
    if (headers.exists(_.name == Host.name)) headers else headers ++ Seq(Host(address))
  }

  def apply(address: InetSocketAddress, headers: Seq[HttpHeader]): Seq[Byte] = HttpRequest((HttpMethod.CONNECT, address.toString, withHostHeader(address, headers)))
}
