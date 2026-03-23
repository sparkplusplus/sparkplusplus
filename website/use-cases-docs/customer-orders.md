---
title: Customers and Orders to customer_orders
---

This use case joins `customers` and `orders` and writes a curated `customer_orders` Delta dataset.

## Goal

- input datasets: `customers` and `orders`
- output dataset: `customer_orders`
- output format: Delta
- objective: join customer master data with orders and publish a curated analytics-ready table

## Example App

```scala
import io.github.sparkplusplus._
import io.github.sparkplusplus.app.{AppContext, SparkApp}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{col, lower, trim}

final case class CustomerOrdersConfig(
  customersPath: String,
  ordersPath: String,
  outputPath: String,
  partitions: Int,
  sparkConfig: Map[String, String] = Map.empty
)

object CustomerOrdersApp extends SparkApp[CustomerOrdersConfig] {
  override protected def appName: String = "customer-orders-app"

  override protected def configClass: Class[CustomerOrdersConfig] =
    classOf[CustomerOrdersConfig]

  override protected def validateConfig(config: CustomerOrdersConfig): Unit = {
    require(config.partitions > 0, "partitions must be positive")
  }

  override protected def configureSpark(
    builder: SparkSession.Builder,
    config: CustomerOrdersConfig
  ): SparkSession.Builder = {
    builder.config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
  }

  override protected def run(ctx: AppContext[CustomerOrdersConfig]): Unit = {
    val customers = ctx.spark.read
      .format("parquet")
      .load(ctx.config.customersPath)
      .select(
        col("customer_id"),
        trim(col("customer_name")).alias("customer_name"),
        col("customer_segment")
      )

    val orders = ctx.spark.read
      .format("parquet")
      .load(ctx.config.ordersPath)
      .select(
        col("order_id"),
        col("customer_id"),
        lower(trim(col("order_status"))).alias("order_status"),
        col("order_total"),
        col("created_at")
      )
      .dedup("order_id")

    val customerOrders = orders.join(customers, Seq("customer_id"), "left")

    customerOrders.write
      .format("delta")
      .mode("overwrite")
      .save(ctx.config.outputPath)
  }
}
```

## Example YAML

```yaml
customersPath: s3://lakehouse/raw/customers
ordersPath: s3://lakehouse/raw/orders
outputPath: s3://lakehouse/silver/customer_orders
partitions: 200
sparkConfig:
  spark.sql.shuffle.partitions: "200"
  spark.sql.session.timeZone: UTC
```
