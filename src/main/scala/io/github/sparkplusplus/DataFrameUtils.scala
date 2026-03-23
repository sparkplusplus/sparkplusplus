package io.github.sparkplusplus

import org.apache.spark.sql.Column
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{MetadataBuilder, StructField, StructType}

object DataFrameUtils {

  /**
   * Remove duplicate rows from a DataFrame
   * @param df Input DataFrame
   * @param columns Optional list of columns to consider for deduplication
   * @return DataFrame with duplicates removed
   */
  def dedup(df: DataFrame, columns: Seq[String] = Seq.empty): DataFrame = {
    if (columns.nonEmpty) {
      df.dropDuplicates(columns)
    } else {
      df.dropDuplicates()
    }
  }

  /**
   * Add a row number column to the DataFrame
   * @param df Input DataFrame
   * @param columnName Name of the row number column (default: "row_number")
   * @param orderBy Optional columns to order by
   * @return DataFrame with row number column added
   */
  def addRowNumber(df: DataFrame, columnName: String = "row_number", orderBy: Seq[String] = Seq.empty): DataFrame = {
    import org.apache.spark.sql.expressions.Window
    
    val windowSpec = if (orderBy.nonEmpty) {
      Window.orderBy(orderBy.map(col): _*)
    } else {
      Window.orderBy(lit(1))
    }
    
    df.withColumn(columnName, row_number().over(windowSpec))
  }

  /**
   * Count null values in each column
   * @param df Input DataFrame
   * @return DataFrame with column names and their null counts
   */
  def countNulls(df: DataFrame): DataFrame = {
    val spark = df.sparkSession
    
    val nullCounts = df.columns.map { colName =>
      sum(when(col(colName).isNull, 1).otherwise(0)).alias(colName)
    }
    
    val result = df.agg(nullCounts.head, nullCounts.tail: _*)
    
    // Transpose the result
    val columnNames = df.columns
    val nullCountsRow = result.collect()(0)
    
    val data = columnNames.zip(nullCountsRow.toSeq).map { case (colName, nullCount) =>
      (colName, nullCount.asInstanceOf[Long])
    }
    
    spark.createDataFrame(data).toDF("column_name", "null_count")
  }

  /**
   * Get basic statistics for numeric columns
   * @param df Input DataFrame
   * @return DataFrame with basic statistics
   */
  def getBasicStats(df: DataFrame): DataFrame = {
    val numericColumns = df.dtypes.filter { case (_, dataType) =>
      dataType.contains("Int") || dataType.contains("Long") || 
      dataType.contains("Float") || dataType.contains("Double") || 
      dataType.contains("Decimal")
    }.map(_._1)
    
    if (numericColumns.nonEmpty) {
      df.select(numericColumns.map(col): _*).describe()
    } else {
      df.sparkSession.emptyDataFrame
    }
  }

  /**
   * Rename columns using a mapping
   * @param df Input DataFrame
   * @param columnMapping Map of old column names to new column names
   * @return DataFrame with renamed columns
   */
  def renameColumns(df: DataFrame, columnMapping: Map[String, String]): DataFrame = {
    columnMapping.foldLeft(df) { case (tempDf, (oldName, newName)) =>
      if (tempDf.columns.contains(oldName)) {
        tempDf.withColumnRenamed(oldName, newName)
      } else {
        tempDf
      }
    }
  }

  /**
   * Flatten nested struct columns into top-level columns using underscore-separated names.
   * Example: `customer.id` becomes `customer_id`.
   */
  def flattenFields(df: DataFrame): DataFrame = {
    def createAliases(field: StructField, ancestors: Seq[String] = Nil): Seq[(String, String)] =
      field.dataType match {
        case StructType(children) =>
          children.flatMap(child => createAliases(child, ancestors :+ field.name))
        case _ =>
          val fullPath = ancestors :+ field.name
          Seq(fullPath.mkString(".") -> fullPath.mkString("_"))
      }

    val selectColumns = df.schema.fields.toSeq
      .flatMap(field => createAliases(field))
      .map { case (originalPath, aliasedName) => new Column(originalPath).as(aliasedName) }

    df.select(selectColumns: _*)
  }

  /**
   * Rename DataFrame columns so they are Avro-compliant and keep the original name in metadata.
   */
  def makeColumnNamesAvroCompliant(
    df: DataFrame,
    replaceWith: String = "_",
    prefix: String = "",
    suffix: String = ""
  ): DataFrame = {
    val newSchema = StructType(df.schema.fields.map { field =>
      val newFieldName = makeNameAvroCompliant(field.name, replaceWith, prefix, suffix)
      val newMetadata =
        new MetadataBuilder().withMetadata(field.metadata).putString("originalColumnName", field.name).build()
      field.copy(name = newFieldName, metadata = newMetadata)
    })

    df.sparkSession.createDataFrame(df.rdd, newSchema)
  }

  private[sparkplusplus] def makeNameAvroCompliant(
    string: String,
    replaceWith: String,
    prefix: String,
    suffix: String
  ): String = {
    def acceptableFirstChar(char: Char): Boolean =
      (char >= 'a' && char <= 'z') || (char >= 'A' && char <= 'Z') || char == '_'

    def acceptableTailChar(char: Char): Boolean = char.isDigit || acceptableFirstChar(char)

    def illegalContentChars(value: String): Set[Char] = value.filterNot(acceptableTailChar).toSet

    def requireFirstChar(value: String, printName: String): Unit =
      require(
        if (value.nonEmpty) acceptableFirstChar(value.head) else true,
        s"The $printName starts with an illegal Avro character: '${value.head}'."
      )

    def requireContentChars(value: String, printName: String): Unit =
      require(
        if (value.nonEmpty) illegalContentChars(value).isEmpty else true,
        s"The $printName contains illegal Avro character(s): '${illegalContentChars(value).mkString("'", ", ", "'")}'."
      )

    require(string.nonEmpty, "The input string can not be empty.")

    if (prefix.nonEmpty) {
      requireFirstChar(prefix, "prefix")
      requireContentChars(prefix, "prefix")
    } else {
      requireFirstChar(replaceWith, "replacement string")
    }

    requireContentChars(replaceWith, "replacement string")
    requireContentChars(suffix, "suffix")

    val first = if (prefix.isEmpty && !acceptableFirstChar(string.head)) replaceWith else string.head.toString
    val body = string.tail.toSeq.flatMap { char =>
      if (acceptableTailChar(char)) Seq(char) else replaceWith
    }.mkString

    prefix + first + body + suffix
  }
}
