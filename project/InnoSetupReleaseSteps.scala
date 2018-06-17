import scala.sys.process._

import sbt._
import sbtrelease.ReleasePlugin.autoImport.ReleaseStep

object InnoSetupReleaseSteps {
  private[this] val findISCCExecutable = ReleaseStep(action = state ⇒ {
    if (state.get(InnoSetupKeys.isccExecutable).isEmpty) {
      val executable = sys.env.getOrElse("ISCC_PATH", "ISCC")
      state.put(InnoSetupKeys.isccExecutable, executable)
    } else {
      state
    }
  })

  private[this] val findISSFile = ReleaseStep(action = state ⇒ {
    if (state.get(InnoSetupKeys.issFilePath).isEmpty) {
      val issFile = sys.env.get("ISS_FILE")
        .map(new File(_))
        .getOrElse {
          val projectState = Project.extract(state)
          projectState.get(Keys.baseDirectory) / "setup" / "setup.iss"
        }

      state.put(InnoSetupKeys.issFilePath, issFile)
    } else {
      state
    }
  })

  private[this] val runISCCCompiler = ReleaseStep(action = state ⇒ {
    val projectState = Project.extract(state)
    val issFile = state.get(InnoSetupKeys.issFilePath).getOrElse(throw new NoSuchElementException("ISS file not specified"))
    val workingDir = issFile.getParentFile
    ISSUtils.updateAppVersion(issFile, projectState.get(Keys.version))
    require(Process(Seq("ISCC", issFile.toString), workingDir).! == 0, "Inno Setup error")
    state
  })

  val compileISPackage: ReleaseStep = findISCCExecutable
    .andThen(findISCCExecutable)
    .andThen(findISSFile)
    .andThen(runISCCCompiler)
}
