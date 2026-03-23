package io.github.sparkplusplus.io

final case class DatasetConfig(
  name: String,
  `type`: String,
  path: String,
  format: String,
  options: Map[String, String] = Map.empty,
  mode: Option[String] = None,
  partitionBy: Seq[String] = Seq.empty,
  schemaPath: Option[String] = None,
  schemaJson: Option[String] = None
) {

  def datasetTypeNormalized: String = `type`.trim.toLowerCase

  def formatNormalized: String = format.trim.toLowerCase
}

object DatasetConfig {
  val InputType = "input"
  val OutputType = "output"
  val SupportedFormats: Set[String] = Set("csv", "json", "parquet", "delta", "text")

  def validateAll(datasets: Seq[DatasetConfig]): Seq[DatasetConfig] = {
    val duplicateNames = datasets.groupBy(_.name).collect { case (name, entries) if entries.size > 1 => name }.toSeq.sorted
    require(duplicateNames.isEmpty, s"Duplicate dataset names: ${duplicateNames.mkString(", ")}")

    datasets.foreach(validate)
    datasets
  }

  def validate(dataset: DatasetConfig): Unit = {
    require(dataset.name.trim.nonEmpty, "Dataset name must not be empty")
    require(dataset.path.trim.nonEmpty, s"Dataset '${dataset.name}' path must not be empty")
    require(dataset.format.trim.nonEmpty, s"Dataset '${dataset.name}' format must not be empty")
    require(
      SupportedFormats.contains(dataset.formatNormalized),
      s"Dataset '${dataset.name}' format '${dataset.format}' is not supported. Supported formats: ${SupportedFormats.toSeq.sorted.mkString(", ")}"
    )

    dataset.datasetTypeNormalized match {
      case InputType =>
        require(dataset.mode.isEmpty, s"Input dataset '${dataset.name}' must not define mode")
        require(dataset.partitionBy.isEmpty, s"Input dataset '${dataset.name}' must not define partitionBy")
      case OutputType =>
        require(dataset.schemaPath.isEmpty, s"Output dataset '${dataset.name}' must not define schemaPath")
        require(dataset.schemaJson.isEmpty, s"Output dataset '${dataset.name}' must not define schemaJson")
      case other =>
        throw new IllegalArgumentException(
          s"Dataset '${dataset.name}' type '$other' is not supported. Allowed values: input, output"
        )
    }

    require(
      !(dataset.schemaPath.nonEmpty && dataset.schemaJson.nonEmpty),
      s"Dataset '${dataset.name}' must not define both schemaPath and schemaJson"
    )
  }

  def requireInput(dataset: DatasetConfig): Unit =
    require(dataset.datasetTypeNormalized == InputType, s"Dataset '${dataset.name}' is not an input dataset")

  def requireOutput(dataset: DatasetConfig): Unit =
    require(dataset.datasetTypeNormalized == OutputType, s"Dataset '${dataset.name}' is not an output dataset")
}
