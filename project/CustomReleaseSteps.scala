import scala.sys.process._

import sbt._
import sbtrelease.ReleasePlugin.autoImport.ReleaseStep

object CustomReleaseSteps {
  val compileISSPackage = ReleaseStep(action = state => {
    val projectState = Project.extract(state)
    val workingDir = projectState.get(Keys.baseDirectory) / "setup"
    val issFile = workingDir / "setup.iss"
    ISSUtils.updateAppVersion(issFile, projectState.get(Keys.version))
    require(Process(Seq("ISCC", issFile.toString), workingDir).! == 0, "Inno Setup error")
    state
  })
}
