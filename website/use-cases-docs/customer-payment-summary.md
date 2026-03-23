---
title: Customers and Payments to customer_payment_summary
---

This use case joins `customers` and `payments` to create a customer-level payment summary table.

## Goal

- input datasets: `customers` and `payments`
- output dataset: `customer_payment_summary`
- output format: Delta
- objective: aggregate payment activity by customer for reporting and downstream serving

## Example App

```scala
import io.github.sparkplusplus.app.{AppContext, SparkApp}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{count, max, sum}

final case class CustomerPaymentSummaryConfig(
  customersPath: String,
  paymentsPath: String,
  outputPath: String,
  sparkConfig: Map[String, String] = Map.empty
)

object CustomerPaymentSummaryApp extends SparkApp[CustomerPaymentSummaryConfig] {
  override protected def appName: String = "customer-payment-summary-app"

  override protected def configClass: Class[CustomerPaymentSummaryConfig] =
    classOf[CustomerPaymentSummaryConfig]

  override protected def configureSpark(
    builder: SparkSession.Builder,
    config: CustomerPaymentSummaryConfig
  ): SparkSession.Builder = {
    builder.config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
  }

  override protected def run(ctx: AppContext[CustomerPaymentSummaryConfig]): Unit = {
    val customers = ctx.spark.read.parquet(ctx.config.customersPath)
    val payments = ctx.spark.read.parquet(ctx.config.paymentsPath)

    val paymentSummary = payments
      .groupBy("customer_id")
      .agg(
        count("*").alias("payment_count"),
        sum("payment_amount").alias("total_payment_amount"),
        max("payment_date").alias("last_payment_date")
      )

    customers
      .join(paymentSummary, Seq("customer_id"), "left")
      .write
      .format("delta")
      .mode("overwrite")
      .save(ctx.config.outputPath)
  }
}
```

## Example YAML

```yaml
customersPath: s3://lakehouse/raw/customers
paymentsPath: s3://lakehouse/raw/payments
outputPath: s3://lakehouse/gold/customer_payment_summary
sparkConfig:
  spark.sql.shuffle.partitions: "100"
  spark.sql.session.timeZone: UTC
```
