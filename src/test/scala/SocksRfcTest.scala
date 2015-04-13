import akka.util.ByteString
import com.karasiq.parsers.socks.SocksClient._
import com.karasiq.parsers.socks.SocksServer._
import org.scalatest.FlatSpec

class SocksRfcTest extends FlatSpec {
  "SOCKS parser" should "parse SOCKS5 auth request" in {
    ByteString(0x05, 0x01, 0x00) match {
      case AuthRequest(AuthMethod.NoAuth :: Nil) ⇒
        // Pass
    }
  }

  it should "parse SOCKS5 connection request" in {
    ByteString(0x05, 0x03, 0x00, 0x01, 127, 0, 0, 1, 0x1F, 0x90) match {
      case ConnectionRequest((SocksVersion.SocksV5, command, address, _)) ⇒
        assert(command == Command.UdpAssociate)
        assert(address.getHostString == "127.0.0.1")
        assert(address.getPort == 8080)
    }
  }

  it should "parse SOCKS4 connection request" in {
    ByteString(0x04, 0x01, 0x1F, 0x90, 127, 0, 0, 1) ++ ByteString("user") ++ ByteString(0x00) match {
      case ConnectionRequest((SocksVersion.SocksV4, command, address, userId)) ⇒
        assert(command == Command.TcpConnection)
        assert(address.getHostString == "127.0.0.1")
        assert(address.getPort == 8080)
        assert(userId == "user")
    }
  }

  it should "parse SOCKS4A connection request" in {
    ByteString(0x04, 0x01, 0x1F, 0x90, 0, 0, 0, 1) ++ ByteString("user") ++ ByteString(0x00) ++ ByteString("host.com") ++ ByteString(0x00) match {
      case ConnectionRequest((SocksVersion.SocksV4, command, address, userId)) ⇒
        assert(command == Command.TcpConnection)
        assert(address.getHostString == "host.com")
        assert(address.getPort == 8080)
        assert(userId == "user")
    }
  }

  it should "parse SOCKS5 auth method response" in {
    ByteString(0x05, 0x00) match {
      case AuthMethodResponse(AuthMethod.NoAuth) ⇒
        // Pass
    }
  }

  it should "parse SOCKS5 connection response" in {
    ByteString(0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, 0, 80) match {
      case ConnectionStatusResponse((SocksVersion.SocksV5, Some(address), ConnectionSuccess(code @ 0x00, message @ "Request granted"))) ⇒
        assert(address.getHostString == "127.0.0.1")
        assert(address.getPort == 80)
    }
  }

  it should "parse SOCKS4 connection response" in {
    ByteString(0x00, Codes.Socks4.REQUEST_FAILED.code, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00) match {
      case ConnectionStatusResponse((SocksVersion.SocksV4, None, Codes.Socks4.REQUEST_FAILED)) ⇒
        // Pass
    }
  }


  it should "parse username/password auth request" in {
    val (username, password) = "admin" → "password"

    ByteString(0x01, username.length.toByte) ++ ByteString(username) ++ ByteString(password.length.toByte) ++ ByteString(password) match {
      case UsernameAuthRequest((`username`, `password`)) ⇒
        // Pass
    }
  }
}
