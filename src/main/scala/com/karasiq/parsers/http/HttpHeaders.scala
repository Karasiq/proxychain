package com.karasiq.parsers.http

import akka.util.ByteString
import com.karasiq.networkutils.http.headers.HttpHeader

private[http] object HttpHeaders {
  private def asByteString(b: Seq[Byte]) = ByteString(b.toArray)

  private def headersEnd: String = "\r\n\r\n"

  private def asHeader: PartialFunction[String, HttpHeader] = {
    case HttpHeader(header) ⇒ header
  }

  def unapplySeq(b: Seq[Byte]): Option[Seq[HttpHeader]] = {
    asByteString(b).utf8String.split(headersEnd, 2).toList match {
      case h :: _ ⇒
        val headers = h.lines.collect(asHeader).toVector
        Some(headers)

      case _ ⇒
        None
    }
  }
}
