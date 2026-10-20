# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The Maven artifact remains `1.0-SNAPSHOT` until the first tagged release.

## [Unreleased]

### Fixed

- `DataFrame.orderBy` now compares using the column's declared or `Comparable` type with `Comparator.nullsLast`, supports numeric, string, temporal, and boolean columns without integer subtraction overflow, and treats empty frames as a no-op ([#133](https://github.com/filippo-pequini/CupOfTea/issues/133)).
- `DataFrame.alterColumnType` now uses a static converter map instead of scanning `target/classes` via `ClassFinder`, removing `ClassFinder`, the unused `org.reflections` dependency, `Class.forName` lookups, and broken numeric casts in `FloatConverter` and `DoubleConverter` ([#132](https://github.com/filippo-pequini/CupOfTea/issues/132)).
- `renameColumn(String, String)` and `renameColumn(HashMap)` now update row keys, not only the header ([#125](https://github.com/filippo-pequini/CupOfTea/issues/125)).
- `renameColumn` and `moveColumn` keep the existing column type instead of replacing it with `null` ([#128](https://github.com/filippo-pequini/CupOfTea/issues/128)).
- `filter` and `groupBy` now copy headers and row maps so mutating the returned frame does not affect the original ([#127](https://github.com/filippo-pequini/CupOfTea/issues/127)).
- `CSVImporter` now parses CSV files with OpenCSV using UTF-8 by default, supports configurable delimiter/quote/escape/charset and numeric type inference via `CSVImporterConfiguration`, generates sequential `0`-based headers when `hasHeaders` is `false`, and rejects ragged rows ([#130](https://github.com/filippo-pequini/CupOfTea/issues/130)).
- `JsonImporter` now preserves JSON primitive types (`Integer`, `Long`, `Double`, `Boolean`, `String`) and `null` values instead of stringifying every value, infers column types across rows, and supports an explicit schema on ingest ([#131](https://github.com/filippo-pequini/CupOfTea/issues/131)).
