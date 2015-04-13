package com.karasiq.parsers.socks

import java.net.InetSocketAddress

import akka.util.ByteString
import com.karasiq.parsers.socks.SocksClient.SocksVersion.SocksV5
import com.karasiq.parsers.socks.SocksClient.{AuthMethod, SocksVersion}
import com.karasiq.parsers.socks.internal.Address
import com.karasiq.parsers.{BytePacket, ParserException}

import scala.language.implicitConversions

/**
 * Serializers for SOCKS server
 */
object SocksServer {
  implicit def statusToByte(c: ConnectionStatus): Byte = c.code

  /**
   * Server auth method selection
   */
  object AuthMethodResponse extends BytePacket[AuthMethod] {
    override def fromBytes: PartialFunction[Seq[Byte], AuthMethod] = AuthStatusResponse.fromBytes.andThen(AuthMethod.apply)

    override def toBytes: PartialFunction[AuthMethod, Seq[Byte]] = {
      case m: AuthMethod ⇒
        ByteString(SocksV5.code, m.code)
    }

    /**
     * Response for "no supported methods provided"
     */
    def notSupported: ByteString = ByteString(SocksVersion.SocksV5.code, 0xFF)
  }

  /**
   * Server authentication status
   */
  object AuthStatusResponse extends BytePacket[Byte] {
    override def fromBytes: PartialFunction[Seq[Byte], Byte] = {
      case SocksVersion(SocksVersion.SocksV5) :: statusCode :: _ ⇒
        statusCode
    }

    override def toBytes: PartialFunction[Byte, Seq[Byte]] = {
      case b: Byte ⇒ ByteString(b)
    }
  }

  /**
   * Server connection response
   */
  object ConnectionStatusResponse extends BytePacket[(SocksVersion, Option[InetSocketAddress], ConnectionStatus)] {
    override def fromBytes: PartialFunction[Seq[Byte], (SocksVersion, Option[InetSocketAddress], ConnectionStatus)] = {
      // SOCKS5
      case SocksVersion(v @ SocksVersion.SocksV5) :: ConnectionStatus(status: ConnectionSuccess) :: 0x00 :: Address.V5(address, _) ⇒
        (v, Some(address), status)

      case SocksVersion(v @ SocksVersion.SocksV5) :: ConnectionStatus(status) :: 0x00 :: _ ⇒
        (v, None, status)

      // SOCKS4
      case 0x00 :: ConnectionStatus(status) :: _ ⇒
        (SocksVersion.SocksV4, None, status)
    }

    private def emptyAddress: ByteString = ByteString(
      0x01, // Type
      0x00, 0x00, 0x00, 0x00, // IPv4
      0x00, 0x00 // Port
    )

    override def toBytes: PartialFunction[(SocksVersion, Option[InetSocketAddress], ConnectionStatus), Seq[Byte]] = {
      case (version, address, status) ⇒
        version match {
          // SOCKS5
          case SocksVersion.SocksV5 ⇒
            val statusSerialized = ByteString(version.code, status.code, 0x00)
            address.fold(statusSerialized ++ emptyAddress)(statusSerialized ++ Address.V5(_))

          // SOCKS4
          case SocksVersion.SocksV4 ⇒
            ByteString(0x00, status.code, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        }
    }
  }

  trait ConnectionStatus {
    def code: Byte
    def message: String
    override def toString: String = message
  }

  case class ConnectionSuccess(code: Byte, message: String) extends ConnectionStatus
  case class ConnectionError(code: Byte, message: String) extends ConnectionStatus

  /**
   * Server status codes
   */
  object Codes {
    /**
     * Generic success code
     * @param v SOCKS version
     */
    def success(v: SocksVersion): ConnectionStatus = v match {
      case SocksVersion.SocksV4 ⇒
        Socks4.REQUEST_GRANTED

      case SocksVersion.SocksV5 ⇒
        Socks5.REQUEST_GRANTED
    }

    /**
     * Generic failure code
     * @param v SOCKS version
     */
    def failure(v: SocksVersion): ConnectionStatus = v match {
      case SocksVersion.SocksV4 ⇒
        Socks4.REQUEST_FAILED

      case SocksVersion.SocksV5 ⇒
        Socks5.GENERAL_FAILURE
    }

    /**
     * SOCKS4 codes
     */
    object Socks4 {
      val REQUEST_GRANTED: ConnectionStatus = ConnectionSuccess(0x5a, "Request granted")
      val REQUEST_FAILED: ConnectionStatus = ConnectionError(0x5b, "Request failed")
    }

    /**
     * SOCKS5 codes
     */
    object Socks5 {
      val REQUEST_GRANTED: ConnectionStatus = ConnectionSuccess(0x00, "Request granted")
      val GENERAL_FAILURE: ConnectionStatus = ConnectionError(0x01, "General failure")
      val CONN_NOT_ALLOWED: ConnectionStatus = ConnectionError(0x02, "Connection not allowed by ruleset")
      val NETWORK_UNREACHABLE: ConnectionStatus = ConnectionError(0x03, "Network unreachable")
      val HOST_UNREACHABLE: ConnectionStatus = ConnectionError(0x04, "Host unreachable")
      val CONN_REFUSED: ConnectionStatus = ConnectionError(0x05, "Connection refused by destination host")
      val TTL_EXPIRED: ConnectionStatus = ConnectionError(0x06, "TTL expired")
      val COMMAND_NOT_SUPPORTED: ConnectionStatus = ConnectionError(0x07, "Command not supported / protocol error")
      val ADDR_TYPE_NOT_SUPPORTED: ConnectionStatus = ConnectionError(0x08, "Address type not supported")
    }
  }

  object ConnectionStatus {
    import Codes._
    
    def apply(b: Byte): ConnectionStatus = unapply(b).getOrElse(throw new ParserException("Invalid status code: " + b))

    def unapply(b: Byte): Option[ConnectionStatus] = {
      val pf: PartialFunction[Byte, ConnectionStatus] = {
        // SOCKS4
        case 0x5a ⇒ Socks4.REQUEST_GRANTED
        case 0x5b ⇒ Socks4.REQUEST_FAILED

        case 0x00 ⇒ Socks5.REQUEST_GRANTED
        case 0x01 ⇒ Socks5.GENERAL_FAILURE
        case 0x02 ⇒ Socks5.CONN_NOT_ALLOWED
        case 0x03 ⇒ Socks5.NETWORK_UNREACHABLE
        case 0x04 ⇒ Socks5.HOST_UNREACHABLE
        case 0x05 ⇒ Socks5.CONN_REFUSED
        case 0x06 ⇒ Socks5.TTL_EXPIRED
        case 0x07 ⇒ Socks5.COMMAND_NOT_SUPPORTED
        case 0x08 ⇒ Socks5.ADDR_TYPE_NOT_SUPPORTED
      }
      
      pf.lift.apply(b)
    }
  }
}
