---
title: Advanced customer_orders with Schema and Curated Output
---

This version of `customer_orders` builds on the basic end-to-end example and adds two common production needs:

- schema-controlled input loading
- cleaner curated output columns before the Delta write

## Goal

- input datasets: `customers` and `orders`
- output dataset: `customer_orders`
- output format: Delta
- objective: join customer master data with orders and publish a curated analytics-ready table
- extra requirement: keep input parsing deterministic and clean the output shape before publishing

## When to Use This Pattern

Use this version when:

- CSV or JSON inputs should not rely on schema inference
- raw source columns need cleanup before publishing
- the published table should follow a cleaner naming standard
- you want to flatten nested business structures before writing

## Example App

```scala
import io.github.sparkplusplus._
import io.github.sparkplusplus.app.{AppContext, SparkApp}
import io.github.sparkplusplus.io.DatasetConfig
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{col, lower, struct, to_date, trim}

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
        struct(
          col("customer_id"),
          col("customer_name"),
          col("customer_segment")
        ).alias("customer"),
        struct(
          col("order_status"),
          col("order_total"),
          col("order_date")
        ).alias("order")
      )
      .flattenFields()
      .makeColumnNamesAvroCompliant()

    ctx.writeDataset(customerOrders, "customer_orders")
  }
}
```

## Example YAML

```yaml
datasets:
  - name: customers
    type: input
    path: s3://lakehouse/raw/customers.json
    format: json
    schemaPath: schemas/customers.json
  - name: orders
    type: input
    path: s3://lakehouse/raw/orders.csv
    format: csv
    options:
      header: "true"
      delimiter: ","
    schemaPath: schemas/orders.json
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
```

## What Is Different from the Basic Example

- input schemas are fixed with `schemaPath`
- input formats can differ across datasets
- the output is reshaped through nested business structs
- `flattenFields()` converts nested structs into stable top-level columns
- `makeColumnNamesAvroCompliant()` normalizes final column names before writing

## Typical Final Output Columns

- `order_id`
- `customer_customer_id`
- `customer_customer_name`
- `customer_customer_segment`
- `order_order_status`
- `order_order_total`
- `order_order_date`

This version is useful when raw datasets are messy but you still want a small app with predictable IO behavior.
