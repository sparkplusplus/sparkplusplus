package io.github.sparkplusplus.app

import org.yaml.snakeyaml.Yaml

import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import scala.reflect.runtime.universe._

object YamlConfigLoader {

  private val mirror = runtimeMirror(getClass.getClassLoader)
  private val yaml = new Yaml()

  def load[C](path: String, configClass: Class[C]): C = {
    load(Paths.get(path), configClass)
  }

  def load[C](path: Path, configClass: Class[C]): C = {
    require(configClass != null, "configClass must not be null")

    if (!Files.exists(path)) {
      throw new IllegalArgumentException(s"Config file does not exist: $path")
    }

    val reader = Files.newBufferedReader(path)

    val rootValue = try {
      Option(yaml.load[Any](reader)).getOrElse {
        throw new IllegalArgumentException(s"Config file is empty: $path")
      }
    } finally {
      reader.close()
    }

    decodeRoot(normalize(rootValue), configClass)
  }

  private def decodeRoot[C](value: Any, configClass: Class[C]): C = {
    val classSymbol = mirror.classSymbol(configClass)
    val rootType = classSymbol.toType

    decodeValue(value, rootType, "config").asInstanceOf[C]
  }

  private def normalize(value: Any): Any = value match {
    case map: java.util.Map[_, _] =>
      map.asScala.iterator.map { case (key, entryValue) =>
        key.toString -> normalize(entryValue)
      }.toMap
    case list: java.util.List[_] =>
      list.asScala.iterator.map(normalize).toList
    case other => other
  }

  private def decodeValue(value: Any, expectedType: Type, location: String): Any = {
    if (expectedType =:= typeOf[String]) {
      value match {
        case string: String => string
        case other => other.toString
      }
    } else if (expectedType =:= typeOf[Int]) {
      asNumber(value, location).intValue()
    } else if (expectedType =:= typeOf[Long]) {
      asNumber(value, location).longValue()
    } else if (expectedType =:= typeOf[Double]) {
      asNumber(value, location).doubleValue()
    } else if (expectedType =:= typeOf[Float]) {
      asNumber(value, location).floatValue()
    } else if (expectedType =:= typeOf[Boolean]) {
      value match {
        case bool: java.lang.Boolean => bool.booleanValue()
        case bool: Boolean => bool
        case string: String => string.toBoolean
        case _ => throw new IllegalArgumentException(s"Expected boolean at $location but found ${describeValue(value)}")
      }
    } else if (expectedType <:< typeOf[Option[_]]) {
      val innerType = expectedType.typeArgs.head
      if (value == null) {
        None
      } else {
        Some(decodeValue(value, innerType, location))
      }
    } else if (expectedType <:< typeOf[Seq[_]]) {
      val innerType = expectedType.typeArgs.head
      val decoded = value match {
        case sequence: Seq[_] =>
          sequence.map(item => decodeValue(item, innerType, location))
        case _ =>
          throw new IllegalArgumentException(s"Expected sequence at $location but found ${describeValue(value)}")
      }

      if (expectedType <:< typeOf[List[_]]) {
        decoded.toList
      } else if (expectedType <:< typeOf[Vector[_]]) {
        decoded.toVector
      } else {
        decoded
      }
    } else if (expectedType <:< typeOf[Map[_, _]]) {
      val keyType = expectedType.typeArgs.head
      val valueType = expectedType.typeArgs(1)

      if (!(keyType =:= typeOf[String])) {
        throw new IllegalArgumentException(s"Only Map[String, _] is supported at $location")
      }

      value match {
        case map: Map[_, _] =>
          map.iterator.map { case (key, entryValue) =>
            key.toString -> decodeValue(entryValue, valueType, s"$location.${key.toString}")
          }.toMap
        case _ =>
          throw new IllegalArgumentException(s"Expected object at $location but found ${describeValue(value)}")
      }
    } else if (isCaseClass(expectedType)) {
      value match {
        case map: Map[_, _] =>
          constructCaseClass(map.asInstanceOf[Map[String, Any]], expectedType, location)
        case _ =>
          throw new IllegalArgumentException(s"Expected object at $location but found ${describeValue(value)}")
      }
    } else {
      throw new IllegalArgumentException(s"Unsupported config type at $location: $expectedType")
    }
  }

  private def constructCaseClass(data: Map[String, Any], expectedType: Type, location: String): Any = {
    val classSymbol = expectedType.typeSymbol.asClass
    val constructor = expectedType.decl(termNames.CONSTRUCTOR).alternatives.collectFirst {
      case method: MethodSymbol if method.isPrimaryConstructor => method
    }.getOrElse(throw new IllegalArgumentException(s"No primary constructor found for $expectedType"))

    val params = constructor.paramLists.flatten
    val paramNames = params.map(_.name.decodedName.toString)
    val unknownFields = data.keySet.diff(paramNames.toSet)

    if (unknownFields.nonEmpty) {
      throw new IllegalArgumentException(
        s"Unknown config fields at $location: ${unknownFields.toSeq.sorted.mkString(", ")}"
      )
    }

    val companion = classSymbol.companion.asModule
    val companionInstance = mirror.reflectModule(companion).instance
    val companionMirror = mirror.reflect(companionInstance)
    val applyMethod = companion.typeSignature.member(TermName("apply")).alternatives.collectFirst {
      case method: MethodSymbol if method.paramLists.flatten.size == params.size => method
    }.getOrElse(throw new IllegalArgumentException(s"No case class apply method found for $expectedType"))

    val args = params.zipWithIndex.map { case (param, index) =>
      val name = param.name.decodedName.toString
      val paramType = param.typeSignatureIn(expectedType)

      data.get(name) match {
        case Some(rawValue) =>
          decodeValue(rawValue, paramType, s"$location.$name")
        case None =>
          if (paramType <:< typeOf[Option[_]]) {
            None
          } else {
            defaultValue(companionMirror, index + 1).getOrElse {
              throw new IllegalArgumentException(s"Missing required config field: $location.$name")
            }
          }
      }
    }

    companionMirror.reflectMethod(applyMethod)(args: _*)
  }

  private def defaultValue(companionMirror: InstanceMirror, index: Int): Option[Any] = {
    val defaultMethod = companionMirror.symbol.typeSignature.member(TermName(s"apply$$default$$$index"))

    if (defaultMethod == NoSymbol) {
      None
    } else {
      Some(companionMirror.reflectMethod(defaultMethod.asMethod)())
    }
  }

  private def asNumber(value: Any, location: String): java.lang.Number = value match {
    case number: java.lang.Number => number
    case string: String =>
      try {
        java.lang.Double.valueOf(string)
      } catch {
        case _: NumberFormatException =>
          throw new IllegalArgumentException(s"Expected number at $location but found '$string'")
      }
    case _ =>
      throw new IllegalArgumentException(s"Expected number at $location but found ${describeValue(value)}")
  }

  private def isCaseClass(expectedType: Type): Boolean = {
    expectedType.typeSymbol.isClass && expectedType.typeSymbol.asClass.isCaseClass
  }

  private def describeValue(value: Any): String = {
    if (value == null) {
      "null"
    } else {
      value.getClass.getSimpleName
    }
  }
}
