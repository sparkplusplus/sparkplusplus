package io.github.sparkplusplus.app

import io.github.sparkplusplus.io.DatasetConfig
import org.apache.spark.sql.SparkSession
import org.scalatest.funsuite.AnyFunSuite
import org.slf4j.{Logger, LoggerFactory}
import SparkAppTest.{DatasetConfigCaseClass, DefaultsConfig, NestedConfig, SampleConfig, SparkConfigCaseClass}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

class SparkAppTest extends AnyFunSuite {

  test("parseArguments accepts --config with passthrough args") {
    val parsed = SparkApp.parseArguments(Seq("--config", "/tmp/app.yaml", "--mode", "daily"))

    assert(parsed.configPath == "/tmp/app.yaml")
    assert(parsed.passthroughArgs == Seq("--mode", "daily"))
  }

  test("parseArguments accepts equals syntax") {
    val parsed = SparkApp.parseArguments(Seq("--config=/tmp/app.yaml", "--dry-run"))

    assert(parsed.configPath == "/tmp/app.yaml")
    assert(parsed.passthroughArgs == Seq("--dry-run"))
  }

  test("parseArguments rejects missing config") {
    val error = intercept[IllegalArgumentException] {
      SparkApp.parseArguments(Seq("--mode", "daily"))
    }

    assert(error.getMessage.contains("Missing required --config"))
  }

  test("parseArguments rejects duplicate config") {
    val error = intercept[IllegalArgumentException] {
      SparkApp.parseArguments(Seq("--config", "a.yaml", "--config=b.yaml"))
    }

    assert(error.getMessage.contains("Duplicate --config"))
  }

  test("YamlConfigLoader loads typed config") {
    val path = writeTempFile(
      """name: daily-orders
        |partitions: 8
        |""".stripMargin
    )

    val config = YamlConfigLoader.load(path, classOf[SampleConfig])

    assert(config == SampleConfig("daily-orders", 8))
  }

  test("YamlConfigLoader fails on unknown fields") {
    val path = writeTempFile(
      """name: daily-orders
        |partitions: 8
        |owner: analytics
        |""".stripMargin
    )

    val error = intercept[Exception] {
      YamlConfigLoader.load(path, classOf[SampleConfig])
    }

    assert(error.getMessage.contains("owner"))
  }

  test("YamlConfigLoader supports nested config, collections, and optional fields") {
    val path = writeTempFile(
      """job:
        |  name: daily-orders
        |  partitions: 8
        |owners:
        |  - analytics
        |  - finance
        |""".stripMargin
    )

    val config = YamlConfigLoader.load(path, classOf[NestedConfig])

    assert(config == NestedConfig(SampleConfig("daily-orders", 8), List("analytics", "finance"), None))
  }

  test("YamlConfigLoader uses default values when fields are omitted") {
    val path = writeTempFile(
      """job:
        |  name: daily-orders
        |  partitions: 8
        |owners:
        |  - analytics
        |retryCount: 5
        |""".stripMargin
    )

    val config = YamlConfigLoader.load(path, classOf[DefaultsConfig])

    assert(config == DefaultsConfig(SampleConfig("daily-orders", 8), List("analytics"), retryCount = 5, dryRun = false))
  }

  test("YamlConfigLoader supports sparkConfig maps") {
    val path = writeTempFile(
      """name: daily-orders
        |partitions: 8
        |sparkConfig:
        |  spark.sql.shuffle.partitions: "16"
        |  spark.sql.session.timeZone: UTC
        |""".stripMargin
    )

    val config = YamlConfigLoader.load(path, classOf[SparkConfigCaseClass])

    assert(
      config == SparkConfigCaseClass(
        "daily-orders",
        8,
        Map(
          "spark.sql.shuffle.partitions" -> "16",
          "spark.sql.session.timeZone" -> "UTC"
        )
      )
    )
  }

  test("YamlConfigLoader supports datasets list") {
    val path = writeTempFile(
      """datasets:
        |  - name: customers
        |    type: input
        |    path: s3://lakehouse/raw/customers
        |    format: parquet
        |  - name: customer_orders
        |    type: output
        |    path: s3://lakehouse/silver/customer_orders
        |    format: delta
        |    mode: overwrite
        |    partitionBy:
        |      - order_date
        |""".stripMargin
    )

    val config = YamlConfigLoader.load(path, classOf[DatasetConfigCaseClass])

    assert(
      config == DatasetConfigCaseClass(
        Seq(
          DatasetConfig("customers", "input", "s3://lakehouse/raw/customers", "parquet"),
          DatasetConfig(
            "customer_orders",
            "output",
            "s3://lakehouse/silver/customer_orders",
            "delta",
            mode = Some("overwrite"),
            partitionBy = Seq("order_date")
          )
        )
      )
    )
  }

