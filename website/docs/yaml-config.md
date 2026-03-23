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
input: s3://bucket/orders/input
output: s3://bucket/orders/output
partitions: 200
```

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
