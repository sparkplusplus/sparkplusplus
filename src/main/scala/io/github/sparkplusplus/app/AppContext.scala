package io.github.sparkplusplus.app

import io.github.sparkplusplus.io.{DatasetConfig, SparkDatasetIO}
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger

final case class AppContext[C](spark: SparkSession, config: C, args: Seq[String], logger: Logger) {

  lazy val datasets: Map[String, DatasetConfig] =
    SparkApp.extractDatasets(config.asInstanceOf[AnyRef]).map(dataset => dataset.name -> dataset).toMap

  def dataset(name: String): DatasetConfig =
    datasets.getOrElse(name, throw new IllegalArgumentException(s"Dataset '$name' is not defined"))

  def readDataset(name: String): DataFrame =
    SparkDatasetIO.readDataset(spark, dataset(name))

  def writeDataset(df: DataFrame, name: String): Unit =
    SparkDatasetIO.writeDataset(df, dataset(name))
}
