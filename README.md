# SparkPlusPlus

SparkPlusPlus is a Scala framework for building Apache Spark applications with less boilerplate and a more consistent runtime model.

The first framework layer is `SparkApp`, an abstract base class that standardizes:

- YAML-based application config
- `SparkSession` creation
- application logging
- argument parsing
- consistent shutdown behavior
- config-driven IO helpers

On top of that, `SparkETLApp` gives you a faster ETL-oriented path with `extract()`, `transform()`, and `load()`.

The existing `DataFrameUtils` helpers remain available as a utility layer inside your apps.
`SchemaUtils` adds schema derivation, loading, and recursive schema inspection helpers inspired by the reference `spark-utils` project.

## Features

- `SparkApp[C]` for custom batch Spark jobs with typed config
- `SparkETLApp[C]` for framework-managed ETL jobs
- `AppContext[C]` to provide `SparkSession`, config, logger, and passthrough args
- versioned YAML config loading with strict unknown-field validation
- `DataFrame` helper methods via `DataFrameUtils` and implicit extensions
- `SchemaUtils` helpers for deriving, loading, and validating Spark schemas
- separate `inputs` and `outputs` config for file-based IO definitions

## Installation

```xml
<dependency>
  <groupId>io.github.sparkplusplus</groupId>
  <artifactId>sparkplusplus-core_2.12</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>io.github.sparkplusplus</groupId>
  <artifactId>sparkplusplus-config_2.12</artifactId>
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

## Recommended Framework Configuration

New applications should use the strict `sparkplusplus.io/v1` envelope. It
keeps application metadata, Spark settings, and dataset contracts in a stable,
versioned file while decoding the `settings` section into your case class.

```yaml
apiVersion: sparkplusplus.io/v1
application:
  name: orders-job
  version: 1.0.0
  settings:
    sourceSystem: orders
sparkConfig:
  spark.sql.session.timeZone: UTC
inputs: []
outputs: []
```

Extend `FrameworkSparkETLApp[OrdersSettings]` and implement
`transform(ctx, settings, inputs)`. The framework emits a JSON run record with
a run ID, configuration fingerprint, datasets, duration, Spark version, and
failure category. Existing `SparkApp` and `SparkETLApp` configurations remain
supported, but are not automatically converted to v1; migrate them explicitly
and validate the resulting file before deployment.

## CLI and Test Support

Build the CLI with `mvn -pl sparkplusplus-cli -am package`, then run the
assembled JAR with `java -jar ...-jar-with-dependencies.jar`. It supports:

```bash
java -jar sparkplusplus-cli/target/sparkplusplus-cli_2.12-0.0.1-SNAPSHOT-jar-with-dependencies.jar init orders-job --package example.orders --name orders-job
java -jar sparkplusplus-cli/target/sparkplusplus-cli_2.12-0.0.1-SNAPSHOT-jar-with-dependencies.jar validate orders-job/conf/application.yaml
```

Use `sparkplusplus-testkit` for `LocalSparkSession.withSession` and generated
project/config assertions in application tests.

## Legacy SparkETLApp Example

```scala
package example

import io.github.sparkplusplus._
import io.github.sparkplusplus.app.{AppContext, SparkApp, SparkETLApp}
import io.github.sparkplusplus.io.{InputDatasetConfig, OutputDatasetConfig}
import org.apache.spark.sql.SparkSession

final case class OrdersConfig(
  inputs: Seq[InputDatasetConfig],
  outputs: Seq[OutputDatasetConfig],
  sparkConfig: Map[String, String] = Map.empty
) extends SparkApp.WithInputDatasets with SparkApp.WithOutputDatasets with SparkApp.HasSparkConfig

object OrdersJob extends SparkETLApp[OrdersConfig] {

  override protected def appName: String = "orders-job"

  override protected def configClass: Class[OrdersConfig] = classOf[OrdersConfig]

  override protected def configureSpark(
    builder: SparkSession.Builder,
    config: OrdersConfig
  ): SparkSession.Builder = {
    builder.config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
  }

  override protected def transform(
    ctx: AppContext[OrdersConfig],
    inputs: Map[String, org.apache.spark.sql.DataFrame]
  ): Map[String, org.apache.spark.sql.DataFrame] = {
    val customers = inputs("customers")
    val orders = inputs("orders").dedup("order_id")

    val customerOrders = orders.join(customers, Seq("customer_id"), "left")

    Map("customer_orders" -> customerOrders)
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
inputs:
  - name: customers
    path: s3://bucket/customers/input
    format: parquet
    filter: is_active = true
  - name: orders
    path: s3://bucket/orders/input
    format: parquet
outputs:
  - name: customer_orders
    path: s3://bucket/orders/output
    format: delta
    mode: overwrite
    partitionBy:
      - order_date
    repartition: 200
sparkConfig:
  spark.sql.shuffle.partitions: "200"
  spark.sql.session.timeZone: UTC
```

Inside `transform`, passthrough args after `--config` are available through `ctx.args`.

## Dataset IO

SparkPlusPlus keeps file IO config in two simple sections:

- `inputs` for read-only datasets
- `outputs` for write targets

At runtime:

```scala
val customers = ctx.readInput("customers")
val orders = ctx.readInput("orders")

val customerOrders = orders.join(customers, Seq("customer_id"))

ctx.writeOutput(customerOrders, "customer_orders")
```

Validation rules:

- names must be unique within and across `inputs` and `outputs`
- input datasets can use `schemaPath`, `schemaJson`, `filter`, and reader `options`
- output datasets can use `mode`, `partitionBy`, `repartition`, `coalesce`, and writer `options`
- `repartition` and `coalesce` are mutually exclusive
- invalid field combinations fail before Spark starts

## SparkApp vs SparkETLApp

Use `SparkETLApp` when you want the framework to:

- read all configured inputs
- call your transformation logic
- write all configured outputs

Use `SparkApp` when you want full lifecycle control and prefer writing `run(ctx)` yourself.

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

## Runnable Sample

The repository now includes a standalone Maven sample at [samples/customer-orders](samples/customer-orders).

It implements the documented `customer_orders` use case as a full Scala project with:

- its own `pom.xml`
- YAML config and schema files
- local input fixtures
- a Spark-backed end-to-end test

Install the root library snapshot first, then build or run the sample from its directory:

```bash
mvn install -DskipTests
cd samples/customer-orders
mvn test
mvn exec:java -Dexec.args="--config conf/customer-orders-local.yaml"
```

## Documentation Site
The public documentation site source now lives in the separate `sparkplusplus.github.io` repository at `https://github.com/sparkplusplus`.
```

The intended public site domain is `https://sparkplusplus.github.io/`.

## License

Apache License 2.0
