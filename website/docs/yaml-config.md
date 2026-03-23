---
sidebar_position: 5
title: YAML Config
---

SparkPlusPlus expects application config to come from a YAML file passed with `--config`.

## CLI Pattern

```bash
spark-submit \
  --class example.OrdersJob \
  your-app.jar \
  --config conf/orders.yaml \
  --env dev
```

Arguments that are not consumed by SparkPlusPlus remain available through `ctx.args`.

## Example Config

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

## Dataset Rules

Each dataset entry must define:

- `name`
- `type`
- `path`
- `format`

Supported `type` values:

- `input`
- `output`

Input datasets may additionally define:

- `schemaPath`
- `schemaJson`
- `options`

Output datasets may additionally define:

- `mode`
- `partitionBy`
- `options`

SparkPlusPlus validates these combinations before the application starts.

## Applying Spark Session Config

If your config case class exposes a `sparkConfig: Map[String, String]` field, SparkPlusPlus will automatically apply those entries to the `SparkSession.Builder` before your app-specific `configureSpark(...)` hook runs.

This is useful for settings such as:

- shuffle partitions
- timezone
- Delta-related Spark settings
- serializer and SQL behavior flags

## Supported Shapes

The loader currently supports:

- case classes
- nested case classes
- lists and sequences
- `Map[String, T]`
- `Option[T]`
- default constructor values

## Validation Behavior

- unknown YAML fields fail fast
- missing required fields fail fast
- optional fields map to `None` when omitted
- default parameter values are used when defined in the case class
- `sparkConfig` maps can be supplied in YAML and are added to the Spark session automatically
- duplicate dataset names fail fast
- invalid dataset field combinations fail fast
