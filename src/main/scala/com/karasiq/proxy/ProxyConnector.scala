package com.karasiq.proxy

import java.net.InetSocketAddress
import java.nio.channels.SocketChannel

import akka.util.ByteString
import com.karasiq.networkutils.SocketChannelWrapper._
import com.karasiq.networkutils.http.headers.`Proxy-Authorization`
import com.karasiq.networkutils.proxy.Proxy
import com.karasiq.parsers.http.{HttpConnect, HttpResponse}
import com.karasiq.parsers.socks.SocksClient.SocksVersion
import com.karasiq.parsers.socks.SocksClient.SocksVersion.SocksV5
import com.karasiq.parsers.socks.{SocksClient, SocksServer}

import scala.language.implicitConversions

abstract class ProxyConnector {
  @throws[ProxyException]("if connection failed")
  def connect(socket: SocketChannel, destination: InetSocketAddress): Unit
}

object ProxyConnector {
  def apply(protocol: String): ProxyConnector = protocol match {
    case "socks" | "socks5" ⇒ new SocksProxyConnector(SocksV5)
    case "http" | "https" ⇒ new HttpProxyConnector
    case p ⇒ throw new IllegalArgumentException(s"Proxy protocol not supported: $p")
  }

  def apply(proxy: Proxy): ProxyConnector = proxy.scheme match {
    case "socks" | "socks5" ⇒ new SocksProxyConnector(SocksVersion.SocksV5, proxy)
    case "socks4" ⇒ new SocksProxyConnector(SocksVersion.SocksV4, proxy)
    case "http" | "https" | "" ⇒ new HttpProxyConnector(proxy)
    case p ⇒ throw new IllegalArgumentException(s"Proxy protocol not supported: $p")
  }
}

class HttpProxyConnector(proxy: Proxy = null) extends ProxyConnector {

  @throws[ProxyException]("if connection failed")
  override def connect(socket: SocketChannel, destination: InetSocketAddress): Unit = {
    val auth = if (proxy != null && proxy.userInfo.nonEmpty) Seq(`Proxy-Authorization`.basic(proxy.userInfo.get)) else Nil
    socket.writeRead(HttpConnect(destination, auth)) match {
      case HttpResponse((status, headers)) ⇒
        if (status.code != 200) throw new ProxyException(s"HTTP CONNECT failed: ${status.code} ${status.message}")

      case bs: ByteString ⇒
        throw new ProxyException(s"Bad HTTPS proxy response: ${bs.utf8String}")
    }
  }
}

class SocksProxyConnector(version: SocksVersion, proxy: Proxy = null) extends ProxyConnector {
  import SocksClient._
  import SocksServer._

  private def authInfo: (String, String) = {
    proxy.userInfo.map(_.split(":", 2).toList) match {
      case Some(u :: p :: Nil) ⇒
        u → p

      case _ ⇒
        "" → ""
    }
  }

  protected def socks5Auth(socket: SocketChannel, authMethod: AuthMethod): Unit = authMethod match {
    case AuthMethod.NoAuth ⇒
      // Pass

    case AuthMethod.UsernamePassword if proxy != null && proxy.userInfo.isDefined ⇒
      val (userName, password) = authInfo
      socket.writeRead(UsernameAuthRequest((userName, password))) match {
        case AuthStatusResponse(0x00) ⇒
          // Success

        case _ ⇒
          throw new ProxyException("SOCKS authentication rejected")
      }


    case m ⇒
      throw new ProxyException(s"SOCKS authentication not supported: $m")
  }

  @throws[ProxyException]("if connection failed")
  override def connect(socket: SocketChannel, destination: InetSocketAddress): Unit = {
    version match {
      case SocksVersion.SocksV5 ⇒
        socket.writeRead(AuthRequest(Seq(AuthMethod.NoAuth))) match {
          case AuthMethodResponse(authMethod) ⇒
            socks5Auth(socket, authMethod)
            socket.writeRead(ConnectionRequest((SocksVersion.SocksV5, Command.TcpConnection, destination, ""))) match {
              case ConnectionStatusResponse((SocksVersion.SocksV5, address, status)) ⇒
                if(status != Codes.Socks5.REQUEST_GRANTED) throw new ProxyException(s"SOCKS request rejected: $status")

              case bs ⇒
                throw new ProxyException(s"Bad response from SOCKS5 server: $bs")
            }

          case bs ⇒
            throw new ProxyException(s"Bad response from SOCKS5 server: $bs")
        }

      case SocksVersion.SocksV4 ⇒
        socket.writeRead(ConnectionRequest((SocksVersion.SocksV4, Command.TcpConnection, destination, authInfo._1))) match {
          case ConnectionStatusResponse((SocksVersion.SocksV4, address, status)) ⇒
            if(status != Codes.Socks4.REQUEST_GRANTED) throw new ProxyException(s"SOCKS request rejected: $status")

          case _ ⇒
            throw new ProxyException("Bad response from SOCKS4 server")
        }
    }
  }
}