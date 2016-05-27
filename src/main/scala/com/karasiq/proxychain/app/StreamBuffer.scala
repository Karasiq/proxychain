package com.karasiq.proxychain.app

import akka.actor.{Actor, ActorRef, Props, ReceiveTimeout, Terminated}
import akka.io.Tcp._
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import akka.util.ByteString

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.language.postfixOps

private[app] object StreamBuffer {
  def props(connection: ActorRef): Props = {
    Props(classOf[StreamBuffer], connection)
  }
}

private[app] class StreamBuffer(connection: ActorRef) extends Actor with ActorPublisher[ByteString] {
  var reading = true
  var buffer = Vector.empty[ByteString]

  @scala.throws[Exception](classOf[Exception])
  override def preStart() = {
    super.preStart()
    context.watch(connection)
    context.setReceiveTimeout(30 seconds)
  }

  @scala.throws[Exception](classOf[Exception])
  override def postStop() = {
    connection ! Close
    super.postStop()
  }

  @tailrec final def publishBuffer(): Unit = {
    if (totalDemand > 0) {
      if (totalDemand <= Int.MaxValue) {
        val (use, keep) = buffer.splitAt(totalDemand.toInt)
        buffer = keep
        use.foreach(onNext)
        if (reading && buffer.length >= 50) {
          reading = false
          connection ! SuspendReading
        } else if (!reading) {
          reading = true
          connection ! ResumeReading
        }
      } else {
        val (use, keep) = buffer.splitAt(Int.MaxValue)
        buffer = keep
        use foreach onNext
        publishBuffer()
      }
    }
  }

  def receive = {
    case Received(data) ⇒
      buffer :+= data
      publishBuffer()

    case Request(_) ⇒
      publishBuffer()

    case _: ConnectionClosed | _: CloseCommand | Cancel | Terminated(`connection`) | ReceiveTimeout ⇒
      publishBuffer()
      context.stop(self)
  }
}