  test("extractDatasets validates dataset config by convention") {
    val error = intercept[IllegalArgumentException] {
      SparkApp.extractDatasets(
        DatasetConfigCaseClass(
          Seq(
            DatasetConfig(
              "customers",
              "input",
              "s3://lakehouse/raw/customers",
              "parquet",
              mode = Some("overwrite")
            )
          )
        )
      )
    }

    assert(error.getMessage.contains("Input dataset 'customers' must not define mode"))
  }

  test("AppContext exposes datasets by name") {
    val ctx = AppContext(
      null,
      DatasetConfigCaseClass(
        Seq(
          DatasetConfig("customers", "input", "/tmp/customers", "parquet"),
          DatasetConfig("customer_orders", "output", "/tmp/customer_orders", "delta")
        )
      ),
      Seq("--env", "dev"),
      LoggerFactory.getLogger("AppContextTest")
    )

    assert(ctx.dataset("customers").path == "/tmp/customers")

    val error = intercept[IllegalArgumentException] {
      ctx.dataset("missing")
    }

    assert(error.getMessage.contains("Dataset 'missing' is not defined"))
  }

  test("main validates config before creating spark") {
    val lifecycle = new RecordingLifecycle
    val app = new RecordingSparkApp(
      config = SampleConfig("bad", 0),
      lifecycle = lifecycle,
      validationFailure = Some(new IllegalArgumentException("partitions must be positive"))
    )

    val error = intercept[IllegalArgumentException] {
      app.main(Array("--config", "/tmp/ignored.yaml"))
    }

    assert(error.getMessage.contains("partitions must be positive"))
    assert(!lifecycle.createCalled)
    assert(!lifecycle.stopCalled)
    assert(app.beforeSparkArgs.isEmpty)
  }

  test("main passes parsed config and args into run and stops spark on success") {
    val lifecycle = new RecordingLifecycle
    val app = new RecordingSparkApp(config = SampleConfig("orders", 4), lifecycle = lifecycle)

    app.main(Array("--config", "/tmp/app.yaml", "--env", "dev"))

    assert(app.beforeSparkArgs.contains(Seq("--env", "dev")))
    assert(app.ran)
    assert(app.receivedConfig.contains(SampleConfig("orders", 4)))
    assert(app.receivedArgs.contains(Seq("--env", "dev")))
    assert(lifecycle.createCalled)
    assert(lifecycle.stopCalled)
    assert(lifecycle.stopFailure.isEmpty)
    assert(lifecycle.recordedAppName.contains("recording-app"))
    assert(lifecycle.builderWasCustomized)
  }

  test("main stops spark and preserves failure when run throws") {
    val lifecycle = new RecordingLifecycle
    val app = new RecordingSparkApp(
      config = SampleConfig("orders", 4),
      lifecycle = lifecycle,
      runFailure = Some(new RuntimeException("boom"))
    )

    val error = intercept[RuntimeException] {
      app.main(Array("--config", "/tmp/app.yaml"))
    }

    assert(error.getMessage == "boom")
    assert(lifecycle.stopCalled)
    assert(lifecycle.stopFailure.exists(_.getMessage == "boom"))
  }

  test("main applies sparkConfig from yaml-backed config before app customization") {
    val lifecycle = new RecordingLifecycle
    val app = new RecordingSparkApp(
      config = SampleConfig(
        "orders",
        4,
        Map(
          "spark.sql.shuffle.partitions" -> "64",
          "spark.sql.session.timeZone" -> "UTC"
        )
      ),
      lifecycle = lifecycle
    )

    app.main(Array("--config", "/tmp/app.yaml"))

    assert(
      lifecycle.appliedSparkConfig == Map(
        "spark.sql.shuffle.partitions" -> "64",
        "spark.sql.session.timeZone" -> "UTC"
      )
    )
  }

  test("main validates datasets before creating spark") {
    val lifecycle = new DatasetRecordingLifecycle
    val app = new RecordingDatasetSparkApp(
      DatasetConfigCaseClass(
        Seq(
          DatasetConfig("customer_orders", "output", "/tmp/customer_orders", "delta", schemaPath = Some("schema.json"))
        )
      ),
      lifecycle
    )

    val error = intercept[IllegalArgumentException] {
      app.main(Array("--config", "/tmp/app.yaml"))
    }

    assert(error.getMessage.contains("must not define schemaPath"))
    assert(!lifecycle.createCalled)
  }

  private def writeTempFile(contents: String): Path = {
    val path = Files.createTempFile("sparkplusplus-", ".yaml")
    Files.write(path, contents.getBytes(StandardCharsets.UTF_8))
    path
  }

