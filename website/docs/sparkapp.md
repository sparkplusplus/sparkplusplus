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

```scala
import io.github.sparkplusplus._
import io.github.sparkplusplus.app.{AppContext, SparkApp}
import org.apache.spark.sql.SparkSession

final case class OrdersConfig(input: String, output: String, partitions: Int)

object OrdersJob extends SparkApp[OrdersConfig] {
  override protected def appName: String = "orders-job"

  override protected def configClass: Class[OrdersConfig] = classOf[OrdersConfig]

  override protected def validateConfig(config: OrdersConfig): Unit = {
    require(config.partitions > 0, "partitions must be positive")
  }

  override protected def configureSpark(
    builder: SparkSession.Builder,
    config: OrdersConfig
  ): SparkSession.Builder = {
    builder.config("spark.sql.shuffle.partitions", config.partitions.toString)
  }

  override protected def run(ctx: AppContext[OrdersConfig]): Unit = {
    val cleaned = ctx.spark.read.parquet(ctx.config.input).dedup("order_id")
    cleaned.write.mode("overwrite").parquet(ctx.config.output)
  }
}
```

## Runtime Sequence

When `main(args)` runs, SparkPlusPlus:

1. parses CLI args and requires `--config`
2. loads YAML into your config type
3. validates config
4. creates a logger
5. builds the `SparkSession`
6. calls `run(ctx)`
7. stops Spark in a `finally` block
