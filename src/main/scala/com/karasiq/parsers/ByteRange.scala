package com.karasiq.parsers

trait ByteRange[T] {
  def fromByte: PartialFunction[Byte, T]
  def toByte: PartialFunction[T, Byte]

  final def unapply(b: Byte): Option[T] = {
    fromByte.lift.apply(b)
  }

  final def apply(t: T): Byte = {
    toByte(t)
  }

  final def apply(b: Byte): T = unapply(b).getOrElse(throw new ParserException("Not in range: " + b))
}
