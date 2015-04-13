package com.karasiq.parsers.socks.internal

import akka.util.ByteString
import com.karasiq.parsers.{BytePacket, BytePacketFragment}

private[socks] object Port extends BytePacketFragment[Int] {
  override def toBytes: PartialFunction[Int, Seq[Byte]] = {
    case p ⇒ BytePacket.create(2)(_.putShort(p.toShort))
  }

  override def fromBytes: PartialFunction[Seq[Byte], (Int, Seq[Byte])] = {
    case bytes if bytes.length >= 2 ⇒
      readPort(bytes) → bytes.drop(2)
  }

  @inline
  private def readPort(b: Seq[Byte]): Int = {
    val array: Array[Byte] = (ByteString(0x00, 0x00) ++ b.take(2)).toArray
    BigInt(array).intValue()
  }
}
