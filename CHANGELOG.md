# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2026-10-22

### Added

- Relational join operations: `innerJoin`, `leftJoin`, `rightJoin`, `outerJoin`, `crossJoin` with automatic column deduplication, multi-key join support, and customizable column suffixes ([#90](https://github.com/filippo-pequini/CupOfTea/issues/90)).
- Statistical operations on `ColumnVector`, `DataFrame`, and `CupOfTea`: `median`, `variance` / `var`, `standardDeviation` / `std`, `quantile`, `skewness` / `skew`, and `kurtosis` / `kurt` ([#175](https://github.com/filippo-pequini/CupOfTea/issues/175)).
- Guaranteed $O(n)$ rank selection and linear-interpolated quantile calculation using `Introselect` ([#175](https://github.com/filippo-pequini/CupOfTea/issues/175)).
- Primitive vector math vectorizations across `DoubleColumnVector`, `IntColumnVector`, `LongColumnVector`, and `FloatColumnVector` without boxing overhead ([#175](https://github.com/filippo-pequini/CupOfTea/issues/175)).
- Bivariate statistics: pairwise `covariance` / `cov`, Pearson `correlation` / `corr`, and square correlation matrix `corr()` ([#175](https://github.com/filippo-pequini/CupOfTea/issues/175)).
- Descriptive summary `describe()` producing a formatted summary DataFrame with 8 standard metrics (`count`, `mean`, `std`, `min`, `25%`, `50%`, `75%`, `max`) supporting numeric and temporal columns with index access ([#175](https://github.com/filippo-pequini/CupOfTea/issues/175)).
- Extended `GroupBy.agg(...)` and introduced `Aggregation` enum with `MEDIAN`, `STD`, `VAR`, `QUANTILE`, `SKEWNESS`, `KURTOSIS` plus fluent shortcut methods on `GroupBy` ([#175](https://github.com/filippo-pequini/CupOfTea/issues/175)).
- Adoption of Semantic Versioning (SemVer 2.0.0) with automated GitHub tag and release workflow on push to main/master branches.


- Columnar storage engine replacing internal row HashMaps with contiguous primitive vectors (`ColumnVector`, `IntColumnVector`, `LongColumnVector`, `DoubleColumnVector`, `FloatColumnVector`, `BooleanColumnVector`, `StringColumnVector`, `ObjectColumnVector`) ([#141](https://github.com/filippo-pequini/CupOfTea/issues/141)).
- Missing/null value tracking across columnar vectors using contiguous `BitSet` bitmasks ([#141](https://github.com/filippo-pequini/CupOfTea/issues/141)).
- Explicit `Index` model encapsulating row labels / index column designation with `getIndexObject()` on `IDataFrame` ([#141](https://github.com/filippo-pequini/CupOfTea/issues/141)).
- Nullability metadata support on `Column` (`nullable()`, `isNullable()`) and `Schema` (`isNullable(name)`, `isNullable(index)`) ([#141](https://github.com/filippo-pequini/CupOfTea/issues/141)).
- Column vector accessors `getColumnVectors()` and `getColumnVector(...)` on `IDataFrame` for high-throughput vectorized operations ([#141](https://github.com/filippo-pequini/CupOfTea/issues/141)).
- Dedicated columnar vector unit test suite `ColumnVectorTest` verifying vector operations, slicing, permutations, in-place filters, and high-volume performance ([#141](https://github.com/filippo-pequini/CupOfTea/issues/141)).
- Implemented relational DataFrame join operations: `join`, `innerJoin`, `leftJoin`, `rightJoin`, `outerJoin`, and `crossJoin` on `CupOfTea` and `IDataFrame` / `DataFrame` ([#90](https://github.com/filippo-pequini/CupOfTea/issues/90)).
- Added `JoinType` enum (`INNER`, `LEFT`, `RIGHT`, `OUTER`, `CROSS`) with string parsing (`fromString`) supporting SQL and pandas naming conventions ([#90](https://github.com/filippo-pequini/CupOfTea/issues/90)).
- Implemented O(N + M) hash-based join engine in `DataFrameJoiner` supporting single-column, multi-column, and natural joins on inferred common columns or designated index columns ([#90](https://github.com/filippo-pequini/CupOfTea/issues/90)).
- Supported asymmetric join column names via `leftOn` and `rightOn` retaining both key columns ([#90](https://github.com/filippo-pequini/CupOfTea/issues/90)).
- Added automatic column disambiguation for overlapping column names with customizable suffixes (`leftSuffix`, `rightSuffix`, defaulting to `_left` and `_right`) ([#90](https://github.com/filippo-pequini/CupOfTea/issues/90)).
- Ensured missing-value (`null` / `NA`) keys do not match each other conforming to SQL and pandas null semantics ([#90](https://github.com/filippo-pequini/CupOfTea/issues/90)).
- Added numeric type tolerance in join keys allowing `Integer`, `Long`, and integer-valued `Double` keys to match ([#90](https://github.com/filippo-pequini/CupOfTea/issues/90)).
- Ensured joins strictly honor and preserve the caller's `MutationMode` without mutating inputs ([#90](https://github.com/filippo-pequini/CupOfTea/issues/90)).
- Adopted Apache-2.0 open-source license and added full `LICENSE` file ([#144](https://github.com/filippo-pequini/CupOfTea/issues/144)).
- Added automated integration test `ReadmeExampleIntegrationTest` in `cupoftea-all` validating that README code snippets compile and run accurately ([#144](https://github.com/filippo-pequini/CupOfTea/issues/144)).
- Configured `maven-javadoc-plugin` (3.11.2) with Java 17 release support across the multi-module reactor ([#144](https://github.com/filippo-pequini/CupOfTea/issues/144)).
- Implemented core pandas-like operations: `select`, `drop`, `head`, `tail`, `slice`, `iloc`, `setIndex` / `resetIndex` / `loc`, `fillna`, and `dropna` ([#143](https://github.com/filippo-pequini/CupOfTea/issues/143)).
- Added position-based indexer `iloc` supporting 0-based and Python-style negative indexing (`df.iloc(int)`, `df.iloc(row, col)`, `df.iloc().slice(...)`, `df.iloc().rows(...)`, `df.iloc().columns(...)`) ([#143](https://github.com/filippo-pequini/CupOfTea/issues/143)).
- Added index-based indexer `loc` supporting row and cell lookups by designated index key (`df.setIndex(...)`, `df.loc(key)`, `df.loc(key, col)`, `df.loc().rows(...)`) ([#143](https://github.com/filippo-pequini/CupOfTea/issues/143)).
- Enhanced immutable `Row` API with positional accessors (`getInt(int)`, `getString(int)`, etc., `getColumnName(int)`), missing-value checks (`isNA`, `isNull`, `isMissing`), and safe handling of `NA` sentinel across all typed getters ([#143](https://github.com/filippo-pequini/CupOfTea/issues/143)).
- Ensured all operations strictly honor `MutationMode` (`IN_PLACE` modifying in-place, and `COPY_ON_WRITE` returning safe copies) ([#143](https://github.com/filippo-pequini/CupOfTea/issues/143)).

- Split IO into modular, optional Maven modules under parent coordinates `com.cupoftea` (`cupoftea-core`, `cupoftea-csv`, `cupoftea-json`, `cupoftea-excel`, `cupoftea-parquet`, `cupoftea-html`, `cupoftea-yaml`, `cupoftea-xml`, and `cupoftea-all`) ([#139](https://github.com/filippo-pequini/CupOfTea/issues/139)).
- ServiceLoader SPI (`FormatReader` and `FormatWriter`) for format decoupling and runtime discovery with informative diagnostics when a format dependency is absent ([#139](https://github.com/filippo-pequini/CupOfTea/issues/139)).
- Fluent reading and writing API: `CupOfTea.read().<format>(...)` and `df.write().<format>(...)` ([#139](https://github.com/filippo-pequini/CupOfTea/issues/139)).
- Introduced `MutationMode` (`IN_PLACE` fluent builder as default, and `COPY_ON_WRITE`) with `.asCopyOnWrite()` and `.asInPlace()` controls on `CupOfTea` ([#136](https://github.com/filippo-pequini/CupOfTea/issues/136)).
- Implemented explicit `.copy()` deep-copy method on `CupOfTea` and `IDataFrame` / `DataFrame` ([#136](https://github.com/filippo-pequini/CupOfTea/issues/136)).
- Introduced immutable `Row` class implementing `Map<String, Object>` with typed accessors (`getInt`, `getInteger`, `getLong`, `getDouble`, `getFloat`, `getBoolean`, `getString`, `getDate`, `getLocalDate`, `getLocalDateTime`, `getInstant`, `getBigDecimal`, `getAs`) and defensive encapsulation ([#136](https://github.com/filippo-pequini/CupOfTea/issues/136)).
- First-class `Schema` and `DataType` model supporting explicit schema definition on construction and ingest via `Schema.of("year", INT, "price", DOUBLE)` and `Schema.builder()` ([#142](https://github.com/filippo-pequini/CupOfTea/issues/142)).
- First-class missing-value sentinel `NA` (`NA.NA`), with `NA.isNA` and null policy distinguishing missing values (`null` / `NA`) from empty strings (`""`) ([#142](https://github.com/filippo-pequini/CupOfTea/issues/142)).
- Native `InstantConverter` and `BigDecimalConverter` registered in `CONVERTERS` for `java.time.Instant` and `java.math.BigDecimal` ([#142](https://github.com/filippo-pequini/CupOfTea/issues/142)).
- Full type inference on CSV ingest (`inferTypes`) covering `Boolean`, `Integer`, `Long`, `Double`, `LocalDate`, `LocalDateTime`, and `Instant` ([#142](https://github.com/filippo-pequini/CupOfTea/issues/142)).
- `ExcelImporterConfiguration` supporting schema, type inference, sheet selection, NA strings, and empty string preservation ([#142](https://github.com/filippo-pequini/CupOfTea/issues/142), [#80](https://github.com/filippo-pequini/CupOfTea/issues/80)).
- Support for `BigDecimal`, `Long`, and `Float` in aggregations (`sum`, `average`, `maximum`, `minimum`, `count`) with exact arithmetic avoiding binary floating-point representation error, and skipping null/NA values ([#142](https://github.com/filippo-pequini/CupOfTea/issues/142)).
- Documented null policy, type inference rules, and `BigDecimal` currency recommendations in `docs/null_policy.md` ([#142](https://github.com/filippo-pequini/CupOfTea/issues/142)).

### Changed

- Replaced internal DataFrame `ArrayList<HashMap<String, Object>>` storage with typed `ColumnVector` columnar layout; `Row` maps and `getRows()` now act as lightweight, lazy interchange views without eager map allocations ([#141](https://github.com/filippo-pequini/CupOfTea/issues/141)).
- Optimized filtering, sorting, slicing, column manipulation, and aggregation operations to execute directly on columnar vectors without per-cell boxed object overhead ([#141](https://github.com/filippo-pequini/CupOfTea/issues/141)).

- Targeted Java 17 LTS compiler release (`maven.compiler.release` 17) to provide broad enterprise compatibility across all modules ([#144](https://github.com/filippo-pequini/CupOfTea/issues/144)).
- Enriched `pom.xml` with complete project metadata (licenses, developers, SCM, issue management) for Maven/Gradle publication ([#144](https://github.com/filippo-pequini/CupOfTea/issues/144), [#82](https://github.com/filippo-pequini/CupOfTea/issues/82), [#83](https://github.com/filippo-pequini/CupOfTea/issues/83)).
- Overhauled `README.md` with verified Maven and Gradle (Groovy and Kotlin DSL) installation coordinates, tested fluent usage code, and updated roadmap ([#144](https://github.com/filippo-pequini/CupOfTea/issues/144)).
- Cleaned up leftover Portuguese docstrings and comments in `CupOfTea`, `Header`, `DataFrame`, and `HTMLImporter`, standardizing all documentation on English ([#144](https://github.com/filippo-pequini/CupOfTea/issues/144)).
- Standardized package coordinates to lowercase `com.cupoftea` ([#139](https://github.com/filippo-pequini/CupOfTea/issues/139)).
- Switched JSON parser engine to Jackson (`jackson-databind`) in `cupoftea-json` while retaining full type inference and backward-compatible `JSONArray` support ([#139](https://github.com/filippo-pequini/CupOfTea/issues/139)).
- Aligned Apache POI versions in `cupoftea-excel` (`poi` 5.5.1 and `poi-ooxml` 5.5.1) ([#139](https://github.com/filippo-pequini/CupOfTea/issues/139)).
- Decoupled `cupoftea-core` from Hadoop, POI, OpenCSV, and Jsoup compile dependencies so consumers only pull formats they actually use ([#139](https://github.com/filippo-pequini/CupOfTea/issues/139)).
- Encapsulated DataFrame internal storage: `getRows()` returns an unmodifiable list of `Row` objects and `getRow(int)` returns an unmodifiable `Row`, preventing internal map leaks and mutation side-effects ([#136](https://github.com/filippo-pequini/CupOfTea/issues/136)).
- Updated `IFilter` and `IColumnCreator` functional interfaces to accept `Row` instead of raw `Map<String, Object>` ([#136](https://github.com/filippo-pequini/CupOfTea/issues/136)).
- Rewrote `groupBy` as an O(n) hash aggregate with Pandas-style `.agg(...)` API instead of storing `Group` objects in cells ([#140](https://github.com/filippo-pequini/CupOfTea/issues/140)).
- Migrated test suite from JUnit 4 `junit.framework.TestCase` to JUnit 5 (`junit-jupiter`) and AssertJ (`assertj-core`), converting converter tests to parameterized tests (`@ParameterizedTest`) ([#145](https://github.com/filippo-pequini/CupOfTea/issues/145)).
- Replaced binary-float noise assertions with delta assertions ([#145](https://github.com/filippo-pequini/CupOfTea/issues/145)).
- Added regression tests for consecutive `removeRows` (#126), rename-by-name row key updates (#125), schema independence after `filter` (#127), empty frame `addColumn` (#134), quoted CSV parsing (#130), and static converter registry lookup (#132) ([#145](https://github.com/filippo-pequini/CupOfTea/issues/145)).
- Replaced `throws Exception` on public APIs with typed runtime exceptions in `com.cupoftea.exceptions` (`CupOfTeaException`, `SchemaMismatchException`, `ConversionException`, `ImportException`, `CsvParseException`, `ExportException`) ([#137](https://github.com/filippo-pequini/CupOfTea/issues/137)).
- Standardized all exception messages to English.
- Registered `java.time` (`LocalDate`, `LocalDateTime`), `Long`, and primitive type converters statically in `DataFrame` ([#138](https://github.com/filippo-pequini/CupOfTea/issues/138)).
- Replaced `SimpleDateFormat` with thread-safe `java.time.format.DateTimeFormatter` in `DateConverter` and deprecated `DateConverter` in favor of `java.time` ([#138](https://github.com/filippo-pequini/CupOfTea/issues/138), [#142](https://github.com/filippo-pequini/CupOfTea/issues/142)).

### Fixed

- Fixed incorrect `@param` tags in `Header.java` and missing Javadoc descriptions to ensure error-free Javadoc generation ([#144](https://github.com/filippo-pequini/CupOfTea/issues/144), [#61](https://github.com/filippo-pequini/CupOfTea/issues/61)).
- Enhanced index lookup (`df.loc(key)` and `df.loc().rows(...)`) to seamlessly match keys regardless of whether index column is stored as numeric or string ([#144](https://github.com/filippo-pequini/CupOfTea/issues/144)).
- `ExcelImporter` now identifies column types in headers rather than setting them to `null`, correctly reads boolean cells, and parses Excel date-formatted cells as `LocalDate` or `LocalDateTime` ([#80](https://github.com/filippo-pequini/CupOfTea/issues/80), [#142](https://github.com/filippo-pequini/CupOfTea/issues/142)).

- `Printer` now adjusts column widths dynamically based on content, fits table output within terminal width (defaulting to `COLUMNS` env var or 80) by truncating overflowing columns with an ellipsis column, supports rich customization via `PrinterConfiguration` (`maxRows`, `maxColumns`, `terminalWidth`, `showColumnType`, `autoAdjustColumnWidth`, etc.), improves table summary descriptions, and handles empty frames ([#100](https://github.com/filippo-pequini/CupOfTea/issues/100)).
- `DataFrame.orderBy` now compares using the column's declared or `Comparable` type with `Comparator.nullsLast`, supports numeric, string, temporal, and boolean columns without integer subtraction overflow, and treats empty frames as a no-op ([#133](https://github.com/filippo-pequini/CupOfTea/issues/133)).
- `DataFrame.alterColumnType` now uses a static converter map instead of scanning `target/classes` via `ClassFinder`, removing `ClassFinder`, the unused `org.reflections` dependency, `Class.forName` lookups, and broken numeric casts in `FloatConverter` and `DoubleConverter` ([#132](https://github.com/filippo-pequini/CupOfTea/issues/132)).
- `renameColumn(String, String)` and `renameColumn(HashMap)` now update row keys, not only the header ([#125](https://github.com/filippo-pequini/CupOfTea/issues/125)).
- `renameColumn` and `moveColumn` keep the existing column type instead of replacing it with `null` ([#128](https://github.com/filippo-pequini/CupOfTea/issues/128)).
- `filter` and `groupBy` now copy headers and row maps so mutating the returned frame does not affect the original ([#127](https://github.com/filippo-pequini/CupOfTea/issues/127)).
- `CSVImporter` now parses CSV files with OpenCSV using UTF-8 by default, supports configurable delimiter/quote/escape/charset and numeric type inference via `CSVImporterConfiguration`, generates sequential `0`-based headers when `hasHeaders` is `false`, and rejects ragged rows ([#130](https://github.com/filippo-pequini/CupOfTea/issues/130)).
- `JsonImporter` now preserves JSON primitive types (`Integer`, `Long`, `Double`, `Boolean`, `String`) and `null` values instead of stringifying every value, infers column types across rows, and supports an explicit schema on ingest ([#131](https://github.com/filippo-pequini/CupOfTea/issues/131)).
- `addColumn` with `IColumnCreator` now works on empty frames and when initial rows compute `null`, supporting an explicit `Class<?>` or defaulting to `Object.class` until a non-null row value is present ([#134](https://github.com/filippo-pequini/CupOfTea/issues/134)).
