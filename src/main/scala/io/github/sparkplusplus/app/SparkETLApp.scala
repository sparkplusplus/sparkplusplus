package io.github.sparkplusplus.app

import org.apache.spark.sql.DataFrame

abstract class SparkETLApp[C <: AnyRef] extends SparkApp[C] {

  protected def extract(ctx: AppContext[C]): Map[String, DataFrame] =
    ctx.inputs.keys.toSeq.sorted.map(name => name -> ctx.readInput(name)).toMap

  protected def transform(ctx: AppContext[C], inputs: Map[String, DataFrame]): Map[String, DataFrame]

  protected def load(ctx: AppContext[C], outputs: Map[String, DataFrame]): Unit = {
    val expectedOutputNames = ctx.outputs.keySet
    val actualOutputNames = outputs.keySet
    val missingOutputs = expectedOutputNames.diff(actualOutputNames).toSeq.sorted
    val unexpectedOutputs = actualOutputNames.diff(expectedOutputNames).toSeq.sorted

    require(unexpectedOutputs.isEmpty, s"transform() returned outputs that are not configured: ${unexpectedOutputs.mkString(", ")}")
    require(missingOutputs.isEmpty, s"transform() did not produce configured outputs: ${missingOutputs.mkString(", ")}")

    outputs.toSeq.sortBy(_._1).foreach { case (name, df) =>
      ctx.writeOutput(df, name)
    }
  }

  final override protected def run(ctx: AppContext[C]): Unit = {
    val extracted = extract(ctx)
    val transformed = transform(ctx, extracted)
    load(ctx, transformed)
  }
}
