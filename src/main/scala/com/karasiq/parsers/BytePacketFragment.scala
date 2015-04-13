package com.karasiq.parsers

/**
 * Dynamic length byte-seq fragment serializer
 * @tparam T Result type
 */
trait BytePacketFragment[T] {
  def toBytes: PartialFunction[T, Seq[Byte]]

  def fromBytes: PartialFunction[Seq[Byte], (T, Seq[Byte])]

  final def apply(t: T): Seq[Byte] = toBytes(t)

  final def unapply(b: Seq[Byte]): Option[(T, Seq[Byte])] = {
    lazy val asList = b.toList
    b match {
      case bytes if fromBytes.isDefinedAt(bytes) ⇒
        Some(fromBytes(bytes))

      case _ if fromBytes.isDefinedAt(asList) ⇒
        Some(fromBytes(asList))

      case _ ⇒
        None
    }
  }
}
