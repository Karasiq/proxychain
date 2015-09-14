name := "proxychain"

organization := "com.karasiq"

version := "1.4"

scalaVersion := "2.11.7"

resolvers += "softprops-maven" at "http://dl.bintray.com/content/softprops/maven"

libraryDependencies ++= Seq(
  "commons-io" % "commons-io" % "2.4",
  "org.apache.httpcomponents" % "httpclient" % "4.3.3",
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.karasiq" %% "cryptoutils" % "1.0",
  "com.karasiq" %% "proxyutils" % "1.0",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "com.karasiq" %% "coffeescript" % "1.0"
)

scalacOptions ++= Seq("-optimize", "-deprecation", "-feature")

mainClass in Compile := Some("com.karasiq.proxychain.app.Boot")

enablePlugins(JavaServerAppPackaging, LinuxPlugin)

maintainer := "Karasiq <yoba123@yandex.ru>"

packageSummary := "Proxychain"

packageDescription := "Proxy-chaining SOCKS/HTTP/TLS proxy server"