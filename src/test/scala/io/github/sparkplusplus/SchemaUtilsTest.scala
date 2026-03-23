package io.github.sparkplusplus

import org.apache.spark.sql.types.{ArrayType, IntegerType, MapType, StringType, StructField, StructType}
import org.scalatest.funsuite.AnyFunSuite

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class SchemaUtilsTest extends AnyFunSuite {

  test("schemaFor should derive a struct type from a case class") {
    val schema = SchemaUtils.schemaFor[Customer]

    assert(schema.fieldNames.sameElements(Array("id", "name")))
    assert(schema("id").dataType == IntegerType)
    assert(schema("name").dataType == StringType)
  }

  test("loadSchemaFromString should parse a valid schema json") {
    val json =
      """{
        |  "type":"struct",
        |  "fields":[
        |    {"name":"id","type":"integer","nullable":false,"metadata":{}},
        |    {"name":"name","type":"string","nullable":true,"metadata":{}}
        |  ]
        |}""".stripMargin

    val schema = SchemaUtils.loadSchemaFromString(json).get

    assert(
      schema == StructType(
        Seq(
          StructField("id", IntegerType, nullable = false),
          StructField("name", StringType, nullable = true)
        )
      )
    )
  }

  test("loadSchemaFromFile should parse a valid schema file") {
    val json =
      """{
        |  "type":"struct",
        |  "fields":[
        |    {"name":"id","type":"integer","nullable":false,"metadata":{}}
        |  ]
        |}""".stripMargin

    val path = Files.createTempFile("sparkplusplus-schema-", ".json")
    Files.write(path, json.getBytes(StandardCharsets.UTF_8))

    val schema = SchemaUtils.loadSchemaFromFile(path).get

    assert(schema == StructType(Seq(StructField("id", IntegerType, nullable = false))))
  }

  test("mapFields should transform nested field names recursively") {
    val schema = StructType(
      Seq(
        StructField(
          "customer",
          StructType(
            Seq(
              StructField("customer name", StringType, nullable = true),
              StructField("order count", IntegerType, nullable = false)
            )
          ),
          nullable = true
        ),
        StructField(
          "items",
          ArrayType(
            StructType(
              Seq(
                StructField("item id", StringType, nullable = false)
              )
            ),
            containsNull = false
          ),
          nullable = false
        ),
        StructField(
          "attributes",
          MapType(
            StringType,
            StructType(
              Seq(
                StructField("attr value", StringType, nullable = true)
              )
            ),
            valueContainsNull = true
          ),
          nullable = true
        )
      )
    )

    val mapped = SchemaUtils.mapFields(schema, field => field.copy(name = field.name.replace(' ', '_')))

    assert(mapped.isInstanceOf[StructType])
    assert(mapped.asInstanceOf[StructType].fieldNames.sameElements(Array("customer", "items", "attributes")))

    val customerFields = mapped.asInstanceOf[StructType]("customer").dataType.asInstanceOf[StructType]
    assert(customerFields.fieldNames.sameElements(Array("customer_name", "order_count")))

    val itemFields = mapped.asInstanceOf[StructType]("items").dataType.asInstanceOf[ArrayType].elementType.asInstanceOf[StructType]
    assert(itemFields.fieldNames.sameElements(Array("item_id")))

    val attributeFields = mapped.asInstanceOf[StructType]("attributes").dataType.asInstanceOf[MapType].valueType.asInstanceOf[StructType]
    assert(attributeFields.fieldNames.sameElements(Array("attr_value")))
  }

  test("checkAllFields should validate every field recursively") {
    val schema = StructType(
      Seq(
        StructField(
          "customer",
          StructType(
            Seq(
              StructField("customer_id", StringType, nullable = false),
              StructField("order_count", IntegerType, nullable = false)
            )
          ),
          nullable = false
        ),
        StructField(
          "items",
          ArrayType(
            StructType(
              Seq(
                StructField("item_id", StringType, nullable = false)
              )
            ),
            containsNull = false
          ),
          nullable = false
        )
      )
    )

    assert(SchemaUtils.checkAllFields(schema, field => !field.name.contains(" ")))
    assert(!SchemaUtils.checkAllFields(schema, field => field.dataType == StringType))
  }

  test("checkAnyFields should find matching nested fields recursively") {
    val schema = StructType(
      Seq(
        StructField(
          "customer",
          StructType(
            Seq(
              StructField("customer_id", StringType, nullable = false),
              StructField("order_count", IntegerType, nullable = false)
            )
          ),
          nullable = false
        )
      )
    )

    assert(SchemaUtils.checkAnyFields(schema, field => field.name == "order_count"))
    assert(!SchemaUtils.checkAnyFields(schema, field => field.name == "missing_field"))
  }
}

final case class Customer(id: Int, name: String)
