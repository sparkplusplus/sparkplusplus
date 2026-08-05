package io.github.sparkplusplus.config

import io.github.sparkplusplus.app.{AppContext, SparkApp, SparkETLApp, YamlConfigLoader}
import io.github.sparkplusplus.io.{DatasetCollection, DatasetConfig, InputDatasetConfig, OutputDatasetConfig}
import org.apache.spark.sql.DataFrame

import java.nio.file.{Path, Paths}

final case class ApplicationConfig[C](name: String, version: String = "unknown", settings: C)

final case class FrameworkConfig[C](
  apiVersion: String,
  application: ApplicationConfig[C],
  sparkConfig: Map[String, String] = Map.empty,
  inputs: Seq[InputDatasetConfig] = Seq.empty,
  outputs: Seq[OutputDatasetConfig] = Seq.empty
) extends SparkApp.HasSparkConfig
    with SparkApp.WithInputDatasets
    with SparkApp.WithOutputDatasets

object FrameworkConfig {
  val ApiVersion: String = "sparkplusplus.io/v1"

  def validate[C](config: FrameworkConfig[C]): FrameworkConfig[C] = {
    require(
      config.apiVersion == ApiVersion,
      s"Unsupported apiVersion '${config.apiVersion}'. Supported version: $ApiVersion. See the migration guide before changing apiVersion."
    )
    require(config.application.name.trim.nonEmpty, "application.name must not be empty")
    require(config.application.version.trim.nonEmpty, "application.version must not be empty")
    DatasetConfig.validateAll(DatasetCollection(config.inputs, config.outputs))
    config
  }
}

object FrameworkConfigLoader {

  private val RootFields = Set("apiVersion", "application", "sparkConfig", "inputs", "outputs")
  private val ApplicationFields = Set("name", "version", "settings")

  def load[C](path: String, settingsClass: Class[C]): FrameworkConfig[C] =
    load(Paths.get(path), settingsClass)

  def load[C](path: Path, settingsClass: Class[C]): FrameworkConfig[C] = {
    val root = YamlConfigLoader.loadRaw(path)
    validateRoot(root)

    val application = requiredMap(root, "application", "config")
    validateFields(application, ApplicationFields, "config.application")
    val settings = application.getOrElse(
      "settings",
      throw new IllegalArgumentException("Missing required config field: config.application.settings")
    )

    val config = FrameworkConfig(
      apiVersion = requiredString(root, "apiVersion", "config"),
      application = ApplicationConfig(
        name = requiredString(application, "name", "config.application"),
        version = optionalString(application, "version").getOrElse("unknown"),
        settings = YamlConfigLoader.decode(settings, settingsClass, "config.application.settings")
      ),
      sparkConfig = optionalMap(root, "sparkConfig", "config").map(stringMap(_, "config.sparkConfig")).getOrElse(Map.empty),
      inputs = optionalValue(root, "inputs").map(value => YamlConfigLoader.decodeSeq(value, classOf[InputDatasetConfig], "config.inputs")).getOrElse(Seq.empty),
      outputs = optionalValue(root, "outputs").map(value => YamlConfigLoader.decodeSeq(value, classOf[OutputDatasetConfig], "config.outputs")).getOrElse(Seq.empty)
    )

    FrameworkConfig.validate(config)
  }

  def validate(path: String): Unit = validate(Paths.get(path))

  def validate(path: Path): Unit = {
    val root = YamlConfigLoader.loadRaw(path)
    validateRoot(root)
    val application = requiredMap(root, "application", "config")
    validateFields(application, ApplicationFields, "config.application")
    requiredString(application, "name", "config.application")
    requiredValue(application, "settings", "config.application")

    val config = FrameworkConfig[Map[String, String]](
      apiVersion = requiredString(root, "apiVersion", "config"),
      application = ApplicationConfig(
        requiredString(application, "name", "config.application"),
        optionalString(application, "version").getOrElse("unknown"),
        Map.empty
      ),
      sparkConfig = optionalMap(root, "sparkConfig", "config").map(stringMap(_, "config.sparkConfig")).getOrElse(Map.empty),
      inputs = optionalValue(root, "inputs").map(value => YamlConfigLoader.decodeSeq(value, classOf[InputDatasetConfig], "config.inputs")).getOrElse(Seq.empty),
      outputs = optionalValue(root, "outputs").map(value => YamlConfigLoader.decodeSeq(value, classOf[OutputDatasetConfig], "config.outputs")).getOrElse(Seq.empty)
    )
    FrameworkConfig.validate(config)
  }

  private def validateRoot(root: Map[String, Any]): Unit = validateFields(root, RootFields, "config")

  private def validateFields(data: Map[String, Any], fields: Set[String], location: String): Unit = {
    val unknown = data.keySet.diff(fields).toSeq.sorted
    require(unknown.isEmpty, s"Unknown config fields at $location: ${unknown.mkString(", ")}")
  }

  private def requiredMap(data: Map[String, Any], name: String, location: String): Map[String, Any] =
    requiredValue(data, name, location) match {
      case map: Map[_, _] => map.asInstanceOf[Map[String, Any]]
      case value => throw new IllegalArgumentException(s"Expected object at $location.$name but found ${value.getClass.getSimpleName}")
    }

  private def optionalMap(data: Map[String, Any], name: String, location: String): Option[Map[String, Any]] =
    optionalValue(data, name).map { value =>
      value match {
        case map: Map[_, _] => map.asInstanceOf[Map[String, Any]]
        case other => throw new IllegalArgumentException(s"Expected object at $location.$name but found ${other.getClass.getSimpleName}")
      }
    }

  private def requiredString(data: Map[String, Any], name: String, location: String): String =
    requiredValue(data, name, location) match {
      case string: String if string.trim.nonEmpty => string
      case _: String => throw new IllegalArgumentException(s"$location.$name must not be empty")
      case value => value.toString
    }

  private def optionalString(data: Map[String, Any], name: String): Option[String] =
    optionalValue(data, name).map(_.toString)

  private def requiredValue(data: Map[String, Any], name: String, location: String): Any =
    data.getOrElse(name, throw new IllegalArgumentException(s"Missing required config field: $location.$name"))

  private def optionalValue(data: Map[String, Any], name: String): Option[Any] = data.get(name)

  private def stringMap(data: Map[String, Any], location: String): Map[String, String] =
    data.map { case (key, value) =>
      require(value != null, s"$location.$key must not be null")
      key -> value.toString
    }

}

abstract class FrameworkSparkETLApp[C <: AnyRef] extends SparkETLApp[FrameworkConfig[C]] {

  private var loadedApplication: ApplicationConfig[C] = _

  protected def applicationSettingsClass: Class[C]

  protected def transform(
    ctx: AppContext[FrameworkConfig[C]],
    settings: C,
    inputs: Map[String, DataFrame]
  ): Map[String, DataFrame]

  final override protected def appName: String = loadedApplication.name

  final override protected def appVersion: String = loadedApplication.version

  final override protected def configClass: Class[FrameworkConfig[C]] =
    classOf[FrameworkConfig[_]].asInstanceOf[Class[FrameworkConfig[C]]]

  final override protected def loadConfig(configPath: String): FrameworkConfig[C] = {
    val config = FrameworkConfigLoader.load(configPath, applicationSettingsClass)
    loadedApplication = config.application
    config
  }

  final override protected def transform(
    ctx: AppContext[FrameworkConfig[C]],
    inputs: Map[String, DataFrame]
  ): Map[String, DataFrame] = transform(ctx, ctx.config.application.settings, inputs)
}
