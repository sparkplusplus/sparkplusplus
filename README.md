# Spark Utils Library

A utility library for Apache Spark with DataFrame operations that supports both Scala 2.12 and 2.13.

## Features

This library provides utility methods for common DataFrame operations:

- **dedup**: Remove duplicate rows from a DataFrame
- **addRowNumber**: Add a row number column to the DataFrame
- **countNulls**: Count null values in each column
- **getBasicStats**: Get basic statistics for numeric columns
- **renameColumns**: Rename columns using a mapping

## Project Structure

```
spark-utils/
├── pom.xml
├── src/
│   ├── main/
│   │   └── scala/
│   │       └── com/
│   │           └── yourcompany/
│   │               └── sparkutils/
│   │                   ├── DataFrameUtils.scala
│   │                   └── package.scala
│   └── test/
│       └── scala/
│           └── com/
│               └── yourcompany/
│                   └── sparkutils/
│                       └── DataFrameUtilsTest.scala
└── README.md
```

## Build Instructions

### Prerequisites

- Java 8 or higher
- Maven 3.6 or higher
- Apache Spark 3.4.1

### Building for Scala 2.12 (default)

```bash
mvn clean compile
mvn clean package
```

### Building for Scala 2.13

```bash
mvn clean compile -Pscala-2.13
mvn clean package -Pscala-2.13
```

### Running Tests

```bash
# For Scala 2.12
mvn test

# For Scala 2.13
mvn test -Pscala-2.13
```

### Building Both Versions

```bash
# Build for Scala 2.12
mvn clean package -Pscala-2.12

# Build for Scala 2.13
mvn clean package -Pscala-2.13
```

### Publishing Note

Publishing uses concrete Maven coordinates per Scala binary version (`sparkplusplus_2.12` and `sparkplusplus_2.13`).
The 2.13 publish path temporarily rewrites `<artifactId>` during deploy to keep Sonatype Central filename validation consistent.

## Usage

### Using Object Methods

```scala
import org.apache.spark.sql.SparkSession
import com.yourcompany.sparkutils.DataFrameUtils

val spark = SparkSession.builder()
  .appName("SparkUtilsExample")
  .master("local[*]")
  .getOrCreate()

import spark.implicits._

val df = Seq(
  ("Alice", 25, "Engineer"),
  ("Bob", 30, "Manager"),
  ("Alice", 25, "Engineer")
).toDF("name", "age", "role")

// Remove duplicates
val dedupedDf = DataFrameUtils.dedup(df)

// Add row number
val withRowNum = DataFrameUtils.addRowNumber(df, "id", Seq("age"))

// Count nulls
val nullCounts = DataFrameUtils.countNulls(df)

// Get basic statistics
val stats = DataFrameUtils.getBasicStats(df)

// Rename columns
val renamedDf = DataFrameUtils.renameColumns(df, Map("name" -> "full_name"))
```

### Using Implicit Extensions

```scala
import org.apache.spark.sql.SparkSession
import com.yourcompany.sparkutils._

val spark = SparkSession.builder()
  .appName("SparkUtilsExample")
  .master("local[*]")
  .getOrCreate()

import spark.implicits._

val df = Seq(
  ("Alice", 25, "Engineer"),
  ("Bob", 30, "Manager"),
  ("Alice", 25, "Engineer")
).toDF("name", "age", "role")

// Using implicit extensions
val dedupedDf = df.dedup()
val withRowNum = df.addRowNumber("age")
val nullCounts = df.countNulls()
val stats = df.getBasicStats()
val renamedDf = df.renameColumns(Map("name" -> "full_name"))
```

## API Reference

### DataFrameUtils Object Methods

#### dedup(df: DataFrame, columns: Seq[String] = Seq.empty): DataFrame
Remove duplicate rows from a DataFrame.
- `df`: Input DataFrame
- `columns`: Optional list of columns to consider for deduplication
- Returns: DataFrame with duplicates removed

#### addRowNumber(df: DataFrame, columnName: String = "row_number", orderBy: Seq[String] = Seq.empty): DataFrame
Add a row number column to the DataFrame.
- `df`: Input DataFrame
- `columnName`: Name of the row number column (default: "row_number")
- `orderBy`: Optional columns to order by
- Returns: DataFrame with row number column added

#### countNulls(df: DataFrame): DataFrame
Count null values in each column.
- `df`: Input DataFrame
- Returns: DataFrame with column names and their null counts

#### getBasicStats(df: DataFrame): DataFrame
Get basic statistics for numeric columns.
- `df`: Input DataFrame
- Returns: DataFrame with basic statistics

#### renameColumns(df: DataFrame, columnMapping: Map[String, String]): DataFrame
Rename columns using a mapping.
- `df`: Input DataFrame
- `columnMapping`: Map of old column names to new column names
- Returns: DataFrame with renamed columns

### Implicit Extensions

When you import `com.yourcompany.sparkutils._`, the following methods are added to DataFrame:

- `df.dedup(columns: String*)`
- `df.addRowNumber(columnName: String, orderBy: String*)`
- `df.addRowNumber(orderBy: String*)`
- `df.countNulls()`
- `df.getBasicStats()`
- `df.renameColumns(columnMapping: Map[String, String])`

## Dependencies

- Scala 2.12.17 or 2.13.10
- Apache Spark 3.4.1
- ScalaTest 3.2.15 (for testing)

## Generated Artifacts

The build process generates JARs with Scala binary version in their names:
- `spark-utils_2.12-1.0.0.jar` (for Scala 2.12)
- `spark-utils_2.13-1.0.0.jar` (for Scala 2.13)

## License

This project is licensed under the Apache License 2.0.
