package com.karasiq.proxychain.app.script

import javax.script.Invocable

import com.karasiq.networkutils.proxy.Proxy
import jdk.nashorn.api.scripting.JSObject
import jdk.nashorn.internal.objects.NativeArray

import scala.collection.GenTraversableOnce
import scala.collection.JavaConversions._

/**
 * Javascript conversions
 */
private[script] object Conversions {
  def asInvocable(engine: AnyRef): Invocable = engine match {
    case c: Invocable ⇒ c
    case _ ⇒ throw new IllegalArgumentException("Not invocable")
  }

  def asSeq(v: AnyRef): Seq[AnyRef] = v match {
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

  def asJsArray(seq: Seq[AnyRef]): NativeArray = {
    val cs = classOf[NativeArray].getDeclaredConstructor(classOf[Array[AnyRef]])
    cs.setAccessible(true)
    cs.newInstance(seq.toArray)
  }

  private def asProxy: PartialFunction[AnyRef, Proxy] = {
    case p: Proxy ⇒ p
    case s: String ⇒ Proxy(if (s.contains("://")) s else s"http://$s")
  }

  def asProxySeq(v: AnyRef): Seq[Proxy] = {
    asSeq(v).collect(asProxy)
  }
}
