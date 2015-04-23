name := "proxychain"

organization := "com.karasiq"

version := "1.2"

scalaVersion := "2.11.6"

resolvers += "softprops-maven" at "http://dl.bintray.com/content/softprops/maven"

libraryDependencies ++= Seq(
  "commons-io" % "commons-io" % "2.4",
  "org.apache.httpcomponents" % "httpclient" % "4.3.3",
  "com.typesafe.akka" %% "akka-actor" % "2.3.9",
  "com.typesafe.akka" %% "akka-kernel" % "2.3.9",
  "com.karasiq" %% "akka-commons" % "1.0",
  "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test",
  "com.karasiq" %% "coffeescript" % "1.0"
)

scalacOptions ++= Seq("-optimize", "-deprecation", "-feature")

mainClass in Compile := Some("com.karasiq.proxychain.app.Boot")

enablePlugins(AkkaAppPackaging)