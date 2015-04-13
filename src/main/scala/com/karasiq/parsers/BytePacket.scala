package com.karasiq.parsers

import java.nio.ByteBuffer

import akka.util.ByteString

import scala.language.implicitConversions

/**
 * Generic bytes serializer/deserializer
 * @tparam T Result type
 */
trait BytePacket[T] {
  def fromBytes: PartialFunction[Seq[Byte], T]

  def toBytes: PartialFunction[T, Seq[Byte]]

  final def unapply(bytes: Seq[Byte]): Option[T] = {
    lazy val asList = bytes.toList
    bytes match {
      case bs if fromBytes.isDefinedAt(bs) ⇒
        Some(fromBytes(bs))

      case _ if fromBytes.isDefinedAt(asList) ⇒ // List matchers workaround
        Some(fromBytes(asList))

      case _ ⇒
        None
    }
  }

  final def apply(t: T): Seq[Byte] = toBytes(t)
}

object BytePacket {
  def wrap(f: ⇒ ByteBuffer): ByteString = {
    val buffer = f
    buffer.flip()
    ByteString(buffer)
  }

  def create(size: Int)(f: ByteBuffer ⇒ ByteBuffer): ByteString = {
    wrap(f(ByteBuffer.allocate(size)))
  }

  implicit def bytesToByteString(b: Seq[Byte]): ByteString = ByteString(b.toArray)
}
