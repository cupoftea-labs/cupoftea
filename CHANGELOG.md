# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.25.0] - 2026-11-05

### Added

- Streaming & Lazy JSON Scan (`JsonLazySource`, `JsonFormatScanner`, NDJSON/JSON Lines chunking) ([#300](https://github.com/cupoftea-labs/cupoftea/issues/300)):
  - Added low-level Jackson `JsonParser` direct streaming ingestion to columnar `ColumnVector` without intermediate DOM (`JsonNode`) allocations in `JsonFormatReader`.
  - Added high-performance chunked and parallel scanner for NDJSON / JSON Lines (`JsonLazySource`) supporting parallel partitions across CPU cores.
  - Added full integration with `LazyDataFrame` and `CupOfTea.scan().json(...)` / `CupOfTea.scanJson(...)` supporting column projection pushdown (`select`), limits (`head`/`limit`), and streaming aggregations.
  - Added comparative JSON / NDJSON ingestion benchmarks in `cupoftea-benchmark` (`PolarsJsonBenchmark`).

## [0.24.1] - 2026-11-05

### Fixed

- Exclude heavy internal benchmark artifacts (`benchmarks.jar` and `cupoftea-benchmark`) from GitHub Release assets upload to prevent release HTTP 500 timeouts ([#294](https://github.com/cupoftea-labs/cupoftea/issues/294)).

## [0.24.0] - 2026-11-05

### Added

- Implement standard TPC-H analytical queries (Q1-Q22) benchmark suite ([#284](https://github.com/cupoftea-labs/cupoftea/issues/284)):
  - Added deterministic synthetic TPC-H data generator (`TpchDataGenerator`) supporting scale factors SF 0.01, SF 0.1, SF 1, and beyond across all standard TPC-H relations (`lineitem`, `orders`, `customer`, `part`, `supplier`, `partsupp`, `nation`, `region`) with repeatable seeded PRNG.
  - Implemented the complete analytical query suite (`TpchAnalyticalBenchmark`, Q1 through Q22) covering star/snowflake multi-joins, correlated subqueries, multi-column group-by aggregations, and complex date/numeric predicates across parallel eager and lazy streaming execution plans.
  - Added comparative Polars benchmarks for key TPC-H queries in `benchmarks/polars/run_polars_benchmarks.py` using both Eager and Lazy Streaming (`scan_csv() -> collect()`) execution.
  - Extended `BenchmarkRunner` and `run_benchmarks.sh` with the `tpch` preset target for automated benchmarking and dataset generation.

## [0.23.0] - 2026-11-05

### Added

- Support Vectorized As-Of Join and add comparative benchmarks ([#283](https://github.com/cupoftea-labs/cupoftea/issues/283)):
  - Added temporal join engine `asOfJoin` to `IDataFrame`, `DataFrame`, and `CupOfTea` APIs supporting `asOfJoin(right, on)`, `asOfJoin(right, on, direction)`, `asOfJoin(right, on, by)`, and full multi-key/aliased variants.
  - Implemented `AsOfDirection` enum supporting `BACKWARD` (<= timestamp), `FORWARD` (>= timestamp), and `NEAREST` (closest timestamp with equidistant tie-break preference).
  - Optimized execution with specialized zero-boxing binary search and two-pointer pass over sorted primitive long/int/double vectors and partitioned by-group index lookup.
  - Added As-Of Join scenarios (`asOfJoinBackward`, `asOfJoinForward`, `asOfJoinNearest`) to `PolarsJoinBenchmark` JMH suite and comparative Python Polars benchmarks (`polars.join_asof()`).

## [0.22.0] - 2026-11-05

### Added

- Support Window Functions and comparative Polars window benchmarks ([#282](https://github.com/cupoftea-labs/cupoftea/issues/282)):
  - Added fluent convenience methods `rolling_mean(column, windowSize, outputColumn)` and `rolling_sum(column, windowSize, outputColumn)` directly on `CupOfTea` and `IDataFrame` to match Polars `rolling_*` expressions for fixed-size sliding windows.
  - Implemented `PolarsWindowBenchmark` JMH suite measuring latency and IPC across essential window operations: shift functions (`lag`, `lead`), cumulative functions (`cumsum`, `cumprod`, `cummin`, `cummax`), fixed-size rolling windows (`rolling_mean`, `rolling_sum`), and ranking operations (`row_number`, `rank`, `dense_rank`), as well as partitioned analytical window operations (`window().partitionBy().orderBy()`).
  - Added corresponding Polars benchmark implementations in `benchmarks/polars/run_polars_benchmarks.py` evaluating `pl.col().over()` and `pl.col().rolling_*()`.
  - Added `polars-window` and `window` preset patterns to `BenchmarkRunner` and orchestrator `run_benchmarks.sh`.

## [0.21.17] - 2026-11-05

### Added

- Arrow RecordBatch & C Data Interface zero-copy conversion and benchmark suite ([#281](https://github.com/cupoftea-labs/cupoftea/issues/281)):
  - Implemented direct bulk buffer memory transfers in `ArrowVectorMapper` between Arrow off-heap memory buffers (`IntVector`, `BigIntVector`, `Float8Vector`, `Float4Vector`, `VarCharVector`) and CupOfTea columnar vectors (`IntColumnVector`, `LongColumnVector`, `DoubleColumnVector`, `FloatColumnVector`, `StringColumnVector`), avoiding intermediate JVM arrays or per-element boxing.
  - Added zero-copy `StringColumnVector` conversion using contiguous byte and offset buffers, eliminating per-row `byte[]` allocations and `String` instantiations.
  - Implemented bidirectional conversion methods for `VectorSchemaRoot`, `ArrowRecordBatch`, and Arrow C Data Interface (`ArrowArray` and `ArrowSchema`).
  - Added JMH comparative benchmark suite `PolarsArrowBenchmark` and integrated it with `BenchmarkRunner` preset flags (`polars-arrow`, `arrow`) and `BenchmarkSmokeTest`.
  - Added Arrow round-trip conversion benchmark tests in `benchmarks/polars/run_polars_benchmarks.py` comparing CupOfTea and Polars Arrow throughput.

## [0.21.16] - 2026-11-05

### Performance

- Optimize String Hash Join using dictionary keys and compact hashing ([#280](https://github.com/cupoftea-labs/cupoftea/issues/280)):
  - Implemented Short String Optimization (SSO) with 64-bit word register comparisons in `StringHashJoinIndex` for UTF-8 byte slice keys $\le 16$ bytes, using little-endian `VarHandle` word loading (`p1` and `p2`) and compact SWAR hashing via `StringColumnVector.hashShortWords`.
  - Replaced repetitive `Arrays.equals` byte slice comparisons during build uniqueness checks and probe phase with CPU register comparisons (`buildLengths[r] == pLen && bP1[r] == p1 && bP2[r] == p2`), reserving array equality checks only for strings $> 16$ bytes.
  - Added specialized zero-boxing join paths for mixed `DictionaryColumnVector` and `StringColumnVector` keys (`DictBuildStringProbeHashJoinIndex` and `StringBuildDictProbeHashJoinIndex`), avoiding general multi-column fallback overhead.
  - Reduced string hash join latencies across 1:1 and 1:N benchmarks (e.g. 100k `leftJoinString1toN` down to 0.82 ms, 500k `innerJoinString1to1` down to 12.60 ms, and 500k `leftJoinString1to1` down to 13.50 ms), beating Polars.

## [0.21.15] - 2026-11-04

### Performance

- Optimize DataFrame materialization from CSV/TSV LazyScan ([#279](https://github.com/cupoftea-labs/cupoftea/issues/279)):
  - Added vectorized columnar bulk concatenation in `DataFrameConcatenator.tryConcatColumnVectorDirect` for homogeneous columns, replacing row-by-row boxing/unboxing and intermediate String object instantiation with `System.arraycopy` of contiguous arrays.
  - Implemented direct contiguous UTF-8 byte array block copying and slice offset arithmetic for `StringColumnVector` concatenation, preallocating total byte storage and eliminating heap `java.lang.String` instances during partition merging.
  - Optimized `LazyCupOfTea.collectRows` during parallel partition scans by flattening parsed batch chunks directly into a single unified list, eliminating intermediate partition DataFrame copies.
  - Reduced Lazy CSV Scan latency from ~36.3 ms down to ~5.2 ms and Lazy TSV Scan latency from ~74.9 ms down to ~7.8 ms (matching/surpassing eager ingestion at ~8.0 ms), while decreasing memory allocation from ~1274 B/row down to ~372 B/row.

## [0.21.14] - 2026-11-04

### Performance

- Accelerate Parquet and CSV/TSV egress throughput ([#278](https://github.com/cupoftea-labs/cupoftea/issues/278)):
  - Parallelized Parquet columnar serialization across available CPU cores by chunking large DataFrames into multiple contiguous row groups encoded in parallel via `ExecutorService` / `ForkJoinPool` and flushed to `ParquetFileWriter`.
  - Optimized CSV/TSV egress with 2-digit radix-100 lookup tables in `FastByteArrayOutputStream` for `writeInt` and `writeLong`, eliminating division and reverse loops.
  - Implemented branchless fast-path decimal serialization for common fixed-point numbers (1-2 decimal places) in `FastByteArrayOutputStream.writeDouble`, bypassing expensive intermediate `Double.toString()` heap allocations.
  - Added zero-copy string UTF-8 byte slice streaming in `CSVExporter` for `StringColumnVector`, copying raw contiguous bytes directly into chunk buffers.
  - Reduced CSV egress latency from ~20.8 ms down to ~10.2 ms (matching / outperforming Polars 13.5 ms), and Parquet egress latency from ~83.4 ms down to ~29.5 ms.

## [0.21.13] - 2026-11-04

### Performance

- Direct off-heap columnar Parquet page decompression and lazy predicate pushdown ([#277](https://github.com/cupoftea-labs/cupoftea/issues/277)):
  - Implemented `NioChannelInputFile` using Java NIO `FileChannel` with direct `ByteBuffer` reads to eliminate `RandomAccessFile` and heap buffer copy bottlenecks.
  - Configured off-heap memory allocation and decompression (`DirectByteBufferAllocator.getInstance()` and `useOffHeapDecryptBuffer(true)`) in both `ParquetImporter` and `ParquetLazySource`.
  - Added statistics-based row group pruning in `ParquetLazySource.ParquetPartitionReader` to evaluate filter predicates against block-level min/max column chunk statistics and skip unmatching row groups before decompressing pages.
  - Implemented columnar vector batch decoding directly into unboxed contiguous arrays and zero-copy string UTF-8 byte buffers.
  - Maintained full roundtrip fidelity across all primitive types and dictionary encodings.

## [0.21.12] - 2026-11-04

### Performance

- Vectorized direct JSON streaming egress via Jackson `JsonGenerator` ([#273](https://github.com/cupoftea-labs/cupoftea/issues/273)):
  - Replaced full row map serialization via `ObjectMapper.writeValue(file, dataFrame.getRows())` with low-level `JsonGenerator` streaming in `JsonFormatWriter` and `JsonExporter`.
  - Reads directly from columnar vectors (`IntColumnVector`, `LongColumnVector`, `DoubleColumnVector`, `FloatColumnVector`, `BooleanColumnVector`, `DictionaryColumnVector`, `StringColumnVector`), eliminating intermediate `Row` maps, reflection overhead, and boxing allocations.
  - Pre-encodes field names with `SerializedString` and caches dictionary string entries for `DictionaryColumnVector`, speeding up egress by ~3x (100k rows egress from ~147 ms down to ~51 ms).
  - Introduced configurable JSON export modes (`COMPACT`, `PRETTY`, and `LINES` for NDJSON) via `JsonExporterConfiguration` and fluent API options (`df.write().json(file, config)` and `df.write().option("lines", true).json(file)`).
  - Added unit test coverage in `JsonExporterTest` covering compact, pretty, and newline-delimited JSON formats.

## [0.21.11] - 2026-11-04

### Performance

- Chunked parallel egress and zero-copy byte buffer streaming for CSV/TSV export ([#272](https://github.com/cupoftea-labs/cupoftea/issues/272)):
  - Implemented parallel chunk encoding in `CSVExporter` via `ParallelVectorEngine.executeRanges`, partitioning large DataFrames into contiguous row ranges across worker threads (`ForkJoinPool`).
  - Added `FastByteArrayOutputStream` direct byte accumulator with non-allocating integer and long byte serialization routines, avoiding intermediate `String` or `char[]` allocations.
  - Implemented zero-copy streaming of encoded chunk byte buffers directly into the destination `FileChannel` (`ByteBuffer.wrap`).
  - Maintained full compatibility with headers placement (`TOP`, `BOTTOM`, `NO_HEADER`), null replacements, and quote behaviors (`ALL`, `ALL_BUT_NUMBERS`, `ALL_BUT_NULL`, `STRING_ONLY`, `HEADERS_ONLY`, `NONE`).
  - Added unit test in `CSVExporterTest` verifying byte fidelity and correctness of parallel chunked egress.

## [0.21.10] - 2026-11-04

### Performance

- Native columnar Parquet export without Avro/Hadoop GenericRecord overhead ([#271](https://github.com/cupoftea-labs/cupoftea/issues/271)):
  - Rewrote `ParquetExporter` to stream data directly from primitive columnar vectors (`IntColumnVector`, `LongColumnVector`, `DoubleColumnVector`, `FloatColumnVector`, `BooleanColumnVector`, `StringColumnVector`, `DictionaryColumnVector`) into Parquet `ColumnWriter` pages via `ColumnChunkPageWriteStore` and `ParquetFileWriter`.
  - Eliminated row-by-row `GenericRecord` and `Row` map materialization, bypassing intermediate Avro serialization and avoiding millions of transient object allocations.
  - Leveraged `LocalOutputFile` and Snappy compression block encoding, reducing 100k-row Parquet export latency by ~20x (from ~1,495 ms down to ~76 ms).
  - Added comprehensive test coverage in `ParquetExporterTest` validating primitive vectors, null values, dictionary encodings, and roundtrip data fidelity.

## [0.21.9] - 2026-11-04

### Performance

- Vectorized columnar file creation/export optimization and Polars egress benchmark suite ([#269](https://github.com/cupoftea-labs/cupoftea/issues/269)):
  - Optimized `CSVExporter` by replacing row-by-row `Row` map materialization, stream mapping, and `ArrayList<String>` heap allocations with direct columnar vector traversal (`ColumnVector` primitive accessors) and a reusable line buffer.
  - Implemented 64 KB buffered UTF-8 streaming output in `CSVExporter`, drastically cutting memory allocations and GC overhead during large exports.
  - Added comparative JMH benchmark suite `PolarsEgressBenchmark` measuring `writeCsv`, `writeTsv`, `writeParquet`, and `writeJson` across mixed datasets.
  - Added matching Polars egress benchmarks in `benchmarks/polars/run_polars_benchmarks.py` (`write_csv`, `write_parquet`, `write_json`) and wired egress dataset metrics in `generate_report.py`.
  - Added comprehensive unit tests in `CSVExporterTest` and `JsonExporterTest`.

## [0.21.8] - 2026-11-04

### Performance

- Columnar predicate pushdown and dictionary support in `ParquetLazySource` ([#265](https://github.com/cupoftea-labs/cupoftea/issues/265)):
  - Pushed filter predicates and projected columns directly into `ScanContext` and `ParquetLazySource`.
  - Added native Parquet dictionary support (`CapturingConverter`) for binary/string columns, constructing `DictionaryColumnVector` directly from page dictionaries and integer dictionary IDs without row-by-row UTF-8 decoding.
  - Implemented adaptive filter-first columnar scanning: evaluated filter predicates on filter-referenced columns first to construct a boolean `BitSet` mask, then skipped unmatched rows on payload columns using `ColumnReader.skip()` or applied vectorized filtering on columnar batches before row materialization.
  - Reduced `PolarsIngestBenchmark.ingestParquetLazyPushdown` (100k rows) latency from ~33.8 ms to ~17.1 ms, outperforming eager reading (~19.5 ms).

## [0.21.7] - 2026-11-04

### Performance

- Vectorized thread-local batch accumulation in `StreamingDirectAggregator` and hierarchical tree reduction ([#264](https://github.com/cupoftea-labs/cupoftea/issues/264)):
  - Implemented vectorized batch accumulation (`accumulateBatchDouble`) in `StreamingDirectAggregator` buffering primitive group keys and numeric metric values into cache-friendly chunks (512 values) in `DirectRowState`.
  - Replaced linear `CompactRunningGroupBy` state merge across partition readers with hierarchical tree reduction (`O(log P)` depth), eliminating serial reduction bottlenecks.
  - Reduced `PolarsIngestBenchmark.ingestMixedCsvLazyFusedAgg` (100k rows) execution latency from ~14 ms to ~4.1-4.4 ms (exceeding the target of < 6.5 ms and beating Polars).

## [0.21.6] - 2026-11-04

### Performance

- Adaptive chunk segmentation in `CsvLazySource` for multi-core parallelism ([#263](https://github.com/cupoftea-labs/cupoftea/issues/263)):
  - Implemented adaptive segment sizing in `CsvLazySource` based on total file size and available processors: for files < 32 MB, segments are dynamically scaled (`segmentSize = Math.max(256 * 1024, (int)(dataLen / procs))`), ensuring all processor threads are utilized during parallel scanning.
  - Retained fixed 4 MB bounds for files >= 32 MB and user-specified `chunkSize(...)` configurations.
  - Configured default scan chunk size to adapt dynamically across `DataFrameScanner`, `ScanOptions`, and `LazyCupOfTea`.
  - Aligned `ingestMixedCsvLazyScan` and `ingestMixedTsvLazyScan` latency with eager parallel reading.

## [0.21.5] - 2026-11-03

### Performance

- Multi-column CSV/TSV parser physical projection pushdown and parallel batch collection ([#256](https://github.com/cupoftea-labs/cupoftea/issues/256)):
  - Extended `ScanContext` and `StreamingDirectAggregator` with bitmask-based physical projection pushdown (`projectedColumnsMask`), allowing `SimdCsvScanner` and `FastCsvReader` to bypass parsing, numeric decoding, and string allocations for unprojected columns during direct streaming aggregations.
  - Unified `LazyCupOfTea.collectRows` to parallelize partition batch collection across available CPU cores when scanning without row-level filters or limits, bringing `ingestMixedCsvLazyScan` and `ingestMixedTsvLazyScan` execution time in line with eager parallel CSV reading.
  - Optimized type inference in `CsvLazySource` by reusing zero-allocation sampling from `FastCsvReader` when inferring numeric/general column types.

## [0.21.4] - 2026-11-03

### Performance

- Native Columnar Parquet Reader and Scanner with JDK 23+ compatibility and Projection Pushdown ([#257](https://github.com/cupoftea-labs/cupoftea/issues/257)):
  - Replaced `HadoopInputFile` and `HadoopOutputFile` with `LocalInputFile` and `LocalOutputFile` using `PlainParquetConfiguration`, completely bypassing Hadoop's `UserGroupInformation` and `Subject.getSubject()` to ensure full native support across JDK 21, 23, 25, and 27+.
  - Replaced row-by-row `AvroParquetReader` and `GenericRecord` decoding with direct vectorized columnar ingestion into primitive `ColumnVector` instances (`IntColumnVector`, `LongColumnVector`, `DoubleColumnVector`, `FloatColumnVector`, `BooleanColumnVector`, and `StringColumnVector`).
  - Added `ParquetLazySource` in `cupoftea-parquet` supporting column projection pushdown directly to `ParquetFileReader` so unneeded column chunks are never read from disk.

## [0.21.3] - 2026-11-03

### Performance

- Single-pass vectorized multivariate aggregations and online Welford standard deviation / variance ([#255](https://github.com/cupoftea-labs/cupoftea/issues/255)):
  - Implemented primitive online Welford accumulation (`WelfordVarianceAccumulator`) for sample/population `STD` and `VAR` maintaining `long count`, `double mean`, and `double m2` per group in a single pass.
  - Implemented parallel chunk reduction using Chan et al.'s exact combination formula ($\delta = \mu_B - \mu_A$, $M_{2,AB} = M_{2,A} + M_{2,B} + \delta^2 \frac{n_A n_B}{n_A + n_B}$) across thread partitions.
  - Enabled native vectorized single-pass evaluation for `Aggregations.std(...)` and `Aggregations.var(...)` in `GroupBy`, preventing degradation into object-oriented row materialization fallback during complex multivariate group operations.
  - Updated `CompactRunningGroupBy.StdAccumulator` to use online Welford mean and $M_2$ updating with Chan merging, eliminating catastrophic numerical cancellation.
  - Reduced `PolarsGroupByBenchmark.groupByMultivariateSinglePassParallel` (100,000 rows) execution time from ~50.28 ms to ~1.75 ms (~28.6x speedup) and reduced memory allocation from ~996 B/row to ~47.6 B/row (>20x reduction).

## [0.21.2] - 2026-11-03

### Performance

- Refactored `DataFrameJoiner` to a vectorized Columnar Hash Join engine with zero `Row`, `HashMap`, `Integer` boxing, or `String` materialization ([#254](https://github.com/cupoftea-labs/cupoftea/issues/254)):
  - Replaced `Map<Object, List<Integer>>` and `Row`-based build/probe/materialize loops with flat primitive chained hash tables (`int[] bucketHead` + `int[] nextRowIdx`) built in reverse row order to preserve ascending match emission without `ArrayList` allocations.
  - Added specialized zero-allocation fast-path indices: `IntHashJoinIndex` (`int[]`), `LongHashJoinIndex` (`long[]`), `StringHashJoinIndex` (direct UTF-8 `byte[]` slices + precomputed 64-bit SWAR `int[] buildHashes`), `DictionaryHashJoinIndex` (direct dictionary code lookup table), and `GeneralColumnarHashJoinIndex` (composite keys and cross-type numeric equivalence).
  - Implemented vectorized columnar Gather materialization across all `ColumnVector` implementations (`copyNullableIndexedValuesOnly`, `applyNullsFromNullableIndexed`, `computeNullableIndexedByteCount`, `copyNullableIndexedBytesLockFree`) and a unique-build-key fast-path (`executeLeftJoinUniqueRight`) for 1:1 / N:1 `LEFT` joins that clones left columns via `System.arraycopy`.
  - Reduced `PolarsJoinBenchmark` (100k rows) latency and allocation rate by ~30–45x: `innerJoinInt1to1` from ~55.0 ms (1,932 B/row) to ~1.21 ms (64.7 B/row), `leftJoinInt1to1` from ~60.1 ms (1,960 B/row) to ~1.46 ms (64.8 B/row), `innerJoinString1to1` from ~54.5 ms to ~1.70 ms (57.9 B/row), and `leftJoinString1to1` from ~56.3 ms to ~1.93 ms (58.0 B/row).

## [0.21.1] - 2026-11-03

### Performance

- Eliminated $O(M^2)$ quadratic complexity in `StringColumnVector` during parallel `DataFrame.filter(...)` and `ColumnVector.filterFromChunks(...)` ([#253](https://github.com/cupoftea-labs/cupoftea/issues/253)):
  - Implemented two-pass lock-free parallel chunk compaction (`computeIndexedByteCount`, `allocateForCompaction`, `copyIndexedBytesLockFree`) in `StringColumnVector` and `DataFrame.executeParallelFilterVectors`: computes per-chunk UTF-8 byte counts during Phase 1 predicate evaluation, computes prefix-sum byte offsets (`byteOffsets[c]`) in Phase 2, pre-allocates `new byte[totalBytes]` and `new int[totalMatched + 1]` once, and copies contiguous UTF-8 slices across worker threads in $O(M)$ without `synchronized` locks.
  - Replaced per-row `replaceRowBytes` calls in `StringColumnVector.copyIndexedValuesOnly` with a single slice-level bulk replacement over `[dstOffset .. dstOffset + count)`, avoiding repeated $O(M)$ tail array and offset shifts when populated sequentially by chunks.
  - Reduced `PolarsFilterVectorBenchmark.filterHighSelectivity90PctParallel` (100k rows) from ~3,190 ms to ~0.49 ms (>6,500x speedup), `filterMediumSelectivity50PctParallel` from ~905 ms to ~0.37 ms (>2,400x speedup), and `filterCompoundBooleanExpressionParallel` from ~2,480 ms to ~0.66 ms (>3,700x speedup).

## [0.21.0] - 2026-11-03

### Added

- Comparative Performance Benchmark Suite & Automated Harness (CupOfTea vs. Polars + 1BRC Standard Suite) ([#251](https://github.com/cupoftea-labs/cupoftea/issues/251)):
  - Added JMH comparative benchmark suites in `cupoftea-benchmark`: `PolarsIngestBenchmark` (CSV, TSV, Parquet eager & lazy pushdown), `PolarsGroupByBenchmark` (low/high cardinality, skewed Zipf, composite keys, single-pass multivariate), `PolarsJoinBenchmark` (Inner & Left joins across Int & UTF-8 String keys for 1:1, 1:N, N:M), `PolarsFilterVectorBenchmark` (1%, 50%, 90% selectivity, compound predicates, vectorized math), and `OneBrcStandardBenchmark` (1-decimal standard, 2-decimal cents, and hybrid 3-decimal dynamic fallback).
  - Extended `SyntheticDataGenerator` with deterministic PRNG dataset generation (`DEFAULT_SEED = 0xCAFEBABE12345678L`) and SHA-256 DataFrame result checksum verification (`computeChecksum`).
  - Added `ParquetFormatScanner` SPI provider in `cupoftea-parquet` enabling `CupOfTea.scan().parquet(...)` lazy pipeline scans.
  - Added `/benchmarks` automation harness (`run_benchmarks.sh`, `setup_env.sh`, `profile_flamegraph.sh`, `run_perf_stat.sh`, `generate_report.py`, and `polars/run_polars_benchmarks.py`) for cross-engine execution, `async-profiler` FlameGraphs, Linux `perf` PMU counters, and automated Markdown comparison reports.

## [0.20.8] - 2026-11-03

### Performance

- Contiguous cache-line interleaved accumulators in `StreamingDirectAggregator` ([#237](https://github.com/cupoftea-labs/cupoftea/issues/237)):
  - Introduced `InterleavedNumericAccumulator` using the Array-of-Structs (AoS) pattern to store group statistics contiguously as `[min, max, sum, count]` (32 bytes) fitting entirely within a single CPU cache line.
  - Eliminated virtual method dispatch loops across separate accumulator objects in hot paths (`accumulate1Brc`, `accumulate1BrcScaled`, and `accumulateFieldDouble/Long/Int/Scaled`).
  - Replaced scattered multi-array heap lookups (`mins[gid]`, `maxs[gid]`, `sums[gid]`, `counts[gid]`) with contiguous single-array updates updating all metrics simultaneously in CPU registers upon cache hit.
  - Added unit test suite `InterleavedNumericAccumulatorTest` validating interleaved group aggregations, fixed-point accumulation, and partition merging.

## [0.20.7] - 2026-11-02

### Performance

- Fast branchless integer and fixed-point decimal parsing in `FastByteParser` ([#230](https://github.com/cupoftea-labs/cupoftea/issues/230)):
  - Added specialized SWAR bitshift digit unpacking routines (`parseFixedPoint1`, `parseFixedPoint2`) using Quan Anh Mai's algorithm to parse 1- and 2-decimal numbers directly into scaled `long` integers without floating-point operations or branch mispredictions.
  - Implemented 64-bit parallel SWAR digit parsing in `FastByteParser.parseInt` for integers up to 8 digits with validation against non-digit ASCII bytes.
  - Extended `CompactRunningGroupBy` accumulators (`SumAccumulator`, `MeanAccumulator`, `MinAccumulator`, `MaxAccumulator`) to maintain scaled integer aggregations via `accumulateScaled`, scaling back to floating point only when computing final result vectors.
  - Integrated fixed-point fast paths into `StreamingDirectAggregator` (`accumulate1BrcScaled`) and `SimdCsvScanner` (1BRC memory-mapped direct execution).

## [0.20.6] - 2026-11-02

### Fixed

- Preserved column names defined in explicit `Schema` when `hasHeaders = false` in `CSVImporter`, `CsvOpenCsvPartitionReader`, and `ExcelImporter` ([#211](https://github.com/cupoftea-labs/cupoftea/issues/211)).

## [0.20.5] - 2026-10-30

### Performance

- Dynamic work-stealing partition execution with `AtomicLong` cursor in `CsvLazySource` and `CsvMmapPartitionReader` ([#229](https://github.com/cupoftea-labs/cupoftea/issues/229)):
  - Worker threads dynamically claim memory-mapped segments on demand using an `AtomicLong` cursor over the memory-mapped channel (`long current = cursor.addAndGet(SEGMENT_SIZE) - SEGMENT_SIZE`), eliminating tail latency caused by uneven page faults or I/O delays.
  - Workers continuously consume segments until the entire file is exhausted, keeping 100% CPU core utilization across all available cores.
  - Automatically aligns dynamic segment boundaries to line breaks without gaps or record duplication.

## [0.20.4] - 2026-10-30

### Performance

- SWAR 64-bit block hashing in `StringColumnVector.hashRow` and `StringColumnVector.hashBytes` ([#228](https://github.com/cupoftea-labs/cupoftea/issues/228)):
  - Reads 8 bytes at a time via little-endian `VarHandle` (`long`) with 64-bit prime multiplications and bit rotations instead of a scalar byte-by-byte loop.
  - Processes remaining tail bytes (1–7) with a bit-masked 64-bit read and Murmur3/xxHash-style 64-bit avalanche finalizer.
  - Applied consistently across `StringColumnVector.hashRow`, `CompactRunningGroupBy.CompactStringTable`, and `DictionaryColumnVector.fromStringVector`.

## [0.20.3] - 2026-10-30

### Performance

- Short String Optimization (SSO) in `CompactRunningGroupBy.CompactStringTable` ([#227](https://github.com/cupoftea-labs/cupoftea/issues/227)):
  - Each hash slot stores the key length and the first 16 key bytes packed into two 64-bit `long` prefixes.
  - Keys of up to 16 bytes are matched with length plus 1-2 primitive `long` comparisons; full byte-array comparison is used only for longer keys.

## [0.20.2] - 2026-10-30

### Performance

- SWAR (SIMD Within A Register) 64-bit delimiter / newline scanner in `FastByteParser` (`indexOfByte`, `indexOfStructural`), used by the scalar CSV fallback path to skip 8 bytes per step with zero incubator-module dependencies ([#226](https://github.com/cupoftea-labs/cupoftea/issues/226)).

## [0.20.1] - 2026-10-30

### Performance

- Fused Scan-Aggregate execution in `LazyCupOfTea` (`StreamingDirectAggregator`) to bypass intermediate DataFrame, ColumnVector, and StringChunkBuffer materialization ([#225](https://github.com/cupoftea-labs/cupoftea/issues/225)):
  - Memory-mapped CSV partition scanner streams parsed field values directly into open-addressing group hash tables and primitive accumulators on the fly during streaming aggregations (`CupOfTea.scan().csv(...).groupBy(...).agg().collect()`).
  - Added fast 1BRC SIMD execution path streaming station byte slices directly to group tables and temperature values directly to running accumulators with zero intermediate allocations.
  - Added explain plan annotation `Optimization: Fused Scan-Aggregate (StreamingDirectAggregator)`.

## [0.20.0] - 2026-10-30

### Added

- Streaming / Lazy DataFrame evaluation for out-of-core data processing (`CupOfTea.scan().csv(...)`, `CupOfTea.scanCsv(...)`, `df.lazy()`, `LazyCupOfTea`, `LazyDataFrame`, `LazyGroupBy`, `DataFrameScanner`, `FormatScanner`, `ScanOptions`, `LazySource`, `CompactRunningGroupBy`) ([#209](https://github.com/cupoftea-labs/cupoftea/issues/209)):
  - Out-of-core memory-mapped streaming partition execution (`CsvLazySource`, `CsvMmapPartitionReader`) processing multi-gigabyte datasets (e.g. 1BRC 1 billion rows / 14 GB) without materializing full datasets into memory.
  - Automatic line-aligned chunk partitioning with hardware-accelerated SIMD structural scanning and direct zero-copy columnar vector assembly (`FastCsvReader.assembleChunk`).
  - Compact running GroupBy engine (`CompactRunningGroupBy`) maintaining cache-resident running statistics (`min`, `mean`, `max`, `sum`, `count`, `first`, `last`, `std`, `var`) with zero heap allocation during hot scanning loops and parallel partition state reduction.
  - Lazy execution graph supporting stream filtering (`filter`), early projection (`select`, `drop`), early limit termination (`limit`, `head`), global aggregations (`agg`), out-of-core counting (`count`), and plan explanation (`explain`), finalized via `.collect()`.

## [0.19.0] - 2026-10-30

### Added

- Direct zero-copy ingestion of Categorical / Dictionary encoded columns in `FastCsvReader` and `SimdCsvScanner` ([#221](https://github.com/cupoftea-labs/cupoftea/issues/221)):
  - Added native `DictionaryChunkBuffer` mapping parsed UTF-8 byte slices directly to dictionary integer codes via local open-addressing byte slice hash tables during chunk parsing.
  - Eliminated intermediate `StringChunkBuffer` allocations and post-load `toDictionary()` conversions when columns are specified as `Schema.CATEGORICAL` or `Categorical.class`.
  - Parallel chunk dictionary merging in `assembleVectors`, building a unified global dictionary and remapping chunk codes across worker threads into `DictionaryColumnVector` and `CategoricalColumnVector`.

## [0.18.0] - 2026-10-30

### Added

- Dictionary / Categorical encoding for repeated string columns (`DictionaryColumnVector`, `CategoricalColumnVector`, `Categorical`, `DataType.CATEGORICAL`, `Schema.CATEGORICAL`, `Schema.DICTIONARY`, `CategoricalConverter`) ([#212](https://github.com/cupoftea-labs/cupoftea/issues/212)):
  - Unique string lookup array (`String[]`) paired with primitive integer code arrays (`int[]` or compact `short[]` with automatic promotion when cardinality exceeds `Short.MAX_VALUE`).
  - DataFrame and column conversion methods: `df.encodeColumn(col)`, `df.encodeColumns(cols...)`, `df.encodeCategoricalColumns(maxCardinalityRatio)`, `df.decodeColumn(col)`, `df.decodeAllDictionaryColumns()`, `StringColumnVector.toDictionaryColumnVector()`, and `ColumnRef.asCategorical()`.
  - Integer-code-accelerated grouping (`GroupBy` direct bucket array indexing for single and composite dictionary columns), joining (`DataFrameJoiner` unified dictionary code mapping), sorting (`compareRows`, `minIndexed`, `maxIndexed`), deduplication (`distinct`), value counts (`valueCounts`), and filtering (`eq`, `notEq`, `isin`, `filterByCategory`).
  - Dictionary-level $O(k)$ vectorized string operations (`StringAccessor` and `DictionaryColumnVector`: `toUpperCase`, `toLowerCase`, `trim`, `strip`, `replace`, `replaceRegex`, `substring`, `contains`, `startsWith`, `endsWith`, `matches`, `length`, `split`).

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
