package io.github.sparkplusplus.app

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

final case class RunRecord(
  runId: String,
  applicationName: String,
  applicationVersion: String,
  configFingerprint: String,
  inputNames: Seq[String],
  outputNames: Seq[String],
  startedAt: Instant,
  sparkVersion: Option[String] = None,
  completedAt: Option[Instant] = None,
  failureCategory: Option[String] = None
) {

  def completed(sparkVersion: Option[String], finishedAt: Instant = Instant.now()): RunRecord =
    copy(sparkVersion = sparkVersion, completedAt = Some(finishedAt))

  def failed(sparkVersion: Option[String], failure: Throwable, finishedAt: Instant = Instant.now()): RunRecord =
    copy(
      sparkVersion = sparkVersion,
      completedAt = Some(finishedAt),
      failureCategory = Some(RunRecord.failureCategory(failure))
    )

  def toJson: String = {
    def quoted(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    def names(values: Seq[String]): String = values.sorted.map(quoted).mkString("[", ",", "]")
    def optional(value: Option[String]): String = value.map(quoted).getOrElse("null")
    def optionalInstant(value: Option[Instant]): String = value.map(instant => quoted(instant.toString)).getOrElse("null")

    s"""{"runId":${quoted(runId)},"applicationName":${quoted(applicationName)},"applicationVersion":${quoted(applicationVersion)},"configFingerprint":${quoted(configFingerprint)},"inputNames":${names(inputNames)},"outputNames":${names(outputNames)},"startedAt":${quoted(startedAt.toString)},"sparkVersion":${optional(sparkVersion)},"completedAt":${optionalInstant(completedAt)},"failureCategory":${optional(failureCategory)}}"""
  }
}

object RunRecord {

  def started(
    applicationName: String,
    applicationVersion: String,
    configPath: String,
    inputNames: Seq[String],
    outputNames: Seq[String]
  ): RunRecord =
    RunRecord(
      runId = UUID.randomUUID().toString,
      applicationName = applicationName,
      applicationVersion = applicationVersion,
      configFingerprint = fingerprint(Path.of(configPath)),
      inputNames = inputNames.sorted,
      outputNames = outputNames.sorted,
      startedAt = Instant.now()
    )

  def fingerprint(path: Path): String = {
    if (!Files.isRegularFile(path)) {
      "unavailable"
    } else {
      val digest = MessageDigest.getInstance("SHA-256")
      val bytes = Files.readAllBytes(path)
      digest.digest(bytes).map(byte => f"$byte%02x").mkString
    }
  }

  def failureCategory(failure: Throwable): String = failure match {
    case _: IllegalArgumentException => "configuration"
    case _: java.io.IOException => "io"
    case _: org.apache.spark.SparkException => "spark"
    case _ => "application"
  }
}
