package io.github.sparkplusplus.samples.customerorders

import org.apache.spark.sql.SparkSession
import org.scalatest.funsuite.AnyFunSuite

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class CustomerOrdersAppTest extends AnyFunSuite {

  test("customer orders sample runs end-to-end with local config and fixture data") {
    val outputDir = Files.createTempDirectory("customer-orders-output-")
    val warehouseDir = Files.createTempDirectory("customer-orders-warehouse-")
    val customersPath = resourcePath("input/customers.json")
    val ordersPath = resourcePath("input/orders.csv")
    val customersSchemaPath = projectPath("schemas/customers.json")
    val ordersSchemaPath = projectPath("schemas/orders.json")

    val configContent =
      s"""inputs:
         |  - name: customers
         |    path: $customersPath
         |    format: json
         |    schemaPath: $customersSchemaPath
         |    filter: customer_status = 'ACTIVE'
         |  - name: orders
         |    path: $ordersPath
         |    format: csv
         |    options:
         |      header: "true"
         |    schemaPath: $ordersSchemaPath
         |outputs:
         |  - name: customer_orders
         |    path: ${outputDir.toString}
         |    format: parquet
         |    mode: overwrite
         |    partitionBy:
         |      - order_order_date
         |    coalesce: 1
         |sparkConfig:
         |  spark.master: local[2]
         |  spark.app.name: customer-orders-test
         |  spark.ui.enabled: "false"
         |  spark.sql.shuffle.partitions: "1"
         |  spark.sql.session.timeZone: UTC
         |  spark.sql.warehouse.dir: ${warehouseDir.toString}
         |""".stripMargin

    val configPath = Files.createTempFile("customer-orders-config-", ".yaml")
    Files.write(configPath, configContent.getBytes(StandardCharsets.UTF_8))

    CustomerOrdersApp.main(Array("--config", configPath.toString))

    val spark = SparkSession.builder()
      .appName("CustomerOrdersAppTestReadback")
      .master("local[2]")
      .config("spark.ui.enabled", "false")
      .config("spark.sql.shuffle.partitions", "1")
      .config("spark.sql.session.timeZone", "UTC")
      .config("spark.sql.warehouse.dir", warehouseDir.toString)
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    try {
      val written = spark.read.parquet(outputDir.toString)
      val actualRows = written
        .select(
          "order_id",
          "customer_customer_id",
          "customer_customer_name",
          "customer_customer_segment",
          "order_order_status",
          "order_order_total",
          "order_order_date"
        )
        .collect()
        .map(row =>
          (
            row.getString(0),
            row.getString(1),
            row.getString(2),
            row.getString(3),
            row.getString(4),
            row.getDouble(5),
            row.getDate(6).toString
          )
        )
        .toSeq
        .sortBy(_._1)

      val expectedRows = Seq(
        ("o-100", "c-001", "Alice Smith", "enterprise", "completed", 125.5, "2026-03-24"),
        ("o-102", "c-003", null, null, "processing", 42.0, "2026-03-26")
      )

      assert(actualRows == expectedRows)
      assert(Files.exists(outputDir.resolve("order_order_date=2026-03-24")))
      assert(Files.exists(outputDir.resolve("order_order_date=2026-03-26")))
    } finally {
      spark.stop()
    }
  }

  private def resourcePath(name: String): String =
    new java.io.File(getClass.getClassLoader.getResource(name).toURI).getAbsolutePath

  private def projectPath(name: String): String =
    new java.io.File(name).getAbsolutePath
}
