name := "proxychain"

organization := "com.github.karasiq"

version := "2.0.4"

isSnapshot := version.value.endsWith("SNAPSHOT")

scalaVersion := "2.11.11"

resolvers += "softprops-maven" at "http://dl.bintray.com/content/softprops/maven"

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= {
  val akkaV = "2.5.4"
  val akkaHttpV = "10.0.10"
  Seq(
    "commons-io" % "commons-io" % "2.5",
    "org.apache.httpcomponents" % "httpclient" % "4.5.3",
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-http" % akkaHttpV,
    "org.bouncycastle" % "bcprov-jdk15on" % "1.58",
    "org.bouncycastle" % "bcpkix-jdk15on" % "1.58",
    "com.github.karasiq" %% "cryptoutils" % "1.4.2",
    "com.github.karasiq" %% "proxyutils" % "2.0.9",
    "com.github.karasiq" %% "coffeescript" % "1.0.2"
  )
}

scalacOptions ++= Seq("-optimize", "-Xelide-below", "0")

mainClass in Compile := Some("com.karasiq.proxychain.app.Boot")

licenses := Seq("Apache License, Version 2.0" → url("http://opensource.org/licenses/Apache-2.0"))

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)