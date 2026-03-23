---
title: Orders and Order Items to order_line_facts
---

This use case joins `orders` and `order_items` to produce an item-level facts table.

## Goal

- input datasets: `orders` and `order_items`
- output dataset: `order_line_facts`
- output format: Delta
- objective: enrich each order item with order-level attributes for analytics and BI

## Example App

```scala
import io.github.sparkplusplus.app.{AppContext, SparkApp}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.col

final case class OrderLineFactsConfig(
  ordersPath: String,
  orderItemsPath: String,
  outputPath: String,
  sparkConfig: Map[String, String] = Map.empty
)

object OrderLineFactsApp extends SparkApp[OrderLineFactsConfig] {
  override protected def appName: String = "order-line-facts-app"

  override protected def configClass: Class[OrderLineFactsConfig] =
    classOf[OrderLineFactsConfig]

  override protected def configureSpark(
    builder: SparkSession.Builder,
    config: OrderLineFactsConfig
  ): SparkSession.Builder = {
    builder.config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
  }

  override protected def run(ctx: AppContext[OrderLineFactsConfig]): Unit = {
    val orders = ctx.spark.read.parquet(ctx.config.ordersPath)
    val orderItems = ctx.spark.read.parquet(ctx.config.orderItemsPath)

    orderItems
      .join(
        orders.select(
          col("order_id"),
          col("customer_id"),
          col("order_status"),
          col("created_at")
        ),
        Seq("order_id"),
        "inner"
      )
      .write
      .format("delta")
      .mode("overwrite")
      .save(ctx.config.outputPath)
  }
}
```

## Example YAML

```yaml
ordersPath: s3://lakehouse/raw/orders
orderItemsPath: s3://lakehouse/raw/order_items
outputPath: s3://lakehouse/silver/order_line_facts
sparkConfig:
  spark.sql.shuffle.partitions: "200"
  spark.sql.session.timeZone: UTC
```
