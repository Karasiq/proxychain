package com.karasiq.proxychain.script

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
  object ScalaSeq {
    private def asSeq: PartialFunction[AnyRef, Seq[AnyRef]] = {
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

    def unapplySeq(v: AnyRef): Option[Seq[AnyRef]] = {
      asSeq.lift.apply(v)
    }
  }

  def asSeq(v: AnyRef): Seq[AnyRef] = v match {
    case ScalaSeq(values @ _*) ⇒
      values

    case _ ⇒
      Nil
  }

  object JsArray {
    def empty: NativeArray = {
      val cs = classOf[NativeArray].getDeclaredConstructor()
      cs.setAccessible(true)
      cs.newInstance()
    }

    def apply(seq: Seq[AnyRef]): NativeArray = {
      val cs = classOf[NativeArray].getDeclaredConstructor(classOf[Array[AnyRef]])
      cs.setAccessible(true)
      cs.newInstance(seq.asInstanceOf[GenTraversableOnce[AnyRef]].toArray)
    }

    def unapply(v: AnyRef): Option[NativeArray] = v match {
      case c: GenTraversableOnce[_] ⇒
        Some(apply(c.toSeq.asInstanceOf[Seq[AnyRef]]))
    }
  }

  def asJsArray(seq: Seq[AnyRef]): NativeArray = JsArray(seq)

  object ProxySeq {
    private def asProxy: PartialFunction[AnyRef, Proxy] = {
      case proxy: Proxy ⇒ proxy
      case str: String ⇒ Proxy(if (str.contains("://")) str else s"http://$str")
    }

    def unapplySeq(v: AnyRef): Option[Seq[Proxy]] = {
      ScalaSeq.unapplySeq(v).map(_.collect(asProxy))
    }
  }

  def asProxySeq(v: AnyRef): Seq[Proxy] = v match {
    case ProxySeq(proxies @ _*) ⇒
      proxies

    case _ ⇒
      Nil
  }
}
