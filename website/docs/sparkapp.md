---
sidebar_position: 4
title: SparkApp Guide
---

`SparkApp[C]` is the main entrypoint abstraction in SparkPlusPlus.

## Core Contract

You implement:

- `appName`
- `configClass`
- `run(ctx)`

You can also override:

- `validateConfig(config)`
- `configureSpark(builder, config)`
- `beforeSparkStart(config, args, logger)`

## Example

This example reads `customers` and `orders`, joins them, and writes a curated `customer_orders` dataset in Delta format.

```scala
import io.github.sparkplusplus._
import io.github.sparkplusplus.app.{AppContext, SparkApp}
import org.apache.spark.sql.SparkSession
import io.github.sparkplusplus.io.DatasetConfig
import org.apache.spark.sql.functions.{col, lower, trim}

final case class OrdersConfig(
  datasets: Seq[DatasetConfig],
  sparkConfig: Map[String, String] = Map.empty
) extends SparkApp.HasDatasets with SparkApp.HasSparkConfig

object OrdersJob extends SparkApp[OrdersConfig] {
  override protected def appName: String = "orders-job"

  override protected def configClass: Class[OrdersConfig] = classOf[OrdersConfig]

  override protected def configureSpark(
    builder: SparkSession.Builder,
    config: OrdersConfig
  ): SparkSession.Builder = {
    builder
      .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
  }

  override protected def run(ctx: AppContext[OrdersConfig]): Unit = {
    val customers = ctx.readDataset("customers")
      .select(
        col("customer_id"),
        trim(col("customer_name")).alias("customer_name"),
        col("customer_segment")
      )

    val orders = ctx.readDataset("orders")
      .select(
        col("order_id"),
        col("customer_id"),
        lower(trim(col("order_status"))).alias("order_status"),
        col("order_total"),
        col("created_at")
      )
      .dedup("order_id")

    val customerOrders = orders
      .join(customers, Seq("customer_id"), "left")

    ctx.writeDataset(customerOrders, "customer_orders")
  }
}
```

Example YAML:

```yaml
datasets:
  - name: customers
    type: input
    path: s3://lakehouse/raw/customers
    format: parquet
  - name: orders
    type: input
    path: s3://lakehouse/raw/orders
    format: parquet
  - name: customer_orders
    type: output
    path: s3://lakehouse/silver/customer_orders
    format: delta
    mode: overwrite
sparkConfig:
  spark.sql.shuffle.partitions: "200"
  spark.sql.session.timeZone: UTC
```

In this example, `sparkConfig` is applied automatically to the Spark session before `configureSpark(...)` runs.
The `datasets` list is also discovered automatically and made available through `ctx.readDataset(...)` and `ctx.writeDataset(...)`.

## Runtime Sequence

When `main(args)` runs, SparkPlusPlus:

1. parses CLI args and requires `--config`
2. loads YAML into your config type
3. validates config
4. creates a logger
5. builds the `SparkSession`
6. calls `run(ctx)`
7. stops Spark in a `finally` block
