package io.github.sparkplusplus

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.struct
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

class DataFrameUtilsTest extends AnyFunSuite with BeforeAndAfterAll {

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

  test("dedup should remove duplicate rows", RequiresSparkRuntime) {
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

  test("dedup with specific columns should work correctly", RequiresSparkRuntime) {
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

  test("addRowNumber should add row number column", RequiresSparkRuntime) {
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

  test("countNulls should count null values correctly", RequiresSparkRuntime) {
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

  test("getBasicStats should return statistics for numeric columns", RequiresSparkRuntime) {
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

  test("renameColumns should rename columns correctly", RequiresSparkRuntime) {
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

  test("flattenFields should unpack nested struct columns", RequiresSparkRuntime) {
    val sparkSession = spark
    import sparkSession.implicits._

    val nested = Seq(
      (1, "Alice", "premium"),
      (2, "Bob", "standard")
    ).toDF("order_id", "customer_name", "customer_segment")
      .select(
        $"order_id",
        struct($"customer_name", $"customer_segment").alias("customer")
      )

    val flattened = DataFrameUtils.flattenFields(nested)

    assert(flattened.columns.toSeq == Seq("order_id", "customer_customer_name", "customer_customer_segment"))

    val rows = flattened.collect().map(row => (row.getInt(0), row.getString(1), row.getString(2))).toSeq
    assert(rows == Seq((1, "Alice", "premium"), (2, "Bob", "standard")))
  }

  test("makeColumnNamesAvroCompliant should normalize invalid column names", RequiresSparkRuntime) {
    val sparkSession = spark
    import sparkSession.implicits._

    val df = Seq(
      ("Alice", 25)
    ).toDF("customer name", "1age")

    val compliantDf = DataFrameUtils.makeColumnNamesAvroCompliant(df)

    assert(compliantDf.columns.sameElements(Array("customer_name", "_age")))
    assert(compliantDf.schema("customer_name").metadata.getString("originalColumnName") == "customer name")
    assert(compliantDf.schema("_age").metadata.getString("originalColumnName") == "1age")
  }

  test("implicit DataFrame extensions should work", RequiresSparkRuntime) {
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

  test("implicit DataFrame extensions - addRowNumber should work", RequiresSparkRuntime) {
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

  test("implicit DataFrame extensions - countNulls should work", RequiresSparkRuntime) {
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

  test("implicit DataFrame extensions - renameColumns should work", RequiresSparkRuntime) {
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

  test("implicit DataFrame extensions - flattenFields should work", RequiresSparkRuntime) {
    val sparkSession = spark
    import sparkSession.implicits._

    val nested = Seq(
      (1, "Alice", "premium")
    ).toDF("order_id", "customer_name", "customer_segment")
      .select(
        $"order_id",
        struct($"customer_name", $"customer_segment").alias("customer")
      )

    val flattened = nested.flattenFields()

    assert(flattened.columns.toSeq == Seq("order_id", "customer_customer_name", "customer_customer_segment"))
  }

  test("implicit DataFrame extensions - makeColumnNamesAvroCompliant should work", RequiresSparkRuntime) {
    val sparkSession = spark
    import sparkSession.implicits._

    val df = Seq(
      ("Alice", 25)
    ).toDF("customer name", "1age")

    val compliantDf = df.makeColumnNamesAvroCompliant(prefix = "col_")

    assert(compliantDf.columns.sameElements(Array("col_customer_name", "col_1age")))
    assert(compliantDf.schema("col_customer_name").metadata.getString("originalColumnName") == "customer name")
  }
}
