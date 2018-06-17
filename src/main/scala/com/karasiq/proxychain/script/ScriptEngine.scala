package com.karasiq.proxychain.script

import javax.script._
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.nio.charset.Charset
import java.nio.file.{Files, Path}

import scala.language.dynamics
import scala.util.control.Exception

import akka.event.LoggingAdapter

import com.karasiq.fileutils.PathUtils._
import com.karasiq.networkutils.proxy.Proxy
import com.karasiq.proxychain.{AppConfig, Firewall}

class ScriptEngine(log: LoggingAdapter) {
  // Script executor
  private val scriptEngine = {
    val scriptEngineManager = new ScriptEngineManager()
    scriptEngineManager.getEngineByName("coffeescript")
  }

  protected def createBindings(): Bindings = {
    val bindings = scriptEngine.createBindings()
    bindings.put("Conversions", Conversions) // JS conversions util
    bindings.put("ChainBuilder", ChainBuilder) // Chain build util
    bindings.put("DefaultFirewall", AppConfig().firewall) // Config firewall
    bindings.put("ProxySource", ProxySource) // URL/file loader
    bindings.put("Logger", log)
    bindings
  }

  // Bind functions
  scriptEngine.setBindings(createBindings(), ScriptContext.ENGINE_SCOPE)

  /**
   * Executes script file
   * @param path Script file path
   */
  @throws(classOf[ScriptException])
  private def loadFile[T](path: T)(implicit toPath: PathProvider[T]): AnyRef = {
    val file: Path = toPath(path)
    require(Files.isRegularFile(file), "Not a file: " + file)
    
    val reader = new InputStreamReader(file.inputStream(), Charset.forName("UTF-8"))
    Exception.allCatch.andFinally(reader.close()) {
      // Execute script
      scriptEngine.eval(reader)
    }
  }

  def asConfig[T](path: T)(implicit toPath: PathProvider[T]): AppConfig = {
    val scope = loadFile(path) // Script scope
    new AppConfig {
      // Dynamic function invoker
      private val invoker = Invoker(scriptEngine, scope)

      override val firewall: Firewall = new Firewall {
        override def connectionIsAllowed(clientAddress: InetSocketAddress, address: InetSocketAddress): Boolean = {
          invoker.connectionIsAllowed(clientAddress, address)
        }
      }

      override def proxyChainsFor(address: InetSocketAddress): Seq[Seq[Proxy]] = {
        (invoker.proxyChainsFor(address): AnyRef) match {
          case Conversions.ScalaSeq(chains @ _*) ⇒
            chains.collect {
              case Conversions.ScalaSeq(proxies @ _*) ⇒
                proxies.collect { case p: Proxy ⇒ p }
            }
        }
      }
    }
  }
}
