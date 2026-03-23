package io.github.sparkplusplus.io

import io.github.sparkplusplus.SchemaUtils
import org.apache.spark.sql.{DataFrame, DataFrameReader, SparkSession}

object SparkDatasetIO {

  def readInput(spark: SparkSession, dataset: InputDatasetConfig): DataFrame = {
    val baseReader = spark.read.format(dataset.formatNormalized).options(dataset.options)
    val loaded = readerWithSchema(baseReader, dataset).load(dataset.path)

    dataset.filter.map(loaded.filter).getOrElse(loaded)
  }

  def writeOutput(df: DataFrame, dataset: OutputDatasetConfig): Unit = {
    val preparedFrame = dataset.repartition.map(df.repartition).orElse(dataset.coalesce.map(df.coalesce)).getOrElse(df)

    val baseWriter = preparedFrame.write.format(dataset.formatNormalized).options(dataset.options)
    val writerWithMode = dataset.mode.map(baseWriter.mode).getOrElse(baseWriter)
    val writerWithPartitions =
      if (dataset.partitionBy.nonEmpty) writerWithMode.partitionBy(dataset.partitionBy: _*) else writerWithMode

    writerWithPartitions.save(dataset.path)
  }

  private def readerWithSchema(reader: DataFrameReader, dataset: InputDatasetConfig): DataFrameReader = {
    val schema =
      dataset.schemaPath.map { path =>
        SchemaUtils.loadSchemaFromFile(path).getOrElse {
          throw new IllegalArgumentException(s"Failed to load schemaPath for input dataset '${dataset.name}': $path")
        }
      }.orElse {
        dataset.schemaJson.map { json =>
          SchemaUtils.loadSchemaFromString(json).getOrElse {
            throw new IllegalArgumentException(s"Failed to parse schemaJson for input dataset '${dataset.name}'")
          }
        }
      }

    schema.map(reader.schema).getOrElse(reader)
  }
}
