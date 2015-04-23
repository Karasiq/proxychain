package com.karasiq.proxychain.app.script

import java.io.{InputStream, InputStreamReader}
import java.net.{InetSocketAddress, URL}
import java.nio.charset.Charset
import java.nio.file.{Files, Path}
import javax.script._

import com.karasiq.fileutils.PathUtils._
import com.karasiq.networkutils.proxy.Proxy
import com.karasiq.proxychain.ProxyChain
import com.karasiq.proxychain.app.{AppConfig, Firewall}
import jdk.nashorn.api.scripting.JSObject
import jdk.nashorn.internal.objects.NativeArray
import org.apache.commons.io.IOUtils

import scala.collection.GenTraversableOnce
import scala.collection.JavaConversions._
import scala.io.Source
import scala.language.dynamics
import scala.util.control.Exception

private class ChainSelector(proxies: Seq[Proxy], hops: Int) {
  def select(): Seq[Proxy] = ProxyChain.chainFrom(proxies, randomize = true, hops)
}

private object ProxySource {
  def fromURL(url: String, encoding: String): NativeArray = {
    // Open connection
    val inputStream: InputStream = {
      val connection = new URL(url).openConnection()
      connection.setConnectTimeout(10000)
      connection.setReadTimeout(10000)
      connection.getInputStream
    }

    Exception.allCatch.andFinally(IOUtils.closeQuietly(inputStream)) {
      val source = Source.fromInputStream(inputStream, encoding)
      Conversions.seqToJsArray(source.getLines().toVector)
    }
  }

  def fromURL(url: String): NativeArray = fromURL(url, "UTF-8")
}

private object Conversions {
  def jsArrayToSeq: PartialFunction[AnyRef, Seq[AnyRef]] = {
    case obj: JSObject ⇒
      obj.values().toIndexedSeq

    case array: NativeArray ⇒
      array.asObjectArray()

    case cs: ChainSelector ⇒
      cs.select()

    case c: GenTraversableOnce[_] ⇒
      c.asInstanceOf[GenTraversableOnce[AnyRef]].toIndexedSeq

    case e ⇒ // Single element
      Vector(e)
  }

  def seqToJsArray(seq: Seq[AnyRef]): NativeArray = {
    val cs = classOf[NativeArray].getDeclaredConstructor(classOf[Array[AnyRef]])
    cs.setAccessible(true)
    cs.newInstance(seq.toArray)
  }

  private def asProxy: PartialFunction[AnyRef, Proxy] = {
    case p: Proxy ⇒ p
    case s: String ⇒ Proxy(if (s.contains("://")) s else s"http://$s")
  }
  
  def proxySeq(v: AnyRef): Seq[Proxy] = {
    jsArrayToSeq(v).collect(asProxy)
  }
}

private object ChainBuilder {
  import Conversions._

  def chainsFrom(maxChains: Int, entry: AnyRef, middle: AnyRef, exit: AnyRef): Seq[Seq[Proxy]] = {
    Seq.fill(maxChains)(proxySeq(entry) ++ proxySeq(middle) ++ proxySeq(exit)).distinct
  }

  def hops(proxies: AnyRef, hops: Int): ChainSelector = new ChainSelector(proxySeq(proxies), hops)

  def concat(l1: AnyRef, l2: AnyRef): NativeArray = {
    seqToJsArray(jsArrayToSeq(l1) ++ jsArrayToSeq(l2))
  }
}

private[app] object ScriptEngine extends scala.Dynamic {
  private val scriptEngineManager = new ScriptEngineManager()
  private val scriptEngine = scriptEngineManager.getEngineByName("coffeescript")
  private def createBindings(): Bindings = {
    val bindings = scriptEngine.createBindings()
    bindings.put("ChainBuilder", ChainBuilder) // Chain build util
    bindings.put("DefaultFirewall", AppConfig().firewall()) // Config firewall
    bindings.put("ProxySource", ProxySource) // URL/file loader
    bindings
  }
  scriptEngine.setBindings(createBindings(), ScriptContext.ENGINE_SCOPE)

  /**
   * Executes script file
   * @param path Script file path
   */
  @throws(classOf[ScriptException])
  private def loadFile[T](path: T)(implicit toPath: PathProvider[T]): AnyRef = {
    val file: Path = toPath(path)
    assert(Files.isRegularFile(file), "Not a file: " + file)
    val reader = new InputStreamReader(file.inputStream(), Charset.forName("UTF-8"))

    Exception.allCatch.andFinally(IOUtils.closeQuietly(reader)) {
      scriptEngine.eval(reader)
    }
  }

  def asConfig[T](path: T)(implicit toPath: PathProvider[T]): AppConfig = {
    val file = loadFile(path)
    
    new AppConfig {
      override def firewall(): Firewall = new Firewall {
        override def connectionIsAllowed(address: InetSocketAddress): Boolean = {
          scriptEngine match {
            case i: Invocable ⇒
              i.invokeMethod(file, "connectionIsAllowed", address).asInstanceOf[Boolean]
          }
        }
      }

      override def proxyChainsFor(address: InetSocketAddress): Seq[ProxyChain] = {
        scriptEngine match {
          case i: Invocable ⇒
            val result = i.invokeMethod(file, "proxyChainsFor", address)
            val chains = Conversions.jsArrayToSeq(result)
            chains.map { chain ⇒ ProxyChain(Conversions.proxySeq(chain):_*) }
        }
      }
    }
  }
}
