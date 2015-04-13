package com.karasiq.parsers.socks

import java.net.InetSocketAddress

import akka.util.ByteString
import com.karasiq.parsers.socks.internal._
import com.karasiq.parsers.{BytePacket, ByteRange, ParserException}

/**
 * Serializers for SOCKS client
 */
object SocksClient {
  sealed trait SocksVersion {
    def code: Byte
  }

  object SocksVersion extends ByteRange[SocksVersion] {
    def of(bs: ByteString): SocksVersion = apply(bs.head) // Extract from first byte

    case object SocksV4 extends SocksVersion {
      override def code: Byte = 0x04
    }

    case object SocksV5 extends SocksVersion {
      override def code: Byte = 0x05
    }

    override def fromByte: PartialFunction[Byte, SocksVersion] = {
      case 0x04 ⇒ SocksV4
      case 0x05 ⇒ SocksV5
    }

    override def toByte: PartialFunction[SocksVersion, Byte] = {
      case v ⇒ v.code
    }
  }

  sealed trait AuthMethod {
    def code: Byte
  }

  object AuthMethod extends ByteRange[AuthMethod] {
    case object NoAuth extends AuthMethod {
      override def code: Byte = 0
      override def toString: String = "No authentication"
    }

    case object GSSAPI extends AuthMethod {
      override def code: Byte = 1
      override def toString: String = "GSSAPI"
    }

    case object UsernamePassword extends AuthMethod {
      override def code: Byte = 2
      override def toString: String = "Username/Password"
    }

    case class IANA(override val code: Byte) extends AuthMethod {
      override def toString: String = s"Method assigned by IANA: $code"
    }
    case class PrivateUse(override val code: Byte) extends AuthMethod {
      override def toString: String = s"Method reserved for private use: $code"
    }

    override def fromByte: PartialFunction[Byte, AuthMethod] = {
      case 0x00 ⇒ NoAuth
      case 0x01 ⇒ GSSAPI
      case 0x02 ⇒ UsernamePassword
      case r if (0x03 to 0x7F).contains(r) ⇒ IANA(r)
      case r if (0x80 to 0xFE).contains(r) ⇒ PrivateUse(r)
    }

    override def toByte: PartialFunction[AuthMethod, Byte] = {
      case authMethod ⇒
        authMethod.code
    }
  }

  object AuthRequest extends BytePacket[Seq[AuthMethod]] {
    override def fromBytes: PartialFunction[Seq[Byte], Seq[AuthMethod]] = {
      case SocksVersion(SocksVersion.SocksV5) :: authMethodsCount :: authMethods if authMethods.length == authMethodsCount.toInt ⇒
        authMethods.collect {
          case AuthMethod(method) ⇒
            method
        }
    }

    override def toBytes: PartialFunction[Seq[AuthMethod], Seq[Byte]] = {
      case authMethods ⇒
        ByteString(SocksVersion(SocksVersion.SocksV5), authMethods.length.toByte) ++ authMethods.map(_.code)
    }
  }

  sealed trait Command {
    def code: Byte
  }

  object Command {
    case object TcpConnection extends Command {
      override def code: Byte = 0x01
    }
    case object TcpBind extends Command {
      override def code: Byte = 0x02
    }
    case object UdpAssociate extends Command {
      override def code: Byte = 0x03
    }

    def apply(b: Byte): Command = {
      unapply(b).getOrElse(throw new ParserException("Invalid SOCKS command: " + b))
    }

    def unapply(b: Byte): Option[Command] = {
      val pf: PartialFunction[Byte, Command] = {
        case 0x01 ⇒ TcpConnection
        case 0x02 ⇒ TcpBind
        case 0x03 ⇒ UdpAssociate
      }
      pf.lift.apply(b)
    }
  }

  object ConnectionRequest extends BytePacket[(SocksVersion, Command, InetSocketAddress, String)] {
    override def fromBytes: PartialFunction[Seq[Byte], (SocksVersion, Command, InetSocketAddress, String)] = {
      case SocksVersion(SocksVersion.SocksV4) :: Command(command) :: Address.V4(address, NullTerminatedString(userId, _)) if command != Command.UdpAssociate ⇒
        (SocksVersion.SocksV4, command, address, userId)

      case SocksVersion(SocksVersion.SocksV5) :: Command(command) :: 0x00 :: Address.V5(address, _) ⇒
        (SocksVersion.SocksV5, command, address, "")
    }

    private def socks4aInvalidIP = ByteString(0x00, 0x00, 0x00, 0x01)

    override def toBytes: PartialFunction[(SocksVersion, Command, InetSocketAddress, String), Seq[Byte]] = {
      case (socksVersion, command, address, userId) ⇒
        val start = ByteString(SocksVersion(socksVersion), command.code, 0x00)
        if (socksVersion == SocksVersion.SocksV4 && address.isUnresolved) {
          start ++ Port(address.getPort) ++ socks4aInvalidIP ++ NullTerminatedString(userId) ++ NullTerminatedString(address.getHostString)
        } else {
          start ++ Address(socksVersion, address) ++ (if(socksVersion == SocksVersion.SocksV4) NullTerminatedString(userId) else ByteString.empty)
        }
    }
  }

  object UsernameAuthRequest extends BytePacket[(String, String)] {
    override def fromBytes: PartialFunction[Seq[Byte], (String, String)] = {
      case 0x01 :: LengthString(username, LengthString(password, _)) ⇒
        username → password
    }

    override def toBytes: PartialFunction[(String, String), Seq[Byte]] = {
      case (username, password) ⇒
        ByteString(0x01) ++ LengthString(username) ++ LengthString(password)
    }
  }
}
