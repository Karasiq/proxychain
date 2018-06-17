import java.io.File

import sbt.internal.util.AttributeKey

object InnoSetupKeys {
  val isccExecutable = AttributeKey[String]("innoSetupExecutable", "Inno Setup executable path")
  val issFilePath = AttributeKey[File]("innoSetupScriptPath", "Inno Setup script path")
}
