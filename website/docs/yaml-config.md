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
customersPath: s3://lakehouse/raw/customers
ordersPath: s3://lakehouse/raw/orders
outputPath: s3://lakehouse/silver/customer_orders
partitions: 200
sparkConfig:
  spark.sql.shuffle.partitions: "200"
  spark.sql.session.timeZone: UTC
  spark.databricks.delta.schema.autoMerge.enabled: "true"
```

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
