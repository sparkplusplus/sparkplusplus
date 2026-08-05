package io.github.sparkplusplus.io

final case class InputDatasetConfig(
  name: String,
  path: String,
  format: String,
  options: Map[String, String] = Map.empty,
  schemaPath: Option[String] = None,
  schemaJson: Option[String] = None,
  filter: Option[String] = None
) {

  def formatNormalized: String = format.trim.toLowerCase
}

final case class OutputDatasetConfig(
  name: String,
  path: String,
  format: String,
  options: Map[String, String] = Map.empty,
  mode: Option[String] = None,
  partitionBy: Seq[String] = Seq.empty,
  repartition: Option[Int] = None,
  coalesce: Option[Int] = None
) {

  def formatNormalized: String = format.trim.toLowerCase
}

final case class DatasetCollection(
  inputs: Seq[InputDatasetConfig] = Seq.empty,
  outputs: Seq[OutputDatasetConfig] = Seq.empty
)

object DatasetConfig {
  val SupportedFormats: Set[String] = Set("csv", "json", "parquet", "delta", "text")

  def validateAll(collection: DatasetCollection): DatasetCollection = {
    validateUniqueNames(collection.inputs.map(_.name), "input")
    validateUniqueNames(collection.outputs.map(_.name), "output")

    val overlappingNames = collection.inputs.map(_.name).toSet.intersect(collection.outputs.map(_.name).toSet).toSeq.sorted
    require(overlappingNames.isEmpty, s"Dataset names must be unique across inputs and outputs: ${overlappingNames.mkString(", ")}")

    collection.inputs.foreach(validateInput)
    collection.outputs.foreach(validateOutput)
    collection
  }

  def validateInput(dataset: InputDatasetConfig): Unit = {
    require(dataset.name.trim.nonEmpty, "Input dataset name must not be empty")
    require(dataset.path.trim.nonEmpty, s"Input dataset '${dataset.name}' path must not be empty")
    validateFormat(dataset.name, dataset.format, dataset.formatNormalized, "Input")
    require(
      !(dataset.schemaPath.nonEmpty && dataset.schemaJson.nonEmpty),
      s"Input dataset '${dataset.name}' must not define both schemaPath and schemaJson"
    )
    dataset.filter.foreach { expression =>
      require(expression.trim.nonEmpty, s"Input dataset '${dataset.name}' filter must not be empty")
    }
  }

  def validateOutput(dataset: OutputDatasetConfig): Unit = {
    require(dataset.name.trim.nonEmpty, "Output dataset name must not be empty")
    require(dataset.path.trim.nonEmpty, s"Output dataset '${dataset.name}' path must not be empty")
    validateFormat(dataset.name, dataset.format, dataset.formatNormalized, "Output")

    require(
      !(dataset.repartition.nonEmpty && dataset.coalesce.nonEmpty),
      s"Output dataset '${dataset.name}' must not define both repartition and coalesce"
    )
    dataset.repartition.foreach { value =>
      require(value > 0, s"Output dataset '${dataset.name}' repartition must be positive")
    }
    dataset.coalesce.foreach { value =>
      require(value > 0, s"Output dataset '${dataset.name}' coalesce must be positive")
    }
  }

  private def validateUniqueNames(names: Seq[String], datasetType: String): Unit = {
    val duplicateNames = names.groupBy(identity).collect { case (name, entries) if entries.size > 1 => name }.toSeq.sorted
    require(duplicateNames.isEmpty, s"Duplicate $datasetType dataset names: ${duplicateNames.mkString(", ")}")
  }

  private def validateFormat(name: String, rawFormat: String, normalizedFormat: String, datasetType: String): Unit = {
    require(rawFormat.trim.nonEmpty, s"$datasetType dataset '$name' format must not be empty")
    require(
      SupportedFormats.contains(normalizedFormat),
      s"$datasetType dataset '$name' format '$rawFormat' is not supported. Supported formats: ${SupportedFormats.toSeq.sorted.mkString(", ")}"
    )
  }
}
