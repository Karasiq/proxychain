name := "proxychain"

organization := "com.github.karasiq"

version := "2.0.4-SNAPSHOT"

isSnapshot := version.value.endsWith("SNAPSHOT")

scalaVersion := "2.11.8"

resolvers += "softprops-maven" at "http://dl.bintray.com/content/softprops/maven"

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= {
  val akkaV = "2.4.6"
  Seq(
    "commons-io" % "commons-io" % "2.4",
    "org.apache.httpcomponents" % "httpclient" % "4.3.3",
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaV,
    "org.scalatest" %% "scalatest" % "2.2.4" % "test",
    "com.github.karasiq" %% "cryptoutils" % "1.4.0",
    "com.github.karasiq" %% "proxyutils" % "2.0.9",
    "com.github.karasiq" %% "coffeescript" % "1.0"
  )
}

scalacOptions ++= Seq("-optimize", "-deprecation", "-feature")

mainClass in Compile := Some("com.karasiq.proxychain.app.Boot")

enablePlugins(JavaAppPackaging)

licenses := Seq("Apache License, Version 2.0" → url("http://opensource.org/licenses/Apache-2.0"))