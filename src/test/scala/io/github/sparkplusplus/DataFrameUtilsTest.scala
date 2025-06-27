package io.github.sparkplusplus

import io.github.sparkplusplus.sparkutils.DataFrameUtils
import org.apache.spark.sql.SparkSession
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

class DataFrameUtilsTest extends AnyFunSuite with BeforeAndAfterAll {≤

  var spark: SparkSession = _

  override def beforeAll(): Unit = {
    spark = SparkSession.builder()
      .appName("DataFrameUtilsTest")
      .master("local[*]")
      .config("spark.sql.warehouse.dir", "/tmp/spark-warehouse")
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")
  }

  override def afterAll(): Unit = {
    if (spark != null) {
      spark.stop()
    }
  }

  test("dedup should remove duplicate rows") {
    val sparkSession = spark
    import sparkSession.implicits._

    val data = Seq(
      ("Alice", 25, "Engineer"),
      ("Bob", 30, "Manager"),
      ("Alice", 25, "Engineer"), // duplicate
      ("Charlie", 35, "Director")
    )

    val df = data.toDF("name", "age", "role")
    val dedupedDf = DataFrameUtils.dedup(df)

    assert(dedupedDf.count() == 3)
  }

  test("dedup with specific columns should work correctly") {
    val sparkSession = spark
    import sparkSession.implicits._

    val data = Seq(
      ("Alice", 25, "Engineer"),
      ("Bob", 30, "Manager"),
      ("Alice", 26, "Senior Engineer"), // different age, same name
      ("Charlie", 35, "Director")
    )

    val df = data.toDF("name", "age", "role")
    val dedupedDf = DataFrameUtils.dedup(df, Seq("name"))

    assert(dedupedDf.count() == 3) // Should keep only unique names
  }

  test("addRowNumber should add row number column") {
    val sparkSession = spark
    import sparkSession.implicits._

    val data = Seq(
      ("Alice", 25),
      ("Bob", 30),
      ("Charlie", 35)
    )

    val df = data.toDF("name", "age")
    val dfWithRowNum = DataFrameUtils.addRowNumber(df, "row_num", Seq("age"))

    assert(dfWithRowNum.columns.contains("row_num"))
    assert(dfWithRowNum.count() == 3)
  }

  test("countNulls should count null values correctly") {
    val sparkSession = spark
    import sparkSession.implicits._

    val data = Seq(
      (Some("Alice"), Some(25)),
      (Some("Bob"), None),
      (None, Some(35))
    )

    val df = data.toDF("name", "age")
    val nullCountsDf = DataFrameUtils.countNulls(df)

    assert(nullCountsDf.count() == 2) // Two columns

    val nullCounts = nullCountsDf.collect().map(row => (row.getString(0), row.getLong(1))).toMap
    assert(nullCounts("name") == 1)
    assert(nullCounts("age") == 1)
  }

  test("getBasicStats should return statistics for numeric columns") {
    val sparkSession = spark
    import sparkSession.implicits._

    val data = Seq(
      ("Alice", 25),
      ("Bob", 30),
      ("Charlie", 35)
    )

    val df = data.toDF("name", "age")
    val statsDf = DataFrameUtils.getBasicStats(df)

    assert(statsDf.count() > 0) // Should have statistics rows
    assert(statsDf.columns.contains("age")) // Should contain age column
  }

  test("renameColumns should rename columns correctly") {
    val sparkSession = spark
    import sparkSession.implicits._

    val data = Seq(
      ("Alice", 25),
      ("Bob", 30)
    )

    val df = data.toDF("name", "age")
    val renamedDf = DataFrameUtils.renameColumns(df, Map("name" -> "full_name", "age" -> "years"))

    assert(renamedDf.columns.contains("full_name"))
    assert(renamedDf.columns.contains("years"))
    assert(!renamedDf.columns.contains("name"))
    assert(!renamedDf.columns.contains("age"))
  }

  test("implicit DataFrame extensions should work") {
    val sparkSession = spark
    import sparkSession.implicits._

    val data = Seq(
      ("Alice", 25),
      ("Bob", 30),
      ("Alice", 25) // duplicate
    )

    val df = data.toDF("name", "age")
    val dedupedDf = df.dedup()

    assert(dedupedDf.count() == 2)
  }

  test("implicit DataFrame extensions - addRowNumber should work") {
    val sparkSession = spark
    import sparkSession.implicits._

    val data = Seq(
      ("Alice", 25),
      ("Bob", 30),
      ("Charlie", 35)
    )

    val df = data.toDF("name", "age")
    val dfWithRowNum = df.addRowNumber("row_id", "age")

    assert(dfWithRowNum.columns.contains("row_id"))
    assert(dfWithRowNum.count() == 3)
  }

  test("implicit DataFrame extensions - countNulls should work") {
    val sparkSession = spark
    import sparkSession.implicits._

    val data = Seq(
      (Some("Alice"), Some(25)),
      (Some("Bob"), None),
      (None, Some(35))
    )

    val df = data.toDF("name", "age")
    val nullCountsDf = df.countNulls()

    assert(nullCountsDf.count() == 2)
  }

  test("implicit DataFrame extensions - renameColumns should work") {
    val sparkSession = spark
    import sparkSession.implicits._

    val data = Seq(
      ("Alice", 25),
      ("Bob", 30)
    )

    val df = data.toDF("name", "age")
    val renamedDf = df.renameColumns(Map("name" -> "full_name"))

    assert(renamedDf.columns.contains("full_name"))
    assert(!renamedDf.columns.contains("name"))
  }
}
