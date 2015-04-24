package com.karasiq.proxychain.app.script

import javax.script.Invocable

import scala.language.dynamics

private[script] sealed trait Invoker extends scala.Dynamic {
  def applyDynamic[T](name: String)(args: Any*): T
  def as[T](implicit m: Manifest[T]): T
}

/**
 * Script invoker util
 */
private[script] object Invoker {
  private def asInvocable(engine: AnyRef): Invocable = engine match {
    case c: Invocable ⇒ c
    case _ ⇒ throw new IllegalArgumentException("Not invocable")
  }

  // For functions
  def apply(engine: AnyRef): Invoker = new Invoker {
    override def applyDynamic[T](name: String)(args: Any*): T = {
      asInvocable(engine).invokeFunction(name, args.asInstanceOf[Seq[AnyRef]]:_*).asInstanceOf[T]
    }

    override def as[T](implicit m: Manifest[T]): T = {
      asInvocable(engine).getInterface(m.runtimeClass).asInstanceOf[T]
    }
  }

  // For methods
  def apply(engine: AnyRef, scope: AnyRef): Invoker = new Invoker {
    override def applyDynamic[T](name: String)(args: Any*): T = {
      asInvocable(engine).invokeMethod(scope, name, args.asInstanceOf[Seq[AnyRef]]:_*).asInstanceOf[T]
    }

    override def as[T](implicit m: Manifest[T]): T = {
      asInvocable(engine).getInterface(scope, m.runtimeClass).asInstanceOf[T]
    }
  }
}
