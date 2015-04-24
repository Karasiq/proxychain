package com.karasiq.proxychain.app.script

import com.karasiq.networkutils.proxy.Proxy
import jdk.nashorn.api.scripting.JSObject
import jdk.nashorn.internal.objects.NativeArray

import scala.collection.GenTraversableOnce
import scala.collection.JavaConversions._
import scala.language.dynamics

/**
 * Javascript conversions
 */
private[script] object Conversions {
  def asSeq(v: AnyRef): Seq[AnyRef] = v match {
    case obj: JSObject ⇒
      obj.values().toIndexedSeq

    case array: NativeArray ⇒
      array.asObjectArray()

    case cs: ChainSelector ⇒
      cs.select()

    case a: Array[_] ⇒
      a.asInstanceOf[Array[AnyRef]].toIndexedSeq

    case c: GenTraversableOnce[_] ⇒
      c.asInstanceOf[GenTraversableOnce[AnyRef]].toIndexedSeq

    case e ⇒ // Single element
      Vector(e)
  }

  def asJsArray(seq: Seq[AnyRef]): NativeArray = {
    val cs = classOf[NativeArray].getDeclaredConstructor(classOf[Array[AnyRef]])
    cs.setAccessible(true)
    cs.newInstance(seq.toArray)
  }

  private def asProxy: PartialFunction[AnyRef, Proxy] = {
    case proxy: Proxy ⇒ proxy
    case str: String ⇒ Proxy(if (str.contains("://")) str else s"http://$str")
  }

  def asProxySeq(v: AnyRef): Seq[Proxy] = {
    asSeq(v).collect(asProxy)
  }
}
