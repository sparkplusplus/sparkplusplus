package io.github.sparkplusplus.testkit

import org.apache.spark.sql.SparkSession

object LocalSparkSession {

  def withSession[A](appName: String = "sparkplusplus-test")(test: SparkSession => A): A = {
    val spark = SparkSession.builder()
      .appName(appName)
      .master("local[2]")
      .config("spark.ui.enabled", "false")
      .config("spark.sql.shuffle.partitions", "1")
      .config("spark.sql.warehouse.dir", "target/spark-warehouse")
      .getOrCreate()

    try test(spark)
    finally spark.stop()
  }
}
