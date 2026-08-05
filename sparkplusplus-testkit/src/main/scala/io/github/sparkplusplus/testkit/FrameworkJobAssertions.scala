package io.github.sparkplusplus.testkit

import io.github.sparkplusplus.config.FrameworkConfigLoader

import java.nio.file.{Files, Path}

object FrameworkJobAssertions {

  def assertValidConfig(path: Path): Unit = FrameworkConfigLoader.validate(path)

  def assertGeneratedProject(path: Path): Unit = {
    Seq("pom.xml", "README.md", "conf/application.yaml").foreach { relativePath =>
      require(Files.exists(path.resolve(relativePath)), s"Generated project is missing $relativePath")
    }
  }
}
