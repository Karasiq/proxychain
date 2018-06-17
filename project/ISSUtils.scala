import java.io.File

import sbt.io.IO

object ISSUtils {
  def updateAppVersion(file: File, newVersion: String): Unit = {
    val fileContent = IO.read(file)
    val newContent = fileContent.replaceAll(
      """#define MyAppVersion "([\d\.]+)"""",
      s"""#define MyAppVersion "$newVersion""""
    )

    IO.write(file, newContent)
  }
}
