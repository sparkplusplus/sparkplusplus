package io.github.sparkplusplus.app

import io.github.sparkplusplus.io.{InputDatasetConfig, OutputDatasetConfig}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.scalatest.funsuite.AnyFunSuite
import org.slf4j.{Logger, LoggerFactory}
import SparkETLAppTest.EtlConfig

class SparkETLAppTest extends AnyFunSuite {

  test("SparkETLApp orchestrates extract transform and load") {
    val lifecycle = new RecordingLifecycle
    val app = new RecordingSparkETLApp(
      config = EtlConfig(
        inputs = Seq(InputDatasetConfig("customers", "/tmp/customers", "parquet")),
        outputs = Seq(OutputDatasetConfig("customer_orders", "/tmp/customer_orders", "delta"))
      ),
      lifecycle = lifecycle
    )

    app.main(Array("--config", "/tmp/app.yaml"))

    assert(app.steps == Seq("extract", "transform", "load"))
  }

  test("SparkETLApp fails when transform misses a configured output") {
    val lifecycle = new RecordingLifecycle
    val app = new MissingOutputSparkETLApp(
      config = EtlConfig(
        outputs = Seq(OutputDatasetConfig("customer_orders", "/tmp/customer_orders", "delta"))
      ),
      lifecycle = lifecycle
    )

    val error = intercept[IllegalArgumentException] {
      app.main(Array("--config", "/tmp/app.yaml"))
    }

    assert(error.getMessage.contains("did not produce configured outputs"))
  }

  test("SparkETLApp fails when transform returns an unexpected output") {
    val lifecycle = new RecordingLifecycle
    val app = new UnexpectedOutputSparkETLApp(
      config = EtlConfig(
        outputs = Seq(OutputDatasetConfig("customer_orders", "/tmp/customer_orders", "delta"))
      ),
      lifecycle = lifecycle
    )

    val error = intercept[IllegalArgumentException] {
      app.main(Array("--config", "/tmp/app.yaml"))
    }

    assert(error.getMessage.contains("not configured"))
  }

  private final class RecordingSparkETLApp(
    config: EtlConfig,
    lifecycle: RecordingLifecycle
  ) extends SparkETLApp[EtlConfig] {

    var steps: Seq[String] = Seq.empty

    override protected def appName: String = "recording-etl-app"

    override protected def configClass: Class[EtlConfig] = classOf[EtlConfig]

    override protected def loadConfig(configPath: String): EtlConfig = config

    override protected def extract(ctx: AppContext[EtlConfig]): Map[String, DataFrame] = {
      steps = steps :+ "extract"
      Map.empty
    }

    override protected def transform(ctx: AppContext[EtlConfig], inputs: Map[String, DataFrame]): Map[String, DataFrame] = {
      steps = steps :+ "transform"
      Map("customer_orders" -> null.asInstanceOf[DataFrame])
    }

    override protected def load(ctx: AppContext[EtlConfig], outputs: Map[String, DataFrame]): Unit = {
      steps = steps :+ "load"
      ()
    }

    override protected def createLogger(): Logger = LoggerFactory.getLogger("SparkETLAppTest")

    override protected[app] def sparkLifecycle: SparkApp.SparkLifecycle[EtlConfig] = lifecycle
  }

  private final class MissingOutputSparkETLApp(
    config: EtlConfig,
    lifecycle: RecordingLifecycle
  ) extends SparkETLApp[EtlConfig] {

    override protected def appName: String = "missing-output-etl-app"

    override protected def configClass: Class[EtlConfig] = classOf[EtlConfig]

    override protected def loadConfig(configPath: String): EtlConfig = config

    override protected def extract(ctx: AppContext[EtlConfig]): Map[String, DataFrame] = Map.empty

    override protected def transform(ctx: AppContext[EtlConfig], inputs: Map[String, DataFrame]): Map[String, DataFrame] = Map.empty

    override protected def createLogger(): Logger = LoggerFactory.getLogger("SparkETLAppTest")

    override protected[app] def sparkLifecycle: SparkApp.SparkLifecycle[EtlConfig] = lifecycle
  }

  private final class UnexpectedOutputSparkETLApp(
    config: EtlConfig,
    lifecycle: RecordingLifecycle
  ) extends SparkETLApp[EtlConfig] {

    override protected def appName: String = "unexpected-output-etl-app"

    override protected def configClass: Class[EtlConfig] = classOf[EtlConfig]

    override protected def loadConfig(configPath: String): EtlConfig = config

    override protected def extract(ctx: AppContext[EtlConfig]): Map[String, DataFrame] = Map.empty

    override protected def transform(ctx: AppContext[EtlConfig], inputs: Map[String, DataFrame]): Map[String, DataFrame] =
      Map("unknown_output" -> null.asInstanceOf[DataFrame])

    override protected def createLogger(): Logger = LoggerFactory.getLogger("SparkETLAppTest")

    override protected[app] def sparkLifecycle: SparkApp.SparkLifecycle[EtlConfig] = lifecycle
  }

  private final class RecordingLifecycle extends SparkApp.SparkLifecycle[EtlConfig] {
    override def create(
      appName: String,
      config: EtlConfig,
      sparkConfig: Map[String, String],
      logger: Logger,
      configureSpark: (SparkSession.Builder, EtlConfig) => SparkSession.Builder
    ): SparkSession = {
      configureSpark(SparkSession.builder(), config)
      null.asInstanceOf[SparkSession]
    }

    override def stop(spark: SparkSession, runFailure: Throwable, logger: Logger): Unit = ()
  }
}

object SparkETLAppTest {
  final case class EtlConfig(
    inputs: Seq[InputDatasetConfig] = Seq.empty,
    outputs: Seq[OutputDatasetConfig] = Seq.empty
  ) extends SparkApp.WithInputDatasets with SparkApp.WithOutputDatasets
}
