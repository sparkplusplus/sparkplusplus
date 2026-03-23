package io.github.sparkplusplus.app

import io.github.sparkplusplus.RequiresSparkRuntime
import io.github.sparkplusplus.io.DatasetConfig
import org.apache.spark.sql.SparkSession
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.slf4j.LoggerFactory

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class DatasetIoTest extends AnyFunSuite with BeforeAndAfterAll {

  private var spark: SparkSession = _

  override def beforeAll(): Unit = {
    spark = SparkSession.builder()
      .appName("DatasetIoTest")
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

  test("readDataset reads csv input datasets with schemaJson", RequiresSparkRuntime) {
    val csvPath = Files.createTempFile("sparkplusplus-customers-", ".csv")
    Files.write(
      csvPath,
      "customer_id,customer_name\n1,Alice\n2,Bob\n".getBytes(StandardCharsets.UTF_8)
    )

    val schemaJson =
      """{
        |  "type":"struct",
        |  "fields":[
        |    {"name":"customer_id","type":"integer","nullable":false,"metadata":{}},
        |    {"name":"customer_name","type":"string","nullable":true,"metadata":{}}
        |  ]
        |}""".stripMargin

    val ctx = AppContext(
      spark,
      DatasetIoTest.DatasetOnlyConfig(
        Seq(
          DatasetConfig(
            name = "customers",
            `type` = "input",
            path = csvPath.toString,
            format = "csv",
            options = Map("header" -> "true"),
            schemaJson = Some(schemaJson)
          )
        )
      ),
      Nil,
      LoggerFactory.getLogger("DatasetIoTest")
    )

    val df = ctx.readDataset("customers")

    assert(df.columns.sameElements(Array("customer_id", "customer_name")))
    assert(df.collect().map(row => (row.getInt(0), row.getString(1))).toSeq == Seq((1, "Alice"), (2, "Bob")))
  }

  test("writeDataset writes output datasets with mode and partitionBy", RequiresSparkRuntime) {
    val sparkSession = spark
    import sparkSession.implicits._

    val outputPath = Files.createTempDirectory("sparkplusplus-customer-orders-")
    val df = Seq(
      ("2026-03-24", "1", 100.0),
      ("2026-03-25", "2", 120.0)
    ).toDF("order_date", "customer_id", "order_total")

    val ctx = AppContext(
      spark,
      DatasetIoTest.DatasetOnlyConfig(
        Seq(
          DatasetConfig(
            name = "customer_orders",
            `type` = "output",
            path = outputPath.toString,
            format = "parquet",
            mode = Some("overwrite"),
            partitionBy = Seq("order_date")
          )
        )
      ),
      Nil,
      LoggerFactory.getLogger("DatasetIoTest")
    )

    ctx.writeDataset(df, "customer_orders")

    val written = spark.read.parquet(outputPath.toString)
    assert(written.count() == 2)
    assert(Files.exists(outputPath.resolve("order_date=2026-03-24")))
    assert(Files.exists(outputPath.resolve("order_date=2026-03-25")))
  }
}

object DatasetIoTest {
  final case class DatasetOnlyConfig(datasets: Seq[DatasetConfig]) extends SparkApp.HasDatasets
}
