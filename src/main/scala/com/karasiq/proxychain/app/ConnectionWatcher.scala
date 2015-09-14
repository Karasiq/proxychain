package com.karasiq.proxychain.app

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.io.Tcp
import com.karasiq.proxychain.AppConfig

import scala.concurrent.duration._

private[app] object ConnectionWatcher {
  case object Refresh

  def closeFrozenConnectionsAfter(): Option[FiniteDuration] = {
    val config = AppConfig.externalConfig().getConfig("proxyChain")
    val ms = config.getDuration("frozen-connection-close", TimeUnit.MILLISECONDS)
    if (ms > 0) Some(FiniteDuration(ms, TimeUnit.MILLISECONDS)) else None
  }
}

private[app] final class ConnectionWatcher(connection: ActorRef) extends Actor {
  import context.dispatcher

  context.watch(connection)
  private val closeAfter = ConnectionWatcher.closeFrozenConnectionsAfter()
  private var task = createTask()
  
  def createTask(): Cancellable = {
    closeAfter.map { ca ⇒
      context.system.scheduler.scheduleOnce(ca) {
        connection ! Tcp.Close
      }
    }.orNull
  }

  override def receive: Receive = {
    case Terminated(`connection`) ⇒
      task.cancel()
      context.stop(self)

    case ConnectionWatcher.Refresh if task != null && (task.cancel() || task.isCancelled) ⇒
      task = createTask()
  }
}
