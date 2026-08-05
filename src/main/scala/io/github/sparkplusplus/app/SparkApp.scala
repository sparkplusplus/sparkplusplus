package io.github.sparkplusplus.app

import io.github.sparkplusplus.io.{DatasetCollection, DatasetConfig, InputDatasetConfig, OutputDatasetConfig}
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
    SparkApp.extractDatasetCollection(config)

    val logger = createLogger()
    beforeSparkStart(config, parsedArgs.passthroughArgs, logger)
    val spark = sparkLifecycle.create(
      appName,
      config,
      extractSparkConfig(config),
      logger,
      configureSpark
    )
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

  protected def extractSparkConfig(config: C): Map[String, String] = SparkApp.extractSparkConfig(config)

  protected def beforeSparkStart(config: C, args: Seq[String], logger: Logger): Unit = ()

  protected def createLogger(): Logger = LoggerFactory.getLogger(getClass)

  protected[app] def sparkLifecycle: SparkApp.SparkLifecycle[C] = new SparkApp.DefaultSparkLifecycle[C]
}

object SparkApp {

  private[app] trait SparkLifecycle[C <: AnyRef] {
    def create(
      appName: String,
      config: C,
      sparkConfig: Map[String, String],
      logger: Logger,
      configureSpark: (SparkSession.Builder, C) => SparkSession.Builder
    ): SparkSession

    def stop(spark: SparkSession, runFailure: Throwable, logger: Logger): Unit
  }

  private[app] final class DefaultSparkLifecycle[C <: AnyRef] extends SparkLifecycle[C] {
    override def create(
      appName: String,
      config: C,
      sparkConfig: Map[String, String],
      logger: Logger,
      configureSpark: (SparkSession.Builder, C) => SparkSession.Builder
    ): SparkSession = {
      val builder = SparkSession.builder().appName(appName)
      val withFrameworkConfig = sparkConfig.foldLeft(builder) { case (currentBuilder, (key, value)) =>
        currentBuilder.config(key, value)
      }
      configureSpark(withFrameworkConfig, config).getOrCreate()
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

  trait HasSparkConfig {
    def sparkConfig: Map[String, String]
  }

  trait WithInputDatasets {
    def inputs: Seq[InputDatasetConfig]
  }

  trait WithOutputDatasets {
    def outputs: Seq[OutputDatasetConfig]
  }

  def extractSparkConfig(config: AnyRef): Map[String, String] = config match {
    case null => Map.empty
    case provider: HasSparkConfig => provider.sparkConfig
    case _ =>
      extractSparkConfigByConvention(config)
  }

  def extractDatasetCollection(config: AnyRef): DatasetCollection = config match {
    case null => DatasetCollection()
    case provider: WithInputDatasets with WithOutputDatasets =>
      DatasetConfig.validateAll(DatasetCollection(provider.inputs, provider.outputs))
    case provider: WithInputDatasets =>
      DatasetConfig.validateAll(DatasetCollection(inputs = provider.inputs))
    case provider: WithOutputDatasets =>
      DatasetConfig.validateAll(DatasetCollection(outputs = provider.outputs))
    case _ =>
      extractDatasetsByConvention(config)
  }

  def extractInputDatasets(config: AnyRef): Seq[InputDatasetConfig] =
    extractDatasetCollection(config).inputs

  def extractOutputDatasets(config: AnyRef): Seq[OutputDatasetConfig] =
    extractDatasetCollection(config).outputs

  private def extractSparkConfigByConvention(config: AnyRef): Map[String, String] = {
    val candidateMethods = Seq("sparkConfig", "sparkConf")
    val configClass = config.getClass

    candidateMethods.iterator
      .flatMap { methodName =>
        try {
          Some(configClass.getMethod(methodName))
        } catch {
          case _: NoSuchMethodException => None
        }
      }
      .collectFirst {
        case method if method.getParameterCount == 0 =>
          method.invoke(config) match {
            case map: scala.collection.Map[_, _] =>
              map.iterator.map { case (key, value) =>
                key.toString -> value.toString
              }.toMap
            case _ =>
              throw new IllegalArgumentException(
                s"${configClass.getSimpleName}.${method.getName} must return Map[String, String]"
              )
          }
      }
      .getOrElse(Map.empty)
  }

  private def extractDatasetsByConvention(config: AnyRef): DatasetCollection = {
    val configClass = config.getClass
    val inputs = extractDatasetSeq(config, configClass, "inputs", "InputDatasetConfig").map {
      case dataset: InputDatasetConfig => dataset
      case other =>
        throw new IllegalArgumentException(
          s"${configClass.getSimpleName}.inputs must contain InputDatasetConfig entries, found ${other.getClass.getName}"
        )
    }
    val outputs = extractDatasetSeq(config, configClass, "outputs", "OutputDatasetConfig").map {
      case dataset: OutputDatasetConfig => dataset
      case other =>
        throw new IllegalArgumentException(
          s"${configClass.getSimpleName}.outputs must contain OutputDatasetConfig entries, found ${other.getClass.getName}"
        )
    }

    DatasetConfig.validateAll(DatasetCollection(inputs, outputs))
  }

  private def extractDatasetSeq(
    config: AnyRef,
    configClass: Class[_],
    methodName: String,
    expectedTypeName: String
  ): Seq[Any] = {
    try {
      val method = configClass.getMethod(methodName)
      if (method.getParameterCount != 0) {
        Seq.empty
      } else {
        method.invoke(config) match {
          case null => Seq.empty
          case seq: Seq[_] => seq
          case other =>
            throw new IllegalArgumentException(
              s"${configClass.getSimpleName}.$methodName must return Seq[$expectedTypeName], found ${other.getClass.getName}"
            )
        }
      }
    } catch {
      case _: NoSuchMethodException => Seq.empty
    }
  }
}
