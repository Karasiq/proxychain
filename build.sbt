name := "proxychain"

organization := "com.github.karasiq"

version := "2.0.3"

isSnapshot := version.value.endsWith("SNAPSHOT")

scalaVersion := "2.11.8"

resolvers += "softprops-maven" at "http://dl.bintray.com/content/softprops/maven"

// resolvers += Resolver.sonatypeRepo("snapshots")

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
    "com.github.karasiq" %% "proxyutils" % "2.0.6",
    "com.github.karasiq" %% "coffeescript" % "1.0"
  )
}

scalacOptions ++= Seq("-optimize", "-deprecation", "-feature")

mainClass in Compile := Some("com.karasiq.proxychain.app.Boot")

enablePlugins(JavaAppPackaging)

maintainer := "Karasiq <yoba123@yandex.ru>"

packageSummary := "Proxychain"

packageDescription := "Proxy-chaining SOCKS/HTTP/TLS proxy server"

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ ⇒ false }

licenses := Seq("Apache License, Version 2.0" → url("http://opensource.org/licenses/Apache-2.0"))

homepage := Some(url("https://github.com/Karasiq/" + name.value))

pomExtra := <scm>
  <url>git@github.com:Karasiq/{name.value}.git</url>
  <connection>scm:git:git@github.com:Karasiq/{name.value}.git</connection>
</scm>
  <developers>
    <developer>
      <id>karasiq</id>
      <name>Piston Karasiq</name>
      <url>https://github.com/Karasiq</url>
    </developer>
  </developers>