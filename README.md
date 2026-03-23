# SparkPlusPlus

SparkPlusPlus is a Scala framework for building Apache Spark applications with less boilerplate and a more consistent runtime model.

The first framework layer is `SparkApp`, an abstract base class that standardizes:

- YAML-based application config
- `SparkSession` creation
- application logging
- argument parsing
- consistent shutdown behavior
- simple dataset-driven IO helpers

The existing `DataFrameUtils` helpers remain available as a utility layer inside your apps.
`SchemaUtils` adds schema derivation, loading, and recursive schema inspection helpers inspired by the reference `spark-utils` project.

## Features

- `SparkApp[C]` for batch Spark jobs with typed config
- `AppContext[C]` to provide `SparkSession`, config, logger, and passthrough args
- YAML config loading with strict unknown-field validation
- `DataFrame` helper methods via `DataFrameUtils` and implicit extensions
- `SchemaUtils` helpers for deriving, loading, and validating Spark schemas
- simple `datasets` config for input/output definitions

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

import io.github.sparkplusplus.io.DatasetConfig

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
    builder.config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
  }

  override protected def run(ctx: AppContext[OrdersConfig]): Unit = {
    val customers = ctx.readDataset("customers")
    val orders = ctx.readDataset("orders").dedup("order_id")

    val customerOrders = orders.join(customers, Seq("customer_id"), "left")

    ctx.writeDataset(customerOrders, "customer_orders")
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
datasets:
  - name: customers
    type: input
    path: s3://bucket/customers/input
    format: parquet
  - name: orders
    type: input
    path: s3://bucket/orders/input
    format: parquet
  - name: customer_orders
    type: output
    path: s3://bucket/orders/output
    format: delta
    mode: overwrite
    partitionBy:
      - order_date
sparkConfig:
  spark.sql.shuffle.partitions: "200"
  spark.sql.session.timeZone: UTC
```

Inside `run`, passthrough args after `--config` are available through `ctx.args`.

## Dataset IO

SparkPlusPlus can keep IO config in one simple `datasets` list. Each dataset entry declares a `type`:

- `input` for read-only datasets
- `output` for write targets

At runtime:

```scala
val customers = ctx.readDataset("customers")
val orders = ctx.readDataset("orders")

val customerOrders = orders.join(customers, Seq("customer_id"))

ctx.writeDataset(customerOrders, "customer_orders")
```

Validation rules:

- dataset names must be unique
- `input` datasets can use `schemaPath` or `schemaJson`
- `output` datasets can use `mode` and `partitionBy`
- invalid field combinations fail before Spark starts

## DataFrame Utilities

SparkPlusPlus still includes utility methods for common `DataFrame` operations:

- `dedup`
- `addRowNumber`
- `countNulls`
- `getBasicStats`
- `renameColumns`
- `flattenFields`
- `makeColumnNamesAvroCompliant`

Example:

```scala
import io.github.sparkplusplus._

val deduped = df.dedup("order_id")
val numbered = df.addRowNumber("row_id", "created_at")
val flattened = df.flattenFields()
val avroSafe = df.makeColumnNamesAvroCompliant()
```

Flattening nested structs is useful when preparing joined business datasets for curated outputs:

```scala
import org.apache.spark.sql.functions.struct

val customerOrders = orders
  .join(customers, Seq("customer_id"))
  .select(
    $"order_id",
    struct(
      $"customer_id",
      $"customer_name",
      $"customer_tier"
    ).alias("customer"),
    struct(
      $"order_total",
      $"order_status"
    ).alias("order")
  )

val curated = customerOrders
  .flattenFields()
  .makeColumnNamesAvroCompliant()

curated.write.format("delta").mode("overwrite").save(outputPath)
```

## Schema Utilities

`SchemaUtils` provides a light schema toolkit on top of Spark SQL:

- derive `StructType` from Scala case classes
- load schema JSON from a string or file
- recursively transform nested fields
- validate nested schemas with `checkAllFields` and `checkAnyFields`

Example:

```scala
import io.github.sparkplusplus.SchemaUtils
import org.apache.spark.sql.types.StructType

final case class CustomerRecord(customer_id: String, customer_name: String)

val schema: StructType = SchemaUtils.schemaFor[CustomerRecord]
val schemaFromFile = SchemaUtils.loadSchemaFromFile("schemas/customer_orders.json").get

val renamedSchema = SchemaUtils.mapFields(schemaFromFile, field =>
  field.copy(name = field.name.replace(' ', '_'))
)

val hasNullableIds = SchemaUtils.checkAnyFields(renamedSchema, field =>
  field.name == "customer_id" && field.nullable
)
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
