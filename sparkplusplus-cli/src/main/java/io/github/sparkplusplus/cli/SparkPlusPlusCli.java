package io.github.sparkplusplus.cli;

import io.github.sparkplusplus.config.FrameworkConfigLoader$;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public final class SparkPlusPlusCli {
  private static final String VERSION = "0.0.1-SNAPSHOT";

  private SparkPlusPlusCli() {}

  public static void main(String[] args) {
    try {
      if (args.length == 2 && "validate".equals(args[0])) {
        FrameworkConfigLoader$.MODULE$.validate(args[1]);
        System.out.println("Valid SparkPlusPlus configuration: " + args[1]);
        return;
      }
      if (args.length >= 2 && "init".equals(args[0])) {
        init(args);
        return;
      }
      usage();
      System.exit(2);
    } catch (IllegalArgumentException exception) {
      System.err.println("Configuration error: " + exception.getMessage());
      System.exit(2);
    } catch (IOException exception) {
      System.err.println("File error: " + exception.getMessage());
      System.exit(1);
    }
  }

  private static void init(String[] args) throws IOException {
    Path projectDirectory = Paths.get(args[1]).toAbsolutePath().normalize();
    String packageName = "example";
    String applicationName = projectDirectory.getFileName().toString();

    for (int index = 2; index < args.length; index += 2) {
      if (index + 1 >= args.length) throw new IllegalArgumentException("Missing value after " + args[index]);
      if ("--package".equals(args[index])) packageName = args[index + 1];
      else if ("--name".equals(args[index])) applicationName = args[index + 1];
      else throw new IllegalArgumentException("Unknown init option: " + args[index]);
    }

    if (!packageName.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*")) {
      throw new IllegalArgumentException("--package must be a lowercase Scala package name");
    }
    if (applicationName.trim().isEmpty()) throw new IllegalArgumentException("--name must not be empty");
    if (Files.exists(projectDirectory)) {
      try (java.util.stream.Stream<Path> entries = Files.list(projectDirectory)) {
        if (entries.findAny().isPresent()) {
          throw new IllegalArgumentException("Refusing to initialize into a non-empty directory: " + projectDirectory);
        }
      }
    }

    String className = toClassName(applicationName) + "App";
    String settingsName = toClassName(applicationName) + "Settings";
    Path sourceDirectory = projectDirectory.resolve("src/main/scala").resolve(packageName.replace('.', '/'));
    Files.createDirectories(sourceDirectory);
    Files.createDirectories(projectDirectory.resolve("src/test/scala").resolve(packageName.replace('.', '/')));
    Files.createDirectories(projectDirectory.resolve("conf"));

    write(projectDirectory.resolve("pom.xml"), pom(applicationName));
    write(projectDirectory.resolve("README.md"), readme(applicationName, packageName, className));
    write(projectDirectory.resolve("conf/application.yaml"), yaml(applicationName));
    write(sourceDirectory.resolve(className + ".scala"), app(packageName, className, settingsName));
    write(projectDirectory.resolve("src/test/scala").resolve(packageName.replace('.', '/')).resolve(className + "Test.scala"), test(packageName, className));
    System.out.println("Created SparkPlusPlus project at " + projectDirectory);
  }

  private static void write(Path path, String content) throws IOException {
    Files.write(path, content.getBytes(StandardCharsets.UTF_8));
  }

  private static String toClassName(String value) {
    StringBuilder result = new StringBuilder();
    for (String part : value.split("[^A-Za-z0-9]+")) {
      if (!part.isEmpty()) result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
    }
    if (result.length() == 0 || !Character.isJavaIdentifierStart(result.charAt(0))) {
      throw new IllegalArgumentException("--name must contain letters or digits and start with a letter");
    }
    return result.toString();
  }

  private static String pom(String artifactId) {
    return """
      <project xmlns=\"http://maven.apache.org/POM/4.0.0\">
        <modelVersion>4.0.0</modelVersion>
        <groupId>example</groupId><artifactId>%s</artifactId><version>0.1.0-SNAPSHOT</version>
        <properties><scala.version>2.12.18</scala.version><scala.binary.version>2.12</scala.binary.version><spark.version>3.4.1</spark.version><scalatest.version>3.2.15</scalatest.version></properties>
        <dependencies>
          <dependency><groupId>io.github.sparkplusplus</groupId><artifactId>sparkplusplus-core_2.12</artifactId><version>%s</version></dependency>
          <dependency><groupId>io.github.sparkplusplus</groupId><artifactId>sparkplusplus-config_2.12</artifactId><version>%s</version></dependency>
          <dependency><groupId>org.apache.spark</groupId><artifactId>spark-sql_${scala.binary.version}</artifactId><version>${spark.version}</version><scope>provided</scope></dependency>
          <dependency><groupId>org.scalatest</groupId><artifactId>scalatest_${scala.binary.version}</artifactId><version>${scalatest.version}</version><scope>test</scope></dependency>
        </dependencies>
        <build><sourceDirectory>src/main/scala</sourceDirectory><testSourceDirectory>src/test/scala</testSourceDirectory><plugins>
          <plugin><groupId>net.alchim31.maven</groupId><artifactId>scala-maven-plugin</artifactId><version>4.8.1</version><executions><execution><goals><goal>compile</goal><goal>testCompile</goal></goals></execution></executions><configuration><scalaVersion>${scala.version}</scalaVersion></configuration></plugin>
          <plugin><groupId>org.scalatest</groupId><artifactId>scalatest-maven-plugin</artifactId><version>2.2.0</version><executions><execution><id>test</id><goals><goal>test</goal></goals></execution></executions></plugin>
          <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-shade-plugin</artifactId><version>3.5.3</version><executions><execution><phase>package</phase><goals><goal>shade</goal></goals><configuration><createDependencyReducedPom>false</createDependencyReducedPom></configuration></execution></executions></plugin>
        </plugins></build>
      </project>
      """.formatted(artifactId, VERSION, VERSION);
  }

  private static String yaml(String applicationName) {
    return """
      apiVersion: sparkplusplus.io/v1
      application:
        name: %s
        version: 0.1.0
        settings:
          sourcePath: input/orders.parquet
          targetPath: output/orders
      sparkConfig:
        spark.app.name: %s
        spark.sql.session.timeZone: UTC
      inputs: []
      outputs: []
      """.formatted(applicationName, applicationName);
  }

  private static String app(String packageName, String className, String settingsName) {
    return """
      package %s

      import io.github.sparkplusplus.app.AppContext
      import io.github.sparkplusplus.config.{FrameworkConfig, FrameworkSparkETLApp}
      import org.apache.spark.sql.DataFrame

      final case class %s(sourcePath: String, targetPath: String)

      object %s extends FrameworkSparkETLApp[%s] {
        override protected def applicationSettingsClass: Class[%s] = classOf[%s]

        override protected def transform(
          ctx: AppContext[FrameworkConfig[%s]],
          settings: %s,
          inputs: Map[String, DataFrame]
        ): Map[String, DataFrame] = Map.empty
      }
      """.formatted(packageName, settingsName, className, settingsName, settingsName, settingsName, settingsName, settingsName);
  }

  private static String test(String packageName, String className) {
    return """
      package %s

      import org.scalatest.funsuite.AnyFunSuite

      class %sTest extends AnyFunSuite {
        test(\"application is available\") {
          assert(%s != null)
        }
      }
      """.formatted(packageName, className, className);
  }

  private static String readme(String applicationName, String packageName, String className) {
    return """
      # %s

      Validate configuration before submitting a Spark job:

      ```bash
      sparkplusplus validate conf/application.yaml
      spark-submit --class %s.%s target/%s-0.1.0-SNAPSHOT.jar --config conf/application.yaml
      ```
      """.formatted(applicationName, packageName, className, applicationName);
  }

  private static void usage() {
    System.err.println("Usage: sparkplusplus validate <config.yaml> | sparkplusplus init <directory> [--package value] [--name value]");
  }
}
