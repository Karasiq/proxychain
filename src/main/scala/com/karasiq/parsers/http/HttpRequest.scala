package com.karasiq.parsers.http

import akka.util.ByteString
import com.karasiq.networkutils.http.headers.HttpHeader
import com.karasiq.parsers.{BytePacket, RegexByteExtractor}

object HttpMethod extends Enumeration {
  val GET, POST, PUT, PATCH, DELETE, CONNECT = Value
}

object HttpRequest extends BytePacket[(HttpMethod.Value, String, Seq[HttpHeader])] {
  private val regex = new RegexByteExtractor("""^([A-Z]+) ((?:https?://|)[^\s]+) HTTP/1\.[01]\r\n""".r)

  override def fromBytes: PartialFunction[Seq[Byte], (HttpMethod.Value, String, Seq[HttpHeader])] = {
    case regex(result, HttpHeaders(headers @ _*)) ⇒
      (HttpMethod.withName(result.group(1)), result.group(2), headers)
  }

  override def toBytes: PartialFunction[(HttpMethod.Value, String, Seq[HttpHeader]), Seq[Byte]] = {
    case (method, address, headers) ⇒
      val connect = s"$method $address HTTP/1.1\r\n"
      ByteString(connect + HttpHeader.formatHeaders(headers) + "\r\n")
  }
}
