# Customer Orders Sample

This sample is a standalone Scala + Maven SparkPlusPlus project that implements the `customer_orders` use case with local file inputs, schema-controlled reads, and curated output columns.

## What It Includes

- `CustomerOrdersApp` built on `FrameworkSparkETLApp`
- versioned YAML (`apiVersion: sparkplusplus.io/v1`) with typed application settings, `inputs`, `outputs`, and `sparkConfig`
- JSON and CSV fixture inputs for local runs
- schema files for deterministic parsing
- a Spark-backed end-to-end test

## Prerequisites

Install the root SparkPlusPlus library snapshot into your local Maven repository:

```bash
cd ../..
mvn install -DskipTests
```

Then build or run the sample from this directory:

```bash
mvn test
mvn package
```

## Run Locally

The checked-in config uses local Spark and writes Parquet output to `target/customer-orders-output`.

```bash
mvn exec:java -Dexec.args="--config conf/customer-orders-local.yaml"
```

The same app can be submitted with Spark if you prefer:

```bash
spark-submit \
  --class io.github.sparkplusplus.samples.customerorders.CustomerOrdersApp \
  target/customer-orders-0.0.1-SNAPSHOT.jar \
  --config conf/customer-orders-local.yaml
```

## Layout

- `conf/customer-orders-local.yaml`: runnable local config
- `schemas/`: input schemas used by the config
- `src/main/scala/`: sample application code
- `src/test/resources/input/`: local input fixtures for the sample

## Delta Output

The sample defaults to Parquet output so it runs locally without additional Delta Lake packages. To target Delta in your runtime, switch the output format in config and provide the appropriate Delta dependencies and Spark session extensions for your platform.
