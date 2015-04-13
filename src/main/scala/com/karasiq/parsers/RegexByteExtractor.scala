package com.karasiq.parsers

import akka.util.ByteString

import scala.util.matching.Regex

final class RegexByteExtractor(r: Regex) {
  def unapply(b: Seq[Byte]): Option[(Regex.Match, Seq[Byte])] = {
    val str = ByteString(b.toArray).utf8String
    r.findFirstMatchIn(str) match {
      case Some(m) ⇒
        Some(m → b.drop(m.group(0).length))

      case _ ⇒
        None
    }
  }
}
