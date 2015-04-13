package com.karasiq.parsers.socks.internal

import akka.util.ByteString
import com.karasiq.parsers.BytePacketFragment

private[socks] object LengthString extends BytePacketFragment[String] {
  override def toBytes: PartialFunction[String, Seq[Byte]] = {
    case s ⇒ ByteString(s.length.toByte) ++ ByteString(s)
  }

  override def fromBytes: PartialFunction[Seq[Byte], (String, Seq[Byte])] = {
    case length :: string if string.length >= length ⇒
      ByteString(string.take(length).toArray).utf8String → string.drop(length)
  }
}
