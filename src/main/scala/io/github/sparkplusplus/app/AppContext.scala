package io.github.sparkplusplus.app

import io.github.sparkplusplus.io.{InputDatasetConfig, OutputDatasetConfig, SparkDatasetIO}
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger

final case class AppContext[C](
  spark: SparkSession,
  config: C,
  args: Seq[String],
  logger: Logger,
  runRecord: Option[RunRecord] = None
) {

  lazy val inputs: Map[String, InputDatasetConfig] =
    SparkApp.extractInputDatasets(config.asInstanceOf[AnyRef]).map(dataset => dataset.name -> dataset).toMap

  lazy val outputs: Map[String, OutputDatasetConfig] =
    SparkApp.extractOutputDatasets(config.asInstanceOf[AnyRef]).map(dataset => dataset.name -> dataset).toMap

  def input(name: String): InputDatasetConfig =
    inputs.getOrElse(name, throw new IllegalArgumentException(s"Input dataset '$name' is not defined"))

  def output(name: String): OutputDatasetConfig =
    outputs.getOrElse(name, throw new IllegalArgumentException(s"Output dataset '$name' is not defined"))

  def readInput(name: String): DataFrame =
    SparkDatasetIO.readInput(spark, input(name))

  def writeOutput(df: DataFrame, name: String): Unit =
    SparkDatasetIO.writeOutput(df, output(name))
}
