package io.github.sparkplusplus.config

import org.scalatest.funsuite.AnyFunSuite
import FrameworkConfigLoaderTest.Settings

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

class FrameworkConfigLoaderTest extends AnyFunSuite {

  test("load decodes a versioned framework configuration with typed settings") {
    val config = FrameworkConfigLoader.load(
      writeTempFile(
        """apiVersion: sparkplusplus.io/v1
          |application:
          |  name: customer-orders
          |  version: 1.2.3
          |  settings:
          |    sourceSystem: orders
          |    batchSize: 20
          |sparkConfig:
          |  spark.sql.session.timeZone: UTC
          |inputs:
          |  - name: orders
          |    path: s3://bucket/orders
          |    format: parquet
          |outputs:
          |  - name: orders_curated
          |    path: s3://bucket/curated/orders
          |    format: parquet
          |    mode: overwrite
          |""".stripMargin
      ),
      classOf[Settings]
    )

    assert(config.application == ApplicationConfig("customer-orders", "1.2.3", Settings("orders", 20)))
    assert(config.inputs.map(_.name) == Seq("orders"))
    assert(config.outputs.map(_.name) == Seq("orders_curated"))
  }

  test("load rejects unknown root and typed settings fields") {
    val rootError = intercept[IllegalArgumentException] {
      FrameworkConfigLoader.load(
        writeTempFile(
          """apiVersion: sparkplusplus.io/v1
            |application:
            |  name: orders
            |  settings:
            |    sourceSystem: orders
            |    batchSize: 20
            |owner: data-platform
            |""".stripMargin
        ),
        classOf[Settings]
      )
    }
    assert(rootError.getMessage.contains("owner"))

    val settingsError = intercept[IllegalArgumentException] {
      FrameworkConfigLoader.load(
        writeTempFile(
          """apiVersion: sparkplusplus.io/v1
            |application:
            |  name: orders
            |  settings:
            |    sourceSystem: orders
            |    batchSize: 20
            |    owner: data-platform
            |""".stripMargin
        ),
        classOf[Settings]
      )
    }
    assert(settingsError.getMessage.contains("owner"))
  }

  test("load rejects unsupported versions with migration guidance") {
    val error = intercept[IllegalArgumentException] {
      FrameworkConfigLoader.load(
        writeTempFile(
          """apiVersion: sparkplusplus.io/v0
            |application:
            |  name: orders
            |  settings:
            |    sourceSystem: orders
            |    batchSize: 20
            |""".stripMargin
        ),
        classOf[Settings]
      )
    }

    assert(error.getMessage.contains("Unsupported apiVersion"))
    assert(error.getMessage.contains("migration guide"))
  }

  test("validate performs framework and dataset validation without decoding settings") {
    val path = writeTempFile(
      """apiVersion: sparkplusplus.io/v1
        |application:
        |  name: orders
        |  settings:
        |    arbitrary: value
        |outputs:
        |  - name: orders
        |    path: /tmp/orders
        |    format: parquet
        |""".stripMargin
    )

    FrameworkConfigLoader.validate(path)
  }

  private def writeTempFile(contents: String): Path = {
    val path = Files.createTempFile("sparkplusplus-framework-", ".yaml")
    Files.write(path, contents.getBytes(StandardCharsets.UTF_8))
    path
  }
}

object FrameworkConfigLoaderTest {
  final case class Settings(sourceSystem: String, batchSize: Int)
}
