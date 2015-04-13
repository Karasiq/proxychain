package com.karasiq.parsers.http

import akka.util.ByteString
import com.karasiq.networkutils.http.HttpStatus
import com.karasiq.networkutils.http.headers.HttpHeader
import com.karasiq.parsers.{BytePacket, RegexByteExtractor}

object HttpResponse extends BytePacket[(HttpStatus, Seq[HttpHeader])] {
  private val regex = new RegexByteExtractor("""^HTTP/1\.[01] (\d+) (.*)\r\n""".r)

  override def fromBytes: PartialFunction[Seq[Byte], (HttpStatus, Seq[HttpHeader])] = {
    case regex(result, rest) ⇒
      HttpStatus(result.group(1).toInt, result.group(2)) → HttpRequest.headersOf(rest)
  }

  override def toBytes: PartialFunction[(HttpStatus, Seq[HttpHeader]), Seq[Byte]] = {
    case (status, headers) ⇒
      val statusString = s"HTTP/1.1 ${status.code} ${status.message}\r\n"
      ByteString(statusString + HttpHeader.formatHeaders(headers) + "\r\n")
  }
}
