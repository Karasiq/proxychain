package com.karasiq.proxychain.app.script

import java.io.InputStream
import java.net.URL

import com.karasiq.fileutils.PathUtils._
import jdk.nashorn.internal.objects.NativeArray
import org.apache.commons.io.IOUtils

import scala.io.Source
import scala.util.control.Exception

/**
 * File/URL loader
 */
private[script] object ProxySource {
  private def defaultEncoding: String = "UTF-8"

  private def asStringArray(inputStream: InputStream, encoding: String): NativeArray = {
    Exception.allCatch.andFinally(IOUtils.closeQuietly(inputStream)) {
      val source = Source.fromInputStream(inputStream, encoding)
      Conversions.asJsArray(source.getLines().toVector)
    }
  }

  def fromURL(url: String, encoding: String): NativeArray = {
    val inputStream: InputStream = {
      val connection = new URL(url).openConnection()
      connection.setConnectTimeout(10000)
      connection.setReadTimeout(10000)
      connection.getInputStream
    }
    asStringArray(inputStream, encoding)
  }

  def fromURL(url: String): NativeArray = fromURL(url, defaultEncoding)

  def fromFile(file: String, encoding: String): NativeArray = {
    asStringArray(asPath(file).inputStream(), encoding)
  }

  def fromFile(file: String): NativeArray = fromFile(file, defaultEncoding)
}
