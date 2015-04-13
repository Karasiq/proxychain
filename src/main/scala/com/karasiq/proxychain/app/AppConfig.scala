package com.karasiq.proxychain.app

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}

object AppConfig {
  private val file = new File(ConfigFactory.load().getString("proxyChain.external"))
  assert(file.exists(), s"Invalid external configuration file: $file")

  def apply(): Config = ConfigFactory.parseFile(file)
    .withFallback(ConfigFactory.load())
}
