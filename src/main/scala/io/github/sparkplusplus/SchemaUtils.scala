package io.github.sparkplusplus

import org.apache.spark.sql.catalyst.ScalaReflection
import org.apache.spark.sql.types.{ArrayType, DataType, MapType, StructField, StructType}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.reflect.runtime.universe.TypeTag
import scala.util.Try

object SchemaUtils {

  /**
   * Extract the Spark SQL schema for a Scala type.
   */
  def schemaFor[T: TypeTag]: StructType =
    ScalaReflection.schemaFor[T].dataType.asInstanceOf[StructType]

  /**
   * Parse a Spark StructType from a JSON string.
   */
  def loadSchemaFromString(json: String): Try[StructType] =
    Try(DataType.fromJson(json).asInstanceOf[StructType])

  /**
   * Load a Spark StructType from a JSON file on disk.
   */
  def loadSchemaFromFile(path: String): Try[StructType] =
    loadSchemaFromFile(Paths.get(path))

  def loadSchemaFromFile(path: Path): Try[StructType] =
    for {
      json <- Try(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))
      schema <- loadSchemaFromString(json)
    } yield schema

  /**
   * Transform fields inside a schema recursively.
   */
  def mapFields(dataType: DataType, mapField: StructField => StructField): DataType =
    dataType match {
      case StructType(children) =>
        StructType(children.map(mapField).map { field =>
          field.copy(dataType = mapFields(field.dataType, mapField))
        })
      case ArrayType(elementType, containsNull) =>
        ArrayType(mapFields(elementType, mapField), containsNull)
      case MapType(keyType, valueType, valueContainsNull) =>
        MapType(mapFields(keyType, mapField), mapFields(valueType, mapField), valueContainsNull)
      case other => other
    }

  /**
   * Check whether all fields in a schema satisfy a predicate.
   */
  def checkAllFields(dataType: DataType, predicate: StructField => Boolean): Boolean =
    checkFields(dataType, predicate, _ && _, initial = true)

  /**
   * Check whether any field in a schema satisfies a predicate.
   */
  def checkAnyFields(dataType: DataType, predicate: StructField => Boolean): Boolean =
    checkFields(dataType, predicate, _ || _, initial = false)

  private def checkFields(
    dataType: DataType,
    predicate: StructField => Boolean,
    reducer: (Boolean, Boolean) => Boolean,
    initial: Boolean
  ): Boolean =
    dataType match {
      case StructType(children) if children.nonEmpty =>
        val current = children.map(predicate).reduce(reducer)
        children.map(_.dataType).map(checkFields(_, predicate, reducer, current)).reduce(reducer)
      case StructType(_) =>
        initial
      case ArrayType(elementType, _) =>
        checkFields(elementType, predicate, reducer, initial)
      case MapType(keyType, valueType, _) =>
        checkFields(keyType, predicate, reducer, checkFields(valueType, predicate, reducer, initial))
      case _ =>
        initial
    }
}
