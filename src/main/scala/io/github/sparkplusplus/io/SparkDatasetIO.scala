package io.github.sparkplusplus.io

import io.github.sparkplusplus.SchemaUtils
import org.apache.spark.sql.{DataFrame, DataFrameReader, SparkSession}

object SparkDatasetIO {

  def readDataset(spark: SparkSession, dataset: DatasetConfig): DataFrame = {
    DatasetConfig.requireInput(dataset)
    val baseReader = spark.read.format(dataset.formatNormalized).options(dataset.options)
    readerWithSchema(baseReader, dataset).load(dataset.path)
  }

  def writeDataset(df: DataFrame, dataset: DatasetConfig): Unit = {
    DatasetConfig.requireOutput(dataset)

    val baseWriter = df.write.format(dataset.formatNormalized).options(dataset.options)
    val writerWithMode = dataset.mode.map(baseWriter.mode).getOrElse(baseWriter)
    val writerWithPartitions =
      if (dataset.partitionBy.nonEmpty) writerWithMode.partitionBy(dataset.partitionBy: _*) else writerWithMode

    writerWithPartitions.save(dataset.path)
  }

  private def readerWithSchema(reader: DataFrameReader, dataset: DatasetConfig): DataFrameReader = {
    val schema =
      dataset.schemaPath.map { path =>
        SchemaUtils.loadSchemaFromFile(path).getOrElse {
          throw new IllegalArgumentException(s"Failed to load schemaPath for dataset '${dataset.name}': $path")
        }
      }.orElse {
        dataset.schemaJson.map { json =>
          SchemaUtils.loadSchemaFromString(json).getOrElse {
            throw new IllegalArgumentException(s"Failed to parse schemaJson for dataset '${dataset.name}'")
          }
        }
      }

    schema.map(reader.schema).getOrElse(reader)
  }
}
