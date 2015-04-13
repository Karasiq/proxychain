import java.net.InetSocketAddress
import java.nio.channels.SocketChannel

import com.karasiq.networkutils.SocketChannelWrapper.ReadWriteOps
import com.karasiq.networkutils.http.headers.HttpHeader
import com.karasiq.networkutils.proxy.Proxy
import com.karasiq.parsers.http.{HttpMethod, HttpRequest}
import com.karasiq.proxy.ProxyConnector
import com.karasiq.proxychain.ProxyChain
import org.apache.commons.io.IOUtils
import org.scalatest.FlatSpec

import scala.util.control.Exception

// You need to have running proxies to run this test
class ProxyChainTest extends FlatSpec {
  val testHost: InetSocketAddress = InetSocketAddress.createUnresolved("ipecho.net", 80) // Host for test connection
  val testUrl: String = "/plain" // URL for test connection

  val testProxies = IndexedSeq(
    Proxy("127.0.0.1", 9999, "http"), // HTTP proxy
    Proxy("127.0.0.1", 1080, "socks") // Socks proxy
  )

  private def readFrom(socket: SocketChannel): Unit = {
    val request = HttpRequest((HttpMethod.GET, testUrl, Seq(HttpHeader("Host" → testHost.getHostString))))
    val response = socket.writeRead(request, 512)
    println(response.utf8String)
  }

  private def tryAndClose(sc: SocketChannel) = Exception.allCatch.andFinally(IOUtils.closeQuietly(sc))

  "Connector" should "connect to HTTP proxy" in {
    assert(testProxies.head.scheme == "http")
    val socket = SocketChannel.open(testProxies.head.toInetSocketAddress)
    tryAndClose(socket) {
      ProxyConnector("http").connect(socket, testHost)
      readFrom(socket)
    }
  }

  it should "connect to SOCKS proxy" in {
    assert(testProxies.last.scheme == "socks")
    val socket = SocketChannel.open(testProxies.last.toInetSocketAddress)
    tryAndClose(socket) {
      ProxyConnector("socks").connect(socket, testHost)
      readFrom(socket)
    }
  }

  it should "connect through proxy chain" in {
    val chain = ProxyChain(testProxies:_*)
    val socket = chain.connection(testHost)
    tryAndClose(socket) {
      readFrom(socket)
    }
  }
}
