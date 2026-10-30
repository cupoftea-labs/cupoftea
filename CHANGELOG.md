# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.17.3] - 2026-10-30

### Performance

- Stream raw UTF-8 chunk bytes directly into `StringColumnVector` buffers during `FastCsvReader` ingestion ([#218](https://github.com/cupoftea-labs/cupoftea/issues/218)):
  - Replaced intermediate `String[]` chunk arrays and `FastStringPool` string decoding for `String` columns with contiguous UTF-8 byte buffers (`StringChunkBuffer`: `byte[] values`, `int[] offsets`).
  - Added parallel chunk byte-buffer concatenation and offset adjustment (`System.arraycopy` across worker threads) in `FastCsvReader.assembleVectors(...)`, eliminating 100M single-threaded `String.getBytes(UTF_8)` re-encoding calls.
  - Added L2-cache-resident windowed SIMD scanning over `MappedByteBuffer` (`SimdCsvScanner.parseChunkSimdMapped`) to avoid humongous heap allocations on multi-gigabyte CSV files.
  - Optimized `StringColumnVector.copyIndexedValuesOnly` with an $O(G)$ single-pass bulk copy fast path instead of repeated $O(G^2)$ `replaceRowBytes` offset shifts during `GroupBy` key materialization.
  - Eliminated unnecessary deep cloning of source column vectors in `GroupBy` construction, avoiding multi-gigabyte heap allocations and GC pauses during large aggregations.

## [0.17.2] - 2026-10-30

### Performance

- Contiguous Arrow-style UTF-8 string vectors backed by contiguous bytes buffer and offset array ([#214](https://github.com/cupoftea-labs/cupoftea/issues/214)):
  - Replaced pointer-heavy `String[]` array in `StringColumnVector` with contiguous `byte[] values` buffer and `int[] offsets` index array, eliminating 100M heap pointer allocations.
  - Implemented zero-copy byte slice comparisons (`compareRows`, `rowsEqual`, `rowEqualsBytes`) and hash calculations (`hashRow`) without instantiating `java.lang.String` objects.
  - Added direct UTF-8 byte ingestion (`appendUtf8Bytes`) and direct byte buffer constructors (`StringColumnVector(name, nullable, values, valuesCount, offsets, nulls, size)`).
  - Integrated `FastCsvReader` to populate contiguous byte vectors directly during parallel chunk parsing.

## [0.17.1] - 2026-10-30

### Performance

- Optimized GroupBy with primitive open-addressing hash table and single-pass aggregation ([#210](https://github.com/cupoftea-labs/cupoftea/issues/210)):
  - Replaced `Map<List<Object>, IntList>` with primitive-backed `GroupHashTable` storing representative row indices, eliminating per-row key object allocation and pointer chasing.
  - Implemented single-pass accumulation across chunk data for `sum`, `mean`, `count`, `min`, `max`, and `listAgg`.
  - Populated typed output column vectors (`DoubleColumnVector`, `IntColumnVector`, `LongColumnVector`, `FloatColumnVector`, `StringColumnVector`) directly by index, avoiding row-level `HashMap` creation and boxing.

## [0.17.0] - 2026-10-30

### Added

- Two-Stage SIMD CSV structural scanner (`SimdCsvScanner`) using the Java Vector API (`jdk.incubator.vector.ByteVector` with `SPECIES_PREFERRED` and 4× loop unrolling), branchless parallel prefix-XOR quote parity resolution (`prefixXor`), and `TZCNT`/`BLSR` bit-scanning intrinsics ([#215](https://github.com/cupoftea-labs/cupoftea/issues/215)).
- Runtime hardware and module capability detector (`SimdDetector`) with automatic fallback to scalar scanning when `--add-modules jdk.incubator.vector` is absent or disabled (`-Dcupoftea.simd.disabled=true`) ([#215](https://github.com/cupoftea-labs/cupoftea/issues/215)).
- Added `simdEnabled` toggle (`isSimdEnabled()`, `setSimdEnabled(boolean)`) to `CSVImporterConfiguration` (enabled by default) and `FastCsvReader.readInto(byte[], CupOfTea)` ([#215](https://github.com/cupoftea-labs/cupoftea/issues/215)).
- JMH micro- and end-to-end SIMD CSV benchmark suite (`SimdCsvScannerBenchmark`) covering standard wide CSV, high-density quoted CSV, and mixed varied-length CSV datasets ([#215](https://github.com/cupoftea-labs/cupoftea/issues/215)).

## [0.16.0] - 2026-10-29

### Added

- Native chunk-based parallel CSV reader (`FastCsvReader`) supporting direct byte decoding, NIO memory-mapping, zero/low object allocation, and multi-core parallel ingestion ([#208](https://github.com/cupoftea-labs/cupoftea/issues/208)).
- Added `fastEngine` toggle to `CSVImporterConfiguration` (enabled by default) for high-throughput columnar ingestion.

## [0.15.1] - 2026-10-28

### Fixed

- Upgraded `central-publishing-maven-plugin` to `0.12.0` to handle Sonatype Central Portal API response schema update (support for new `warnings` field).

## [0.15.0] - 2026-10-28

### Changed

- Updated Maven `groupId` across parent and all 13 reactor modules from `com.cupoftea` to `io.github.cupoftea-labs` to align with the verified Sonatype Central Portal namespace.
- Updated documentation and README coordinates and badges to `io.github.cupoftea-labs`.

## [0.14.0] - 2026-10-28

### Added

- Automated SNAPSHOT release workflow to GitHub Packages (`.github/workflows/publish-snapshot.yml`) on pushes to `develop` and `master` ([#181](https://github.com/cupoftea-labs/cupoftea/issues/181), [#83](https://github.com/cupoftea-labs/cupoftea/issues/83)).
- Automated Maven Central Portal release workflow (`.github/workflows/release.yml`) with GPG artifact signing, source and Javadoc attachment, and automated GitHub Releases ([#181](https://github.com/cupoftea-labs/cupoftea/issues/181), [#82](https://github.com/cupoftea-labs/cupoftea/issues/82)).
- Comprehensive release procedures and maintainer guide in `docs/releasing.md` ([#181](https://github.com/cupoftea-labs/cupoftea/issues/181)).
- Central publishing Maven plugin (`central-publishing-maven-plugin`), GPG plugin, and distribution management in `pom.xml`.
- Release status and registry badges in `README.md`.

## [0.13.0] - 2026-10-28

### Added

- Comprehensive documentation website using **MkDocs Material** with responsive light/dark theme, full-text search, and Mermaid diagram support ([#201](https://github.com/cupoftea-labs/cupoftea/issues/201)).
- Automated multi-module aggregate Javadoc publishing pipeline deployed alongside user guides under `/apidocs/` ([#201](https://github.com/cupoftea-labs/cupoftea/issues/201)).
- Complete documentation guides for all modules, columnar memory layout, typing & schema inference, immutability & mutation modes, `loc`/`iloc` indexing, and IO format SPI implementations ([#201](https://github.com/cupoftea-labs/cupoftea/issues/201)).
- GitHub Pages continuous deployment workflow (`.github/workflows/deploy-docs.yml`) triggered automatically on pushes to `develop` and `master` ([#201](https://github.com/cupoftea-labs/cupoftea/issues/201)).
- Repository & Organization migration documentation ([#201](https://github.com/cupoftea-labs/cupoftea/issues/201)).

## [0.12.0] - 2026-10-27

### Added

- Comprehensive JMH micro- and macro-benchmarking module `cupoftea-benchmark` with high-volume throughput and comparative baseline suites ([#195](https://github.com/filippo-pequini/CupOfTea/issues/195)).
- High-performance synthetic dataset generator (`SyntheticDataGenerator`) with support for numeric primitives, nullable values, high-cardinality strings, and multi-format files (CSV, JSON, Parquet, Excel, XML) ([#195](https://github.com/filippo-pequini/CupOfTea/issues/195)).
- Primitive columnar scan micro-benchmarks (`ColumnarVectorScanBenchmark`) for sequential and vectorized scans across `IntColumnVector`, `DoubleColumnVector`, `LongColumnVector`, `FloatColumnVector`, `BooleanColumnVector`, and `StringColumnVector` ([#195](https://github.com/filippo-pequini/CupOfTea/issues/195)).
- Parallel filter predicates benchmark (`ParallelScanAndFilterBenchmark`) validating single-threaded vs. parallel throughput across 1%, 10%, 50%, and 90% selectivity thresholds ([#195](https://github.com/filippo-pequini/CupOfTea/issues/195)).
- Map-reduce aggregations benchmark (`MapReduceAggregationBenchmark`) evaluating vectorized reductions (`sum`, `mean`, `min`, `max`, `median`, `std`, `count`) ([#195](https://github.com/filippo-pequini/CupOfTea/issues/195)).
- Zero-copy slicing benchmark (`ZeroCopySlicingBenchmark`) demonstrating >40,000x latency reduction and zero GC allocations for `sliceView` and chunk partitioning ([#195](https://github.com/filippo-pequini/CupOfTea/issues/195)).
- End-to-end format IO ingestion and egress benchmarks (`EndToEndIoBenchmark`) across CSV, JSON, Parquet, Excel, and XML ([#195](https://github.com/filippo-pequini/CupOfTea/issues/195)).
- End-to-end data pipeline macro-benchmark (`EndToEndPipelineBenchmark`) measuring multi-stage ingest, imputation, filtering, derivation, aggregation, and export ([#195](https://github.com/filippo-pequini/CupOfTea/issues/195)).
- Multi-core CPU scaling benchmark (`MultiCoreScalingBenchmark`) measuring Amdahl speedup across 1, 2, 4, 8, and 16 worker threads ([#195](https://github.com/filippo-pequini/CupOfTea/issues/195)).
- Dataset scale benchmark (`DatasetScaleBenchmark`) characterizing throughput scaling from 10k to 10M rows ([#195](https://github.com/filippo-pequini/CupOfTea/issues/195)).
- Competitive baseline benchmark (`CompetitiveBaselineBenchmark`) against Tablesaw 0.43.1 demonstrating significant speedups across scans, filters, and aggregations ([#195](https://github.com/filippo-pequini/CupOfTea/issues/195)).
- Automated continuous performance benchmarking GitHub Actions workflow (`.github/workflows/benchmarks.yml`) and reproducible documentation guide (`docs/benchmarks.md`) ([#195](https://github.com/filippo-pequini/CupOfTea/issues/195)).

## [0.11.0] - 2026-10-27

### Added

- Support for importing and exporting DTOs (JavaBeans / POJOs) and Java Records ([#42](https://github.com/filippo-pequini/CupOfTea/issues/42), [#43](https://github.com/filippo-pequini/CupOfTea/issues/43), [#78](https://github.com/filippo-pequini/CupOfTea/issues/78), [#81](https://github.com/filippo-pequini/CupOfTea/issues/81)):
  - Importers: `DtoImporter` / `IDtoImporter` and `RecordImporter` / `IRecordImporter` with `CupOfTea.importFromDtos(...)` and `CupOfTea.importFromRecords(...)`.
  - Exporters: `DtoExporter` / `IDtoExporter` and `RecordExporter` / `IRecordExporter` with `CupOfTea.exportToDtos(Class)` and `CupOfTea.exportToRecords(Class)`.
  - Fluent IO integration via `CupOfTea.read().records(...)` / `CupOfTea.read().dtos(...)` and `df.write().records(...)` / `df.write().dtos(...)`.
  - Full support for `MutationMode` (`COPY_ON_WRITE` and `IN_PLACE`) and automated type conversion during mapping.

## [0.10.0] - 2026-10-27

### Added

- New database integration module `cupoftea-sql` providing JDBC read and write support with batching and transaction control ([#28](https://github.com/filippo-pequini/CupOfTea/issues/28), [#55](https://github.com/filippo-pequini/CupOfTea/issues/55)).
- Fluent SQL reading and writing API: `CupOfTea.read().sql(rs)`, `CupOfTea.read().sql(conn, query)`, `CupOfTea.read().sqlTable(conn, table)`, `CupOfTea.read().sql(dataSource, query)`, and `df.write().sql(conn, table)` ([#28](https://github.com/filippo-pequini/CupOfTea/issues/28), [#55](https://github.com/filippo-pequini/CupOfTea/issues/55)).
- Convenience DataFrame methods: `df.importFromSql(...)` and `df.exportToSql(...)` ([#28](https://github.com/filippo-pequini/CupOfTea/issues/28), [#55](https://github.com/filippo-pequini/CupOfTea/issues/55)).
- Configurable SQL import via `SqlImporterConfiguration` with `fetchSize`, `maxRows`, `queryTimeout`, and explicit schema override ([#28](https://github.com/filippo-pequini/CupOfTea/issues/28)).
- Configurable SQL export via `SqlExporterConfiguration` with `SaveMode` (`APPEND`, `OVERWRITE`, `FAIL_IF_EXISTS`), configurable batch sizing, automatic table creation, and transactional commit/rollback ([#55](https://github.com/filippo-pequini/CupOfTea/issues/55)).
- ServiceLoader SPI registration for `SqlFormatReader` and `SqlFormatWriter` ([#28](https://github.com/filippo-pequini/CupOfTea/issues/28), [#55](https://github.com/filippo-pequini/CupOfTea/issues/55)).

## [0.9.0] - 2026-10-27

### Added

- Parallel and multithreaded DataFrame operations (`df.parallel()`, `df.asParallel()`, `df.sequential()`, `df.asSequential()`) with customizable thread pools (`ForkJoinPool` or custom `ExecutorService`) and configurable parallel thresholds (`df.withParallelThreshold(n)`) ([#179](https://github.com/filippo-pequini/CupOfTea/issues/179)).
- Zero-copy columnar slice view (`ColumnVectorSlice`) and parallel chunk partitioning (`vector.partition(n)`) allowing multi-threaded chunk execution without copying underlying primitive arrays or bitsets ([#179](https://github.com/filippo-pequini/CupOfTea/issues/179)).
- Vectorized parallel row filtering using parallel chunk evaluation and prefix-sum offsets (`ParallelVectorEngine.prefixSumScan`), producing compact, contiguous filtered vectors in parallel without row map allocations ([#179](https://github.com/filippo-pequini/CupOfTea/issues/179)).
- Parallel column mapping (`df.mapColumn`, `df.mapInt`, `df.mapLong`, `df.mapDouble`) and parallel row mapping (`df.map(row -> ...)`) with deterministic index ordering ([#179](https://github.com/filippo-pequini/CupOfTea/issues/179)).
- Parallel aggregations across whole DataFrames (`df.agg()`, `df.sumOf()`, `df.meanOf()`, `df.minOf()`, `df.maxOf()`, `df.countOf()`) and parallel chunk-partitioned `df.groupBy(...).agg(...)` ([#179](https://github.com/filippo-pequini/CupOfTea/issues/179)).
- Zero-copy columnar `Row` accessor backed directly by column vectors, eliminating per-row HashMap instantiations during iterative and parallel operations ([#179](https://github.com/filippo-pequini/CupOfTea/issues/179)).

## [0.8.0] - 2026-10-27

### Added

- Data visualization module `cupoftea-plot` providing declarative plotting API (`df.plot()`, `Plots.of(df)`) backed by Vega-Lite v5 specifications ([#183](https://github.com/filippo-pequini/CupOfTea/issues/183)).
- Supported chart types: line (`line(x, y)`), bar (`bar(x, y)`), scatter (`scatter(x, y)`, `scatter(x, y, colorCol, sizeCol)`), histogram (`hist(column, bins)`), box plot (`box(categoryCol, valueCol)`), area chart (`area(x, y)`), and heatmap matrix (`heatmap(x, y, values)`) ([#183](https://github.com/filippo-pequini/CupOfTea/issues/183)).
- Standalone interactive HTML chart generation (`saveHtml(file)`) and desktop browser preview (`show()`) with headless/CI safe fallbacks ([#183](https://github.com/filippo-pequini/CupOfTea/issues/183)).
- Tabular HTML renderer (`df.toHtml()`) on `IDataFrame` and `CupOfTea` with sticky column headers, column data type indicators, client-side pagination, and customizable configurations via `TableHtmlConfiguration` ([#183](https://github.com/filippo-pequini/CupOfTea/issues/183)).
- Jupyter / IJava / Kotlin notebook rich MIME rendering support (`renderHtml()`, `render()` returning `text/html`) on `IDataFrame`, `DataFrame`, `CupOfTea`, and `Chart`, allowing tables and charts to render interactively directly in notebook cells ([#183](https://github.com/filippo-pequini/CupOfTea/issues/183)).
- ServiceLoader SPI `PlotProvider` and `PlotRegistry` for runtime discovery of plotting engines while keeping `cupoftea-core` lightweight ([#183](https://github.com/filippo-pequini/CupOfTea/issues/183)).

## [0.7.0] - 2026-10-24

### Added

- New format module `cupoftea-arrow` providing native Apache Arrow IPC and Feather zero-copy format support ([#180](https://github.com/filippo-pequini/CupOfTea/issues/180)).
- Fluent Arrow IO integration: `CupOfTea.read().arrow(...)`, `CupOfTea.read().feather(...)`, `df.write().arrow(...)`, and `df.write().feather(...)` ([#180](https://github.com/filippo-pequini/CupOfTea/issues/180)).
- Support for both Arrow File (Feather v2 random-access) and Arrow Stream IPC protocols with automatic header magic detection (`ARROW1`) and explicit format selection ([#180](https://github.com/filippo-pequini/CupOfTea/issues/180)).
- Direct zero-copy memory transfer between Arrow off-heap primitive vectors (`IntVector`, `BigIntVector`, `Float8Vector`, `Float4Vector`, `BitVector`, `VarCharVector`) and CupOfTea columnar vectors (`IntColumnVector`, `LongColumnVector`, `DoubleColumnVector`, `FloatColumnVector`, `BooleanColumnVector`, `StringColumnVector`) with `BitSet` null bitmasks ([#180](https://github.com/filippo-pequini/CupOfTea/issues/180)).
- Bidirectional schema converters between CupOfTea `Schema` and Apache Arrow `org.apache.arrow.vector.types.pojo.Schema` preserving primitive, temporal (`LocalDate`, `Instant`, `LocalDateTime`), and `BigDecimal` data fidelity ([#180](https://github.com/filippo-pequini/CupOfTea/issues/180)).
- ServiceLoader SPI registration for `ArrowFormatReader`, `FeatherFormatReader`, `ArrowFormatWriter`, and `FeatherFormatWriter` ([#180](https://github.com/filippo-pequini/CupOfTea/issues/180)).

## [0.6.0] - 2026-10-23

### Added

- Reshaping operations: pivot table (`df.pivot(...)`) supporting custom aggregations (`Aggregation` and `Aggregate`) and type preservation ([#177](https://github.com/filippo-pequini/CupOfTea/issues/177)).
- Unpivot / melt operation (`df.melt(...)`) transforming wide format to long format with direct columnar vector recreation ([#177](https://github.com/filippo-pequini/CupOfTea/issues/177)).
- Deduplication operations (`df.dropDuplicates(...)` and `df.distinct(...)`) with configurable retention strategies (`Keep.FIRST`, `Keep.LAST`, `Keep.NONE`) and optional column subsets, respecting `MutationMode` ([#177](https://github.com/filippo-pequini/CupOfTea/issues/177)).
- Random sampling (`df.sample(...)`) supporting row count, fractional sampling, optional replacement, and reproducible pseudo-random seeding ([#177](https://github.com/filippo-pequini/CupOfTea/issues/177)).

## [0.5.0] - 2026-10-23

### Added

- Rolling Windows (`df.rolling(int windowSize)`): Fluent sliding-window builder supporting `mean`, `sum`, `min`, `max`, `std`, `variance` / `var`, `median`, `count`, and custom aggregation functions via `apply` ([#176](https://github.com/filippo-pequini/CupOfTea/issues/176)).
- Primitive sliding-window algorithms: Amortized $O(1)$ rolling sum, mean, running variance/std, and monotonic deque minimum and maximum; and $O(\log W)$ rolling median via dual heaps with lazy deletion ([#176](https://github.com/filippo-pequini/CupOfTea/issues/176)).
- Configurable rolling window properties: customizable `minPeriods`, centered (`center()`, `centered()`), forward-looking (`forward()`), and backward-looking (`backward()`) window directions ([#176](https://github.com/filippo-pequini/CupOfTea/issues/176)).
- Cumulative Operations: `cumsum`, `cumprod`, `cummin`, `cummax` preserving missing values (`null` / `NA`) according to null policy, supporting `BigDecimal` exact arithmetic, integers, and floating-point types ([#176](https://github.com/filippo-pequini/CupOfTea/issues/176)).
- Expanding Windows (`df.expanding()`, `df.expanding(int minPeriods)`): Historical cumulative windows from frame start up to current row $[0, i]$ with incremental $O(1)$ statistical operations ([#176](https://github.com/filippo-pequini/CupOfTea/issues/176)).
- Window & Analytic SQL-like Functions: `lead`, `lag`, `diff`, `pctChange`, and ranking functions `rank`, `denseRank`, `percentRank`, and `rowNumber` ([#176](https://github.com/filippo-pequini/CupOfTea/issues/176)).
- SQL-like Window Partitioning: `df.window().partitionBy(...).orderBy(...).rank()` computing analytical metrics across partitions while strictly preserving original DataFrame row alignment and order ([#176](https://github.com/filippo-pequini/CupOfTea/issues/176)).
- Complete compatibility with `MutationMode.IN_PLACE` and `MutationMode.COPY_ON_WRITE` across all window, rolling, and cumulative operations ([#176](https://github.com/filippo-pequini/CupOfTea/issues/176)).

## [0.4.0] - 2026-10-23

### Added

- Fluent column reference helper `col(String name)` on `IDataFrame`, `DataFrame`, and `CupOfTea` ([#178](https://github.com/filippo-pequini/CupOfTea/issues/178)).
- Vectorized string expression helper (`.str`) and direct `StringColumnVector` operations: `toLowerCase`, `toUpperCase`, `capitalize`, `trim`, `strip`, `lstrip`, `rstrip`, `contains`, `startsWith`, `endsWith`, `replace`, `replaceAll`, `split`, `substring` / `slice`, `length`, `padLeft`, and `padRight` ([#178](https://github.com/filippo-pequini/CupOfTea/issues/178)).
- Vectorized temporal expression helper (`.dt`) operating on `LocalDate`, `LocalDateTime`, `Instant`, and `Date` columns ([#178](https://github.com/filippo-pequini/CupOfTea/issues/178)).
- Temporal calendar extraction: `year`, `month`, `monthName`, `dayOfMonth`, `dayOfWeek`, `dayOfYear`, and `quarter` ([#178](https://github.com/filippo-pequini/CupOfTea/issues/178)).
- Temporal time extraction: `hour`, `minute`, `second`, and `nano` ([#178](https://github.com/filippo-pequini/CupOfTea/issues/178)).
- Vectorized date arithmetic: `plusDays`, `minusDays`, `plusMonths`, `minusMonths`, `plusYears`, `minusYears`, `plusHours`, and `minusHours` ([#178](https://github.com/filippo-pequini/CupOfTea/issues/178)).
- Temporal difference calculations: `daysBetween` and `hoursBetween` across columns or constant timestamps with time zone and Daylight Saving Time (DST) awareness ([#178](https://github.com/filippo-pequini/CupOfTea/issues/178)).
- Temporal formatting and parsing: `format`, `parseLocalDate`, `parseLocalDateTime`, and `parseInstant` ([#178](https://github.com/filippo-pequini/CupOfTea/issues/178)).
- Temporal truncation and flooring: `startOfMonth`, `startOfYear`, `startOfDay`, and `truncateTo` ([#178](https://github.com/filippo-pequini/CupOfTea/issues/178)).
- In-place column transformation (`transformColumn`) and new column generation (`withColumn`) supporting both vector expressions and lambdas ([#178](https://github.com/filippo-pequini/CupOfTea/issues/178)).
- Strict null-safe and NA value propagation preserving columnar bitmasks without exceptions ([#178](https://github.com/filippo-pequini/CupOfTea/issues/178)).
- Comprehensive unit tests covering Unicode characters, emojis, leap years, and Daylight Saving Time (DST) transitions ([#178](https://github.com/filippo-pequini/CupOfTea/issues/178)).

## [0.3.0] - 2026-10-23

### Added

- DataFrame concatenation engine `DataFrameConcatenator` supporting both vertical (`ConcatAxis.ROWS` / axis=0) and horizontal (`ConcatAxis.COLUMNS` / axis=1) concatenation ([#89](https://github.com/filippo-pequini/CupOfTea/issues/89)).
- Fluent instance and static concatenation methods on `CupOfTea` and `IDataFrame` (`concat`, `concatWith`, `concatHorizontal`) with support for varargs, lists, and pairs of DataFrames ([#89](https://github.com/filippo-pequini/CupOfTea/issues/89)).
- Automatic schema alignment by column name and configurable join semantics (`JoinType.OUTER` default padding with nulls/NA, `JoinType.INNER` retaining intersection) ([#89](https://github.com/filippo-pequini/CupOfTea/issues/89)).
- Columnar numeric type widening (e.g., Integer to Long, Double, or BigDecimal) and fallback to Object for heterogeneous types ([#89](https://github.com/filippo-pequini/CupOfTea/issues/89)).
- Horizontal concatenation with customizable disambiguation column suffixes (`leftSuffix`, `rightSuffix`) and automatic length padding/truncation based on join type ([#89](https://github.com/filippo-pequini/CupOfTea/issues/89)).
- Consistent preservation of index columns and strict compliance with `MutationMode` (`IN_PLACE` and `COPY_ON_WRITE`) without mutating inputs ([#89](https://github.com/filippo-pequini/CupOfTea/issues/89)).
- Dedicated unit test suite `ConcatTest` covering vertical, horizontal, multi-frame, type widening, empty frames, and error cases ([#89](https://github.com/filippo-pequini/CupOfTea/issues/89)).

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
