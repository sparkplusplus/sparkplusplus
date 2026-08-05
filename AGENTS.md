# Repository Guidelines

## Project Structure & Module Organization

SparkPlusPlus is a Maven-built Scala library for Apache Spark. Production code
lives in `src/main/scala/io/github/sparkplusplus/`: utility extensions are at
the package root, the application framework is in `app/`, and config-driven
dataset IO is in `io/`. Tests mirror that package structure under
`src/test/scala`. `samples/customer-orders/` is a separate example Maven
project with YAML configuration, schemas, and fixture data; keep it aligned
with public API changes. Publishing notes live in `dev/`.

## Build, Test, and Development Commands

Use JDK 17 and Maven. The default Scala target is 2.12; CI validates both 2.12
and 2.13.

```bash
mvn clean verify -Pscala-2.12   # compile and run the primary test matrix entry
mvn clean verify -Pscala-2.13   # validate Scala 2.13 compatibility
make test                       # run both profiles (same as test212 + test213)
make install                    # install both cross-built artifacts locally
```

Run the focused profile first while iterating. Do not use `make snapshot` or
the deploy profiles unless you are intentionally publishing signed artifacts.

## Coding Style & Naming Conventions

Follow the existing Scala style: two-space indentation, braces for method
bodies, and one import per line. Use `PascalCase` for classes, traits, objects,
and case classes; use `camelCase` for methods, fields, and configuration keys.
Keep packages under `io.github.sparkplusplus`, prefer immutable `val`s, and
make public API changes explicit with Scaladoc where behavior is non-obvious.
There is no checked-in formatter or linter, so match surrounding code and keep
diffs narrowly scoped.

## Testing Guidelines

Tests use ScalaTest `AnyFunSuite`; name files `*Test.scala` and tests as
behavioral sentences, for example `test("load should reject missing outputs")`.
Spark-runtime tests carry the `RequiresSparkRuntime` tag and are excluded by
default, so add focused non-Spark coverage when logic can be isolated. Run both
Scala profiles before opening a compatibility-affecting pull request.

## Commit & Pull Request Guidelines

Use concise, imperative commit subjects (for example, `Add YAML config
validation`); release automation uses the `[release]` prefix. In pull requests,
describe the behavior change, list validation commands and both Scala profiles
when applicable, link the issue, and update `README.md` or the sample project
for user-facing APIs. Include configuration snippets or output evidence for
changes that affect Spark jobs or YAML contracts.
