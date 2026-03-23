---
title: End-to-End Customer Orders
sidebar_position: 2
---

This is the simplest complete SparkPlusPlus use case. It shows how a developer can move from YAML config to a curated Delta output using the current `datasets` model.

## Goal

- read `customers` and `orders` as input datasets
- join them into `customer_orders`
- write `customer_orders` in Delta format
- keep Spark session settings in `sparkConfig`

## Why This Page Exists

Use this page when you want the shortest realistic example of how SparkPlusPlus is supposed to feel in day-to-day development:

- datasets declared once in YAML
- app code focused on business logic
- output behavior configured without changing Scala code

## YAML

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
    partitionBy:
      - order_date

sparkConfig:
  spark.sql.shuffle.partitions: "200"
  spark.sql.session.timeZone: UTC
  spark.databricks.delta.schema.autoMerge.enabled: "true"
```

## App

```scala
import io.github.sparkplusplus._
import io.github.sparkplusplus.app.{AppContext, SparkApp}
import io.github.sparkplusplus.io.DatasetConfig
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{col, lower, to_date, trim}

final case class CustomerOrdersConfig(
  datasets: Seq[DatasetConfig],
  sparkConfig: Map[String, String] = Map.empty
) extends SparkApp.HasDatasets with SparkApp.HasSparkConfig

object CustomerOrdersApp extends SparkApp[CustomerOrdersConfig] {

  override protected def appName: String = "customer-orders-app"

  override protected def configClass: Class[CustomerOrdersConfig] =
    classOf[CustomerOrdersConfig]

  override protected def configureSpark(
    builder: SparkSession.Builder,
    config: CustomerOrdersConfig
  ): SparkSession.Builder = {
    builder.config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
  }

  override protected def run(ctx: AppContext[CustomerOrdersConfig]): Unit = {
    val customers = ctx.readDataset("customers")
      .select(
        col("customer_id"),
        trim(col("customer_name")).alias("customer_name"),
        trim(col("customer_segment")).alias("customer_segment")
      )

    val orders = ctx.readDataset("orders")
      .select(
        col("order_id"),
        col("customer_id"),
        lower(trim(col("order_status"))).alias("order_status"),
        col("order_total"),
        to_date(col("created_at")).alias("order_date")
      )
      .dedup("order_id")

    val customerOrders = orders
      .join(customers, Seq("customer_id"), "left")
      .select(
        col("order_id"),
        col("customer_id"),
        col("customer_name"),
        col("customer_segment"),
        col("order_status"),
        col("order_total"),
        col("order_date")
      )

    ctx.writeDataset(customerOrders, "customer_orders")
  }
}
```

## Run It

```bash
spark-submit \
  --class example.CustomerOrdersApp \
  your-app.jar \
  --config conf/customer-orders.yaml
```

## End-to-End Flow

1. SparkPlusPlus loads the YAML config.
2. `datasets` are validated before Spark starts.
3. `sparkConfig` entries are applied to the Spark session.
4. `ctx.readDataset("customers")` and `ctx.readDataset("orders")` load both inputs.
5. The app joins and curates the records.
6. `ctx.writeDataset(customerOrders, "customer_orders")` writes the final Delta dataset.

## What the Developer Gets

- input and output paths live in YAML, not in code
- output behavior such as `mode` and `partitionBy` is also in YAML
- the app code only focuses on Spark transforms
- `ctx.readDataset(...)` and `ctx.writeDataset(...)` keep IO usage simple
- the same app can move between environments by changing only YAML

## Result

The output dataset is written to:

```text
s3://lakehouse/silver/customer_orders
```

Typical columns in the final Delta table:

- `order_id`
- `customer_id`
- `customer_name`
- `customer_segment`
- `order_status`
- `order_total`
- `order_date`

This is the recommended starting point before moving to the more advanced customer-orders variant.
