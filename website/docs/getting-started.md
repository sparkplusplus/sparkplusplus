---
sidebar_position: 2
title: Getting Started
---

SparkPlusPlus is designed for teams that want a light framework on top of Apache Spark without introducing a full orchestration layer.

## What You Get

- `SparkApp[C]` as the standard application entrypoint
- `AppContext[C]` carrying `SparkSession`, typed config, CLI passthrough args, and logger
- YAML config decoding into Scala case classes
- simple dataset-based IO through `ctx.readDataset(...)` and `ctx.writeDataset(...)`
- `DataFrameUtils` and implicit DataFrame extensions

## Typical Flow

1. Define a Scala case class for your app config.
2. Extend `SparkApp[YourConfig]`.
3. Point the app at a YAML file with `--config`.
4. Implement business logic in `run(ctx)`.

## First Real Use Case

A common SparkPlusPlus job looks like this:

- input datasets: `customers` and `orders`
- output table or files: `customer_orders`
- target format: Delta
- goal: join and curate datasets into a cleaner analytics table

If you want a fuller example, see [Use Case Examples](/use-cases/).

## Minimal Example

```scala
import io.github.sparkplusplus.app.{AppContext, SparkApp}
import io.github.sparkplusplus.io.DatasetConfig

final case class ExampleConfig(
  datasets: Seq[DatasetConfig]
) extends SparkApp.HasDatasets

object ExampleJob extends SparkApp[ExampleConfig] {
  override protected def appName: String = "example-job"

  override protected def configClass: Class[ExampleConfig] = classOf[ExampleConfig]

  override protected def run(ctx: AppContext[ExampleConfig]): Unit = {
    val df = ctx.readDataset("input_orders")
    ctx.writeDataset(df, "output_orders")
  }
}
```

Matching YAML:

```yaml
datasets:
  - name: input_orders
    type: input
    path: s3://bucket/raw/orders
    format: parquet
  - name: output_orders
    type: output
    path: s3://bucket/curated/orders
    format: parquet
    mode: overwrite
```
