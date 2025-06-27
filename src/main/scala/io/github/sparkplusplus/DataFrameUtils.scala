package io.github.sparkplusplus.sparkutils

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._

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
}