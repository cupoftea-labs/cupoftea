# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The Maven artifact remains `1.0-SNAPSHOT` until the first tagged release.

## [Unreleased]

### Changed

- Rewrote `groupBy` as an O(n) hash aggregate with Pandas-style `.agg(...)` API instead of storing `Group` objects in cells ([#140](https://github.com/filippo-pequini/CupOfTea/issues/140)).
- Migrated test suite from JUnit 4 `junit.framework.TestCase` to JUnit 5 (`junit-jupiter`) and AssertJ (`assertj-core`), converting converter tests to parameterized tests (`@ParameterizedTest`) ([#145](https://github.com/filippo-pequini/CupOfTea/issues/145)).
- Replaced binary-float noise assertions with delta assertions ([#145](https://github.com/filippo-pequini/CupOfTea/issues/145)).
- Added regression tests for consecutive `removeRows` (#126), rename-by-name row key updates (#125), schema independence after `filter` (#127), empty frame `addColumn` (#134), quoted CSV parsing (#130), and static converter registry lookup (#132) ([#145](https://github.com/filippo-pequini/CupOfTea/issues/145)).
- Replaced `throws Exception` on public APIs with typed runtime exceptions in `com.CupOfTea.exceptions` (`CupOfTeaException`, `SchemaMismatchException`, `ConversionException`, `ImportException`, `CsvParseException`, `ExportException`) ([#137](https://github.com/filippo-pequini/CupOfTea/issues/137)).
- Standardized all exception messages to English.
- Registered `java.time` (`LocalDate`, `LocalDateTime`), `Long`, and primitive type converters statically in `DataFrame` ([#138](https://github.com/filippo-pequini/CupOfTea/issues/138)).
- Replaced `SimpleDateFormat` with thread-safe `java.time.format.DateTimeFormatter` in `DateConverter` ([#138](https://github.com/filippo-pequini/CupOfTea/issues/138)).

### Fixed

- `Printer` now adjusts column widths dynamically based on content, fits table output within terminal width (defaulting to `COLUMNS` env var or 80) by truncating overflowing columns with an ellipsis column, supports rich customization via `PrinterConfiguration` (`maxRows`, `maxColumns`, `terminalWidth`, `showColumnType`, `autoAdjustColumnWidth`, etc.), improves table summary descriptions, and handles empty frames ([#100](https://github.com/filippo-pequini/CupOfTea/issues/100)).
- `DataFrame.orderBy` now compares using the column's declared or `Comparable` type with `Comparator.nullsLast`, supports numeric, string, temporal, and boolean columns without integer subtraction overflow, and treats empty frames as a no-op ([#133](https://github.com/filippo-pequini/CupOfTea/issues/133)).
- `DataFrame.alterColumnType` now uses a static converter map instead of scanning `target/classes` via `ClassFinder`, removing `ClassFinder`, the unused `org.reflections` dependency, `Class.forName` lookups, and broken numeric casts in `FloatConverter` and `DoubleConverter` ([#132](https://github.com/filippo-pequini/CupOfTea/issues/132)).
- `renameColumn(String, String)` and `renameColumn(HashMap)` now update row keys, not only the header ([#125](https://github.com/filippo-pequini/CupOfTea/issues/125)).
- `renameColumn` and `moveColumn` keep the existing column type instead of replacing it with `null` ([#128](https://github.com/filippo-pequini/CupOfTea/issues/128)).
- `filter` and `groupBy` now copy headers and row maps so mutating the returned frame does not affect the original ([#127](https://github.com/filippo-pequini/CupOfTea/issues/127)).
- `CSVImporter` now parses CSV files with OpenCSV using UTF-8 by default, supports configurable delimiter/quote/escape/charset and numeric type inference via `CSVImporterConfiguration`, generates sequential `0`-based headers when `hasHeaders` is `false`, and rejects ragged rows ([#130](https://github.com/filippo-pequini/CupOfTea/issues/130)).
- `JsonImporter` now preserves JSON primitive types (`Integer`, `Long`, `Double`, `Boolean`, `String`) and `null` values instead of stringifying every value, infers column types across rows, and supports an explicit schema on ingest ([#131](https://github.com/filippo-pequini/CupOfTea/issues/131)).
- `addColumn` with `IColumnCreator` now works on empty frames and when initial rows compute `null`, supporting an explicit `Class<?>` or defaulting to `Object.class` until a non-null row value is present ([#134](https://github.com/filippo-pequini/CupOfTea/issues/134)).
