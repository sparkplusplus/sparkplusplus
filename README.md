# SparkPlusPlus

SparkPlusPlus is a Scala framework for building Apache Spark applications with less boilerplate and a more consistent runtime model.

The first framework layer is `SparkApp`, an abstract base class that standardizes:

- YAML-based application config
- `SparkSession` creation
- application logging
- argument parsing
- consistent shutdown behavior

The existing `DataFrameUtils` helpers remain available as a utility layer inside your apps.

## Features

- `SparkApp[C]` for batch Spark jobs with typed config
- `AppContext[C]` to provide `SparkSession`, config, logger, and passthrough args
- YAML config loading with strict unknown-field validation
- `DataFrame` helper methods via `DataFrameUtils` and implicit extensions

## Installation

```xml
<dependency>
  <groupId>io.github.sparkplusplus</groupId>
  <artifactId>sparkplusplus_2.12</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Spark should usually be provided by your runtime environment:

```xml
<dependency>
  <groupId>org.apache.spark</groupId>
  <artifactId>spark-sql_2.12</artifactId>
  <version>3.4.1</version>
  <scope>provided</scope>
</dependency>
```

## SparkApp Example

```scala
package example

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
    val df = ctx.spark.read.parquet(ctx.config.input)
    val cleaned = df.dedup("order_id")
    cleaned.write.mode("overwrite").parquet(ctx.config.output)
  }
}
```

Run the app with:

```bash
spark-submit \
  --class example.OrdersJob \
  your-app.jar \
  --config conf/orders.yaml \
  --env dev
```

Example YAML config:

```yaml
input: s3://bucket/orders/input
output: s3://bucket/orders/output
partitions: 200
```

Inside `run`, passthrough args after `--config` are available through `ctx.args`.

## DataFrame Utilities

SparkPlusPlus still includes utility methods for common `DataFrame` operations:

- `dedup`
- `addRowNumber`
- `countNulls`
- `getBasicStats`
- `renameColumns`

Example:

```scala
import io.github.sparkplusplus._

val deduped = df.dedup("order_id")
val numbered = df.addRowNumber("row_id", "created_at")
```

## Build

```bash
mvn test
mvn package
```

For Scala 2.13:

```bash
mvn test -Pscala-2.13
mvn package -Pscala-2.13
```

## Testing Notes

The repository contains both:

- pure unit tests for framework behavior
- Spark-backed integration tests for `DataFrameUtils`

In restricted environments where a local Spark runtime cannot bind ports, the Spark-backed tests are excluded by default from `mvn test`.

## Documentation Site

This repository also contains the public documentation site source under `website/`.

To work on the docs locally:

```bash
cd website
npm install
npm run dev
```

The docs dev server supports hot reload, so page and markdown changes should refresh automatically.

If filesystem watching is unreliable in your environment, use:

```bash
cd website
npm run dev:poll
```

To create a production build:

```bash
cd website
npm run build
```

The intended public site domain is `https://sparkplusplus.github.io/`.

## License

Apache License 2.0
