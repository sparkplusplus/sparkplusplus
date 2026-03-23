package io.github.sparkplusplus.app

import org.apache.spark.sql.SparkSession
import org.slf4j.{Logger, LoggerFactory}

abstract class SparkApp[C <: AnyRef] {

  protected def appName: String

  protected def configClass: Class[C]

  protected def configureSpark(builder: SparkSession.Builder, config: C): SparkSession.Builder = builder

  protected def validateConfig(config: C): Unit = ()

  protected def run(ctx: AppContext[C]): Unit

  final def main(args: Array[String]): Unit = {
    val parsedArgs = parseArguments(args.toIndexedSeq)
    val config = loadConfig(parsedArgs.configPath)
    validateConfig(config)

    val logger = createLogger()
    beforeSparkStart(config, parsedArgs.passthroughArgs, logger)
    val spark = sparkLifecycle.create(appName, config, logger, configureSpark)
    val context = AppContext(spark, config, parsedArgs.passthroughArgs, logger)

    var runFailure: Throwable = null

    try {
      logger.info("Starting Spark application {}", appName)
      run(context)
      logger.info("Completed Spark application {}", appName)
    } catch {
      case throwable: Throwable =>
        runFailure = throwable
        logger.error(s"Spark application $appName failed", throwable)
        throw throwable
    } finally {
      sparkLifecycle.stop(spark, runFailure, logger)
    }
  }

  protected def parseArguments(args: Seq[String]): AppArguments = SparkApp.parseArguments(args)

  protected def loadConfig(configPath: String): C = YamlConfigLoader.load(configPath, configClass)

  protected def beforeSparkStart(config: C, args: Seq[String], logger: Logger): Unit = ()

  protected def createLogger(): Logger = LoggerFactory.getLogger(getClass)

  protected[app] def sparkLifecycle: SparkApp.SparkLifecycle[C] = new SparkApp.DefaultSparkLifecycle[C]
}

object SparkApp {

  private[app] trait SparkLifecycle[C <: AnyRef] {
    def create(
      appName: String,
      config: C,
      logger: Logger,
      configureSpark: (SparkSession.Builder, C) => SparkSession.Builder
    ): SparkSession

    def stop(spark: SparkSession, runFailure: Throwable, logger: Logger): Unit
  }

  private[app] final class DefaultSparkLifecycle[C <: AnyRef] extends SparkLifecycle[C] {
    override def create(
      appName: String,
      config: C,
      logger: Logger,
      configureSpark: (SparkSession.Builder, C) => SparkSession.Builder
    ): SparkSession = {
      val builder = SparkSession.builder().appName(appName)
      configureSpark(builder, config).getOrCreate()
    }

    override def stop(spark: SparkSession, runFailure: Throwable, logger: Logger): Unit = {
      if (spark != null) {
        try {
          spark.stop()
        } catch {
          case stopFailure: Throwable =>
            if (runFailure != null) {
              runFailure.addSuppressed(stopFailure)
            } else {
              throw stopFailure
            }
        }
      }
    }
  }

  def parseArguments(args: Seq[String]): AppArguments = {
    val passthroughArgs = Vector.newBuilder[String]
    var configPath: Option[String] = None
    var index = 0

    while (index < args.length) {
      val current = args(index)

      if (current == "--config") {
        if (index + 1 >= args.length) {
          throw new IllegalArgumentException("Missing value after --config")
        }

        val value = args(index + 1)
        if (value.startsWith("--")) {
          throw new IllegalArgumentException("Missing value after --config")
        }

        configPath = setConfigPath(configPath, value)
        index += 2
      } else if (current.startsWith("--config=")) {
        val value = current.stripPrefix("--config=")
        if (value.isEmpty) {
          throw new IllegalArgumentException("Missing value after --config=")
        }

        configPath = setConfigPath(configPath, value)
        index += 1
      } else {
        passthroughArgs += current
        index += 1
      }
    }

    AppArguments(
      configPath.getOrElse(throw new IllegalArgumentException("Missing required --config <path> argument")),
      passthroughArgs.result()
    )
  }

  private def setConfigPath(existing: Option[String], newValue: String): Option[String] = {
    if (existing.nonEmpty) {
      throw new IllegalArgumentException("Duplicate --config argument")
    }

    Some(newValue)
  }
}
