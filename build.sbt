
lazy val projectSettings = Seq(
  name := "proxychain",
  organization := "com.github.karasiq",
  isSnapshot := version.value.endsWith("SNAPSHOT"),
  scalaVersion := "2.12.6",
  scalacOptions ++= Seq("-target:jvm-1.8"),
  resolvers += "softprops-maven" at "http://dl.bintray.com/content/softprops/maven",
  // resolvers += Resolver.sonatypeRepo("snapshots"),
  libraryDependencies ++= {
    val akkaV = "2.5.13"
    val akkaHttpV = "10.1.3"
    Seq(
      "commons-io" % "commons-io" % "2.5",
      "org.apache.httpcomponents" % "httpclient" % "4.5.3",
      "com.typesafe.akka" %% "akka-actor" % akkaV,
      "com.typesafe.akka" %% "akka-stream" % akkaV,
      "com.typesafe.akka" %% "akka-http" % akkaHttpV,
      "org.bouncycastle" % "bcprov-jdk15on" % "1.58",
      "org.bouncycastle" % "bcpkix-jdk15on" % "1.58",
      "com.github.karasiq" %% "cryptoutils" % "1.4.3",
      "com.github.karasiq" %% "proxyutils" % "2.0.12",
      "com.github.karasiq" %% "coffeescript" % "1.0.2"
    )
  },
  scalacOptions ++= Seq("-optimize", "-Xelide-below", "0"),
  mainClass in Compile := Some("com.karasiq.proxychain.app.Boot"),
  licenses := Seq("Apache License, Version 2.0" → url("http://opensource.org/licenses/Apache-2.0"))
)

lazy val releaseSettings = Seq(
  releaseCrossBuild := false,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseProcess := {
    import ReleaseTransformations._

    Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      // runTest,
      setReleaseVersion,
      // publishArtifacts,
      releaseStepTask(packageBin in Universal),
      releaseStepTask(stage),
      InnoSetupReleaseSteps.compileISPackage,
      // releaseStepCommand("sonatypeRelease"),
      commitReleaseVersion,
      tagRelease,
      pushChanges,
      releaseStepInputTask(githubRelease),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  }
)

lazy val packageUploadSettings = Seq(
  ghreleaseRepoOrg := "Karasiq",
  ghreleaseRepoName := name.value,
  ghreleaseAssets := Seq(
    target.value / "universal" / s"${name.value}-${version.value}.zip",
    target.value / "iss" / s"${name.value}-${version.value}.exe"
  ),
  ghreleaseNotes := { tagName ⇒
    SimpleReader.readLine(s"Input release notes for $tagName: ").getOrElse("")
  }
  // ghreleaseTitle := { tagName ⇒ tagName.toString }
)

lazy val `proxychain` = (project in file("."))
  .settings(projectSettings, releaseSettings, packageUploadSettings)
  .enablePlugins(JavaAppPackaging)