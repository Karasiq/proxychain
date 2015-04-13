import java.net.InetSocketAddress

import akka.util.ByteString
import com.karasiq.networkutils.http.HttpStatus
import com.karasiq.networkutils.http.headers._
import com.karasiq.parsers.http._
import org.scalatest.{FlatSpec, Matchers}

class HttpConnectRfcTest extends FlatSpec with Matchers {
  "HTTP CONNECT parser" should "parse request" in {
    ByteString("CONNECT host.com:443 HTTP/1.1\r\nHost: host.com\r\nTest-header: test\r\n\r\n") match {
      case HttpRequest((method, url, headers)) ⇒
        url shouldBe "host.com:443"
        headers.toList shouldBe List(HttpHeader("Host: host.com"), HttpHeader("Test-header: test"))
    }
  }

  it should "parse response" in {
    ByteString("HTTP/1.1 123 Test message\r\nTest-header: header\r\n\r\n") match {
      case HttpResponse((status, headers)) ⇒
        status shouldBe HttpStatus(123, "Test message")
        headers.toList shouldBe List(HttpHeader("Test-header: header"))
    }
  }

  it should "create request" in {
    val address = InetSocketAddress.createUnresolved("host.com", 443)
    HttpConnect(address, Seq(Host(address), HttpHeader("Test-Header", "test"))) match {
      case bs: ByteString ⇒
        bs.utf8String shouldBe """CONNECT host.com:443 HTTP/1.1
                                 |Host: host.com:443
                                 |Test-Header: test
                                 |
                                 |""".stripMargin
    }
  }

  it should "create response" in {
    HttpResponse((HttpStatus(123, "Test code"), Seq(HttpHeader("Host: host.com"), HttpHeader("Test-Header: test")))) match {
      case bs: ByteString ⇒
        bs.utf8String shouldBe
          """HTTP/1.1 123 Test code
            |Host: host.com
            |Test-Header: test
            |
            |""".stripMargin
    }
  }

  it should "parse Proxy-Authorization header" in {
    HttpHeader("Proxy-Authorization", "Basic dXNlcjpwYXNz") match {
      case `Proxy-Authorization`("user:pass") ⇒
        // Pass
    }
  }

  it should "create valid Proxy-Authorization header" in {
    `Proxy-Authorization`.basic("user:pass") shouldBe HttpHeader("Proxy-Authorization", "Basic dXNlcjpwYXNz")
  }
}
