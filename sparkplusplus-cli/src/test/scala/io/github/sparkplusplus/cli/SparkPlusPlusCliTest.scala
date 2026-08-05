package io.github.sparkplusplus.cli

import io.github.sparkplusplus.config.FrameworkConfigLoader
import io.github.sparkplusplus.testkit.FrameworkJobAssertions
import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.Files

class SparkPlusPlusCliTest extends AnyFunSuite {

  test("init creates a compile-ready framework project with valid configuration") {
    val directory = Files.createTempDirectory("sparkplusplus-cli-project-")

    SparkPlusPlusCli.main(Array("init", directory.toString, "--package", "example.orders", "--name", "orders-job"))

    FrameworkJobAssertions.assertGeneratedProject(directory)
    FrameworkConfigLoader.validate(directory.resolve("conf/application.yaml"))
    val application = new String(Files.readAllBytes(directory.resolve("src/main/scala/example/orders/OrdersJobApp.scala")))
    assert(application.contains("FrameworkSparkETLApp"))
    assert(application.contains("final case class OrdersJobSettings"))
  }
}