  private final class RecordingSparkApp(
    config: SampleConfig,
    lifecycle: RecordingLifecycle,
    validationFailure: Option[Throwable] = None,
    runFailure: Option[Throwable] = None
  ) extends SparkApp[SampleConfig] {

    var beforeSparkArgs: Option[Seq[String]] = None
    var ran = false
    var receivedConfig: Option[SampleConfig] = None
    var receivedArgs: Option[Seq[String]] = None

    override protected def appName: String = "recording-app"

    override protected def configClass: Class[SampleConfig] = classOf[SampleConfig]

    override protected def loadConfig(configPath: String): SampleConfig = config

    override protected def validateConfig(config: SampleConfig): Unit = {
      validationFailure.foreach(throw _)
    }

    override protected def beforeSparkStart(config: SampleConfig, args: Seq[String], logger: Logger): Unit = {
      beforeSparkArgs = Some(args)
    }

    override protected def configureSpark(builder: SparkSession.Builder, config: SampleConfig): SparkSession.Builder = {
      lifecycle.builderWasCustomized = true
      builder
    }

    override protected def run(ctx: AppContext[SampleConfig]): Unit = {
      ran = true
      receivedConfig = Some(ctx.config)
      receivedArgs = Some(ctx.args)
      runFailure.foreach(throw _)
    }

    override protected def createLogger(): Logger = LoggerFactory.getLogger("SparkAppTest")

    override protected[app] def sparkLifecycle: SparkApp.SparkLifecycle[SampleConfig] = lifecycle
  }

  private final class RecordingLifecycle extends SparkApp.SparkLifecycle[SampleConfig] {
    var createCalled = false
    var stopCalled = false
    var builderWasCustomized = false
    var stopFailure: Option[Throwable] = None
    var recordedAppName: Option[String] = None
    var appliedSparkConfig: Map[String, String] = Map.empty

    override def create(
      appName: String,
      config: SampleConfig,
      sparkConfig: Map[String, String],
      logger: Logger,
      configureSpark: (SparkSession.Builder, SampleConfig) => SparkSession.Builder
    ): SparkSession = {
      createCalled = true
      recordedAppName = Some(appName)
      appliedSparkConfig = sparkConfig
      configureSpark(SparkSession.builder(), config)
      null.asInstanceOf[SparkSession]
    }

    override def stop(spark: SparkSession, runFailure: Throwable, logger: Logger): Unit = {
      stopCalled = true
      stopFailure = Option(runFailure)
    }
  }

  private final class RecordingDatasetSparkApp(
    config: DatasetConfigCaseClass,
    lifecycle: DatasetRecordingLifecycle
  ) extends SparkApp[DatasetConfigCaseClass] {

    override protected def appName: String = "dataset-recording-app"

    override protected def configClass: Class[DatasetConfigCaseClass] = classOf[DatasetConfigCaseClass]

    override protected def loadConfig(configPath: String): DatasetConfigCaseClass = config

    override protected def run(ctx: AppContext[DatasetConfigCaseClass]): Unit = ()

    override protected def createLogger(): Logger = LoggerFactory.getLogger("DatasetSparkAppTest")

    override protected[app] def sparkLifecycle: SparkApp.SparkLifecycle[DatasetConfigCaseClass] = lifecycle
  }

  private final class DatasetRecordingLifecycle extends SparkApp.SparkLifecycle[DatasetConfigCaseClass] {
    var createCalled = false

    override def create(
      appName: String,
      config: DatasetConfigCaseClass,
      sparkConfig: Map[String, String],
      logger: Logger,
      configureSpark: (SparkSession.Builder, DatasetConfigCaseClass) => SparkSession.Builder
    ): SparkSession = {
      createCalled = true
      null.asInstanceOf[SparkSession]
    }

    override def stop(spark: SparkSession, runFailure: Throwable, logger: Logger): Unit = ()
  }
}

object SparkAppTest {
  final case class SampleConfig(
    name: String,
    partitions: Int,
    sparkConfig: Map[String, String] = Map.empty
  ) extends SparkApp.HasSparkConfig

  final case class SparkConfigCaseClass(
    name: String,
    partitions: Int,
    sparkConfig: Map[String, String]
  ) extends SparkApp.HasSparkConfig
  final case class DatasetConfigCaseClass(
    datasets: Seq[DatasetConfig]
  ) extends SparkApp.HasDatasets
  final case class NestedConfig(job: SampleConfig, owners: List[String], dryRun: Option[Boolean])
  final case class DefaultsConfig(
    job: SampleConfig,
    owners: List[String],
    retryCount: Int = 3,
    dryRun: Boolean = false
  )
}
