package com.karasiq.parsers.socks.internal

import akka.util.ByteString
import com.karasiq.parsers.BytePacketFragment

/**
 * Null-byte-terminated string extractor
 */
private[socks] object NullTerminatedString extends BytePacketFragment[String] {
  override def toBytes: PartialFunction[String, Seq[Byte]] = {
    case s ⇒ ByteString(s) ++ ByteString(0x00)
  }

  override def fromBytes: PartialFunction[Seq[Byte], (String, Seq[Byte])] = {
    case bytes if bytes.length >= 1 && bytes.contains(0x00) ⇒
      val string = ByteString(bytes.takeWhile(_ != 0x00).toArray).utf8String
      val tail = bytes.dropWhile(_ != 0x00).drop(1)
      string → tail
  }
}
