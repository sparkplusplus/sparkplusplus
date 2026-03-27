package io.github.sparkplusplus.samples.customerorders

import _root_.io.github.sparkplusplus._
import _root_.io.github.sparkplusplus.app.{AppContext, SparkApp, SparkETLApp}
import _root_.io.github.sparkplusplus.io.{InputDatasetConfig, OutputDatasetConfig}
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions.{col, lower, struct, to_date, trim}

final case class CustomerOrdersConfig(
  inputs: Seq[InputDatasetConfig],
  outputs: Seq[OutputDatasetConfig],
  sparkConfig: Map[String, String] = Map.empty
) extends SparkApp.WithInputDatasets
    with SparkApp.WithOutputDatasets
    with SparkApp.HasSparkConfig

object CustomerOrdersTransform {

  def build(customers: DataFrame, orders: DataFrame): DataFrame = {
    val normalizedCustomers = customers
      .select(
        col("customer_id"),
        trim(col("customer_name")).alias("customer_name"),
        trim(col("customer_segment")).alias("customer_segment")
      )

    val normalizedOrders = orders
      .select(
        col("order_id"),
        col("customer_id"),
        lower(trim(col("order_status"))).alias("order_status"),
        col("order_total"),
        to_date(col("created_at")).alias("order_date")
      )
      .dedup("order_id")

    normalizedOrders
      .join(normalizedCustomers, Seq("customer_id"), "left")
      .select(
        col("order_id"),
        struct(
          col("customer_id"),
          col("customer_name"),
          col("customer_segment")
        ).alias("customer"),
        struct(
          col("order_status"),
          col("order_total"),
          col("order_date")
        ).alias("order")
      )
      .flattenFields()
      .makeColumnNamesAvroCompliant()
  }
}

object CustomerOrdersApp extends SparkETLApp[CustomerOrdersConfig] {

  private val RequiredInputs = Set("customers", "orders")
  private val RequiredOutputs = Set("customer_orders")

  override protected def appName: String = "customer-orders-app"

  override protected def configClass: Class[CustomerOrdersConfig] = classOf[CustomerOrdersConfig]

  override protected def validateConfig(config: CustomerOrdersConfig): Unit = {
    val inputNames = config.inputs.map(_.name).toSet
    val outputNames = config.outputs.map(_.name).toSet

    require(
      inputNames == RequiredInputs,
      s"Expected input dataset names ${RequiredInputs.toSeq.sorted.mkString(", ")} but found ${inputNames.toSeq.sorted.mkString(", ")}"
    )
    require(
      outputNames == RequiredOutputs,
      s"Expected output dataset names ${RequiredOutputs.toSeq.sorted.mkString(", ")} but found ${outputNames.toSeq.sorted.mkString(", ")}"
    )
  }

  override protected def transform(
    ctx: AppContext[CustomerOrdersConfig],
    inputs: Map[String, DataFrame]
  ): Map[String, DataFrame] = {
    val customerOrders = CustomerOrdersTransform.build(
      customers = inputs("customers"),
      orders = inputs("orders")
    )

    Map("customer_orders" -> customerOrders)
  }
}
