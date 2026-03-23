---
sidebar_position: 2
title: Getting Started
---

SparkPlusPlus is designed for teams that want a light framework on top of Apache Spark without introducing a full orchestration layer.

## What You Get

- `SparkApp[C]` as the standard application entrypoint
- `AppContext[C]` carrying `SparkSession`, typed config, CLI passthrough args, and logger
- YAML config decoding into Scala case classes
- `DataFrameUtils` and implicit DataFrame extensions

## Typical Flow

1. Define a Scala case class for your app config.
2. Extend `SparkApp[YourConfig]`.
3. Point the app at a YAML file with `--config`.
4. Implement business logic in `run(ctx)`.

## Minimal Example

```scala
import io.github.sparkplusplus.app.{AppContext, SparkApp}

final case class ExampleConfig(input: String, output: String)

object ExampleJob extends SparkApp[ExampleConfig] {
  override protected def appName: String = "example-job"

  override protected def configClass: Class[ExampleConfig] = classOf[ExampleConfig]

  override protected def run(ctx: AppContext[ExampleConfig]): Unit = {
    val df = ctx.spark.read.parquet(ctx.config.input)
    df.write.mode("overwrite").parquet(ctx.config.output)
  }
}
```
