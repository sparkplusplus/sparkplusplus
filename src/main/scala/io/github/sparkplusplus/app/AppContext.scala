package io.github.sparkplusplus.app

import org.apache.spark.sql.SparkSession
import org.slf4j.Logger

final case class AppContext[C](spark: SparkSession, config: C, args: Seq[String], logger: Logger)
