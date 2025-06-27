package io.github

import org.apache.spark.sql.DataFrame

package object sparkplusplus {

  /**
   * Implicit class to add utility methods directly to DataFrame
   */
  implicit class DataFrameExtensions(df: DataFrame) {

    def dedup(columns: String*): DataFrame = {
      DataFrameUtils.dedup(df, columns)
    }

    def addRowNumber(columnName: String, orderBy: String*): DataFrame = {
      DataFrameUtils.addRowNumber(df, columnName, orderBy)
    }

    def addRowNumber(): DataFrame = {
      DataFrameUtils.addRowNumber(df, "row_number", Seq.empty)
    }

    def countNulls(): DataFrame = {
      DataFrameUtils.countNulls(df)
    }

    def getBasicStats(): DataFrame = {
      DataFrameUtils.getBasicStats(df)
    }

    def renameColumns(columnMapping: Map[String, String]): DataFrame = {
      DataFrameUtils.renameColumns(df, columnMapping)
    }
  }
}
