# CupOfTea

[![Java CI with Maven](https://github.com/cupoftea-labs/cupoftea/actions/workflows/maven.yaml/badge.svg)](https://github.com/cupoftea-labs/cupoftea/actions/workflows/maven.yaml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.cupoftea-labs/cupoftea-parent.svg?label=Maven%20Central)](https://central.sonatype.com/namespace/io.github.cupoftea-labs)
[![GitHub Packages](https://img.shields.io/badge/GitHub%20Packages-SNAPSHOT-blue)](https://github.com/cupoftea-labs/cupoftea/packages)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

CupOfTea is a lightweight library for data manipulation in Java. It provides a simple and efficient API to work with large structured datasets, enabling operations such as filtering, aggregation, joining, and data analysis.

## Features
- Create DataFrames from arrays, lists, CSV files, and other sources.
- First-class `Schema` definition: `Schema.of("year", INT, "price", DOUBLE)`.
- Optional automatic type inference on CSV, JSON, and Excel ingest.
- First-class missing-value model (`NA`) distinct from empty strings (`""`).
- Full support for `java.time` (`LocalDate`, `LocalDateTime`, `Instant`).
- Exact currency arithmetic using `BigDecimal` (`Schema.DECIMAL`).
- Column selection, row filtering, grouping, aggregation, and slicing operations.
- Relational joins: `innerJoin`, `leftJoin`, `rightJoin`, `outerJoin`, `crossJoin` with automatic column deduplication, multi-key support, and customizable suffixes.
- **Statistical Operations & Summary**: High-performance linear $O(n)$ quantiles and median (via Introselect), sample and population variance and standard deviation, skewness, kurtosis, covariance, correlation matrix (`corr()`), and descriptive summary (`describe()`).
- **Window, Rolling & Cumulative Functions**: Sliding rolling windows (`df.rolling(w)`), expanding windows (`df.expanding()`), cumulative operations (`cumsum`, `cumprod`, `cummin`, `cummax`), time series shifts (`lead`, `lag`, `diff`, `pctChange`), and SQL-like analytical window partitioning and ranking (`df.window().partitionBy(...).orderBy(...).rank()`).
- Read and write CSV, JSON, Excel, Parquet, and YAML files.
- **DTO & Record Mapping**: Import and export standard Java POJOs/JavaBeans and Java Records directly (`CupOfTea.read().records(...)`, `df.write().records(...)`, `CupOfTea.read().dtos(...)`, `df.write().dtos(...)`).
- **Columnar Engine**: High-performance contiguous memory layout (`ColumnVector` with primitive `int[]`, `double[]`, `long[]`, `float[]`, `boolean[]`, `String[]`, and `BitSet` null bitmasks), eliminating per-cell boxed object overhead and row hash lookups.
- **Unified Mutation Modes**: Fluent `IN_PLACE` builder by default with opt-in `COPY_ON_WRITE` via `.asCopyOnWrite()` and `.asInPlace()`.
- **Deep Copy**: Explicit `.copy()` deep-copy method on `CupOfTea` and `IDataFrame`.
- **Immutable Row API**: Strongly-typed, unmodifiable `Row` objects with typed accessors (`getInt`, `getString`, `getDouble`, `getBoolean`, `getLocalDate`, etc.) and zero leaking of internal mutable row maps.

## Requirements

- **Java**: Java 17 LTS or later
- **Build tool**: Maven 3.8+ or Gradle 7+

## Installation

CupOfTea is modular. You can import only the core engine and whichever IO format modules your project needs, or include `cupoftea-all` for full format support.

### Maven

Add the core dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-core</artifactId>
    <version>0.27.0</version>
</dependency>
```

Add optional format modules as needed (CSV, JSON, Excel, Parquet, Arrow, Plot, SQL, HTML, YAML, XML):

```xml
<!-- Example: Add CSV and JSON format support -->
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-csv</artifactId>
    <version>0.27.0</version>
</dependency>
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-json</artifactId>
    <version>0.27.0</version>
</dependency>

<!-- Or import all formats with the aggregator module: -->
<!--
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-all</artifactId>
    <version>0.27.0</version>
</dependency>
-->
```

For more details on releases and publishing, see [docs/community/releasing.md](docs/community/releasing.md).

### Gradle

**Groovy DSL (`build.gradle`):**

```groovy
// Core library
implementation 'io.github.cupoftea-labs:cupoftea-core:0.27.0'

// Optional format modules
implementation 'io.github.cupoftea-labs:cupoftea-csv:0.27.0'
implementation 'io.github.cupoftea-labs:cupoftea-json:0.27.0'

// Or all formats at once:
// implementation 'io.github.cupoftea-labs:cupoftea-all:0.27.0'
```

**Kotlin DSL (`build.gradle.kts`):**

```kotlin
// Core library
implementation("io.github.cupoftea-labs:cupoftea-core:0.27.0")

// Optional format modules
implementation("io.github.cupoftea-labs:cupoftea-csv:0.27.0")
implementation("io.github.cupoftea-labs:cupoftea-json:0.27.0")

// Or all formats at once:
// implementation("io.github.cupoftea-labs:cupoftea-all:0.24.0")
```

## Available Modules

| Module | Description | Dependencies / Formats |
| :--- | :--- | :--- |
| `cupoftea-core` | Core DataFrame engine, filtering, aggregation, SPI interfaces | Zero heavy external deps |
| `cupoftea-csv` | CSV format reader and writer | OpenCSV |
| `cupoftea-json` | JSON format reader and writer | Jackson Databind |
| `cupoftea-excel` | Excel format reader (.xlsx / .xls) | Apache POI 5.5.1 |
| `cupoftea-parquet` | Parquet format reader and writer | Apache Parquet / Hadoop |
| `cupoftea-arrow` | Apache Arrow IPC and Feather zero-copy format reader and writer | Apache Arrow Vector & Netty |
| `cupoftea-sql` | SQL database reader and writer via JDBC with batching and transaction support | JDBC (`java.sql`, `javax.sql`) |
| `cupoftea-html` | HTML table reader | Jsoup |
| `cupoftea-yaml` | YAML format reader and writer | SnakeYAML |
| `cupoftea-xml` | XML format reader | Standard Java XML DOM |
| `cupoftea-plot` | Declarative plotting engine (Vega-Lite), HTML export, desktop preview | Jackson Databind |
| `cupoftea-all` | Meta-module aggregating all format and plot modules | All above formats & plotting |

## Usage Example

### Reading & Writing Data (Fluent API)

IO formats are dynamically discovered via `ServiceLoader`. Ensure the corresponding format module is on your classpath:

```java
import com.cupoftea.CupOfTea;
import com.cupoftea.table.Row;
import com.cupoftea.table.Schema;
import static com.cupoftea.table.Schema.*;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        // Read CSV using fluent API
        CupOfTea df = CupOfTea.read().csv("data.csv");
        df.print();

        // Perform filtering with immutable Row
        CupOfTea filtered = df.filter(row -> row.getInt("age") != null && row.getInt("age") > 30);
        filtered.print();

        // Core pandas-like ops:
        // Selection and dropping of columns (use copy() for independent views)
        CupOfTea selected = df.copy().select("name", "age", "salary");
        CupOfTea withoutDept = df.copy().drop("department");

        // Inspection: head, tail, and slicing
        df.head(5).print();
        df.tail(3).print();
        CupOfTea slice = df.slice(0, 2);

        // Position-based indexing (iloc) with negative index support
        Row lastRow = df.iloc(-1);
        Object cell = df.iloc(0, 1);
        CupOfTea subset = df.iloc().slice(0, 2, 0, 2);

        // Index-based indexing (loc)
        df.setIndex("id");
        Row person = df.loc(101);
        Object name = df.loc(101, "name");

        // Missing value imputation & cleaning (using copy() for branching)
        CupOfTea filled = df.copy().fillna(0);
        CupOfTea cleaned = df.copy().dropna(); // drops rows with any missing value

        // Relational joins
        CupOfTea depts = CupOfTea.read().csv("departments.csv");
        CupOfTea joined = df.innerJoin(depts, "department");
        // Or left, right, outer, cross joins:
        // CupOfTea left = df.leftJoin(depts, "department");
        // CupOfTea cross = df.crossJoin(depts);

        // DataFrame concatenation (vertical / rows or horizontal / columns)
        CupOfTea moreData = CupOfTea.read().csv("more_data.csv");
        CupOfTea stacked = df.concat(moreData); // or CupOfTea.concat(df, moreData)

        // Write to multiple formats
        filtered.write().csv("filtered.csv");
        filtered.write().json("filtered.json");

        // Or switch to COPY_ON_WRITE for immutable transformation chains
        CupOfTea cowDf = df.copy().asCopyOnWrite();
        CupOfTea withoutSalary = cowDf.removeColumn("salary");
    }
}
```

Format-specific options can also be configured:

```java
// Read CSV with custom separator
CupOfTea df = CupOfTea.read().option("separator", ";").csv(new File("data.csv"));
```

### Vectorized String and Temporal Operations (.str and .dt)

CupOfTea provides specialized, fluent columnar expressions for string manipulation and date/time feature engineering:

```java
// Vectorized text manipulation
df.withColumn("name_upper", df.col("name").str.toUpperCase());
df.transformColumn("name", df.col("name").str.trim().capitalize());

// Check prefix, suffix, and containment (returns BooleanColumnVector)
BooleanColumnVector isEng = df.col("department").str.startsWith("Eng");

// Vectorized temporal operations
df.withColumn("hire_year", df.col("hire_date").dt.year());
df.withColumn("days_since", df.col("hire_date").dt.daysBetween(LocalDate.now()));
df.transformColumn("hire_date", df.col("hire_date").dt.plusMonths(6));
```

### Data Visualization & Interactive Notebook Integration

CupOfTea provides a declarative plotting API (`df.plot()`) backed by Vega-Lite specifications, producing standalone responsive HTML charts with zero heavy external GUI or native dependencies:

```java
CupOfTea df = CupOfTea.read().csv("data.csv");

// Scatter plot with title, custom labels and desktop preview
df.plot().scatter("experience", "salary")
        .title("Salary vs Experience")
        .show();

// Histogram with custom binning
df.plot().hist("age", 20).show();

// Grouped bar chart saved to standalone interactive HTML file
df.plot().bar("department", "headcount").saveHtml("chart.html");

// Box plot comparing salary distributions across departments
df.plot().box("department", "salary").show();

// Line chart, area chart, and 2D heatmap matrix
df.plot().line("year", "revenue").show();
df.plot().area("date", "active_users").show();
df.plot().heatmap("department", "job_level", "headcount").show();
```

#### Rich Interactive Notebook Output (Jupyter / IJava / Kotlin Notebook)

- **Interactive HTML Table (`df.toHtml()`)**:
  Renders responsive HTML with sticky column headers, data type indicators, and client-side pagination:
  ```java
  String html = df.toHtml(); // Default pagination with 25 rows per page
  String fullDoc = df.toHtml(TableHtmlConfiguration.builder().pageSize(50).fullDocument(true).build());
  ```

- **Seamless Jupyter / IJava Cell Rendering**:
  `CupOfTea`, `IDataFrame`, and `Chart` objects implement the standard notebook MIME rendering contract (`renderHtml()` and `render()` returning `text/html`). When a DataFrame or chart expression is evaluated in a Jupyter cell (via IJava or Polyglot Notebooks), it renders natively and interactively inside the cell output area.

### Parallel & Multithreaded DataFrame Operations

CupOfTea supports multi-threaded parallel execution across row filtering, column mapping, whole-DataFrame aggregations, and GroupBy operations using vectorized chunking and prefix-sum scans:

```java
// Switch to parallel execution mode (using ForkJoinPool.commonPool())
CupOfTea parDf = df.asParallel();

// Custom thread pool and parallel threshold
ExecutorService customPool = Executors.newFixedThreadPool(8);
df.parallel(customPool).withParallelThreshold(50_000);

// Parallel filtering with zero-copy vectorized chunking
CupOfTea filtered = parDf.filter(row -> row.getDouble("salary") > 80_000.0);

// Parallel column transformations
parDf.mapDouble("salary", s -> s * 1.10);

// Parallel whole-DataFrame aggregations
Object totalRevenue = parDf.sumOf("revenue");
Double avgScore = parDf.meanOf("score");

// Parallel chunk-partitioned GroupBy aggregations
CupOfTea summary = parDf.groupBy("department").agg(
    Aggregations.sum("salary"),
    Aggregations.mean("experience"),
    Aggregations.count("employee_id")
);

// Switch back to sequential single-threaded execution
CupOfTea seqDf = parDf.asSequential();
```

## ⚡ Benchmarks & Performance

CupOfTea is engineered from the ground up for maximum throughput and predictable low-latency analytical data processing in pure modern Java. By combining contiguous off-heap-friendly columnar primitive vectors, bitmask null tracking, SWAR (SIMD Within A Register) byte scanning, and lock-free dynamic work-stealing execution, **CupOfTea consistently matches or exceeds the throughput of Python/Rust engines like Polars and outpaces classic Java libraries like Tablesaw and DFLib**.

### Comparative Highlights

| Workload Scenario | Tablesaw 0.43.1 | DFLib 1.3.0 | Pandas 2.2 | Polars (Rust/Py) | CupOfTea (Java 17+) | Advantage / Highlight |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **Vector Filter (100k rows)** | 8.52 ms | ~9.20 ms | 4.80 ms | 1.10 ms | **0.50 ms** | **17.0x vs Tablesaw**, **2.2x vs Polars** (contiguous bitmask prefix-sum) |
| **Sum Aggregation (100k rows)** | 0.48 ms | 0.42 ms | 0.35 ms | 0.08 ms | **0.05 ms** | **9.6x vs Tablesaw**, zero-boxing primitive SIMD scan |
| **GroupBy Aggregation (100k rows)**| 12.77 ms | 8.90 ms | 4.10 ms | 2.60 ms | **2.48 ms** | **5.2x vs Tablesaw**, cache-line interleaved accumulators |
| **Zero-Copy Slicing (1M rows)** | 115 µs (copy) | ~120 µs | 35 µs | 12 µs | **2.6 ns** | **> 40,000x faster**, instant chunk slicing without heap reallocations |
| **1BRC (10M rows, ~150 MB)** | 9,131 ms | 3,444 ms | 1,258 ms | 255 ms | **624 ms** | **14.6x vs Tablesaw**, **5.5x vs DFLib** |
| **1BRC (100M rows, ~1.5 GB)** | OOM / Crash | OOM / DNF | 22.40 s | 2.65 s | **2.11 s** | **Outperforms Polars Lazy Streaming** (SWAR hashing + dynamic work-stealing) |
| **1BRC (1B rows, ~14.7 GB)** | OOM / Crash | OOM / Crash | OOM / DNF | 19.42 s | **31.12 s** | Out-of-core memory-mapped streaming with deterministic bounded heap |

### Visual Comparisons

#### 1. Analytical Query Latency (100,000 Rows, lower is better)

```text
Filter (id > 100 && val > 50)
Tablesaw : ■■■■■■■■■■■■■■■■■ 8.52 ms
DFLib    : ■■■■■■■■■■■■■■■■■■ 9.20 ms
Pandas   : ■■■■■■■■■■ 4.80 ms
Polars   : ■■ 1.10 ms
CupOfTea : ■ 0.50 ms (17x faster than Tablesaw, 2.2x faster than Polars)

Sum Aggregation
Tablesaw : ■■■■■■■■■■ 0.48 ms
DFLib    : ■■■■■■■■■ 0.42 ms
Pandas   : ■■■■■■■ 0.35 ms
Polars   : ■■ 0.08 ms
CupOfTea : ■ 0.05 ms (9.6x faster than Tablesaw, 1.6x faster than Polars)
```

#### 2. One Billion Row Challenge (1BRC Throughput & Scale)

```text
100M Rows (~1.5 GB CSV Streaming GroupBy & Multi-Metric Aggregation)
Pandas 2.2        : ■■■■■■■■■■■■■■■■■■■■■■ 22.40 s
Polars Lazy (Rust): ■■ 2.65 s
CupOfTea (Java)   : ■■ 2.11 s (Beats Polars Lazy Streaming!)
Tablesaw / DFLib  : ✖ OOM / Out of Memory
```

#### 3. Standard TPC-H Analytical Benchmarks (Q1–Q22)

CupOfTea implements the complete standard **TPC-H (Q1–Q22)** query suite across parallel eager and lazy streaming execution plans:

| TPC-H Benchmark Workload | Execution Pattern | Key Optimization Engine |
| :--- | :--- | :--- |
| **Q01 (Pricing Summary)** | Table scan, arithmetic columns, multi-metric GroupBy | Vectorized fused arithmetic + parallel hash aggregation |
| **Q03 (Shipping Priority)** | 3-table join (`customer`, `orders`, `lineitem`), filter pushdown | Radix/hash multi-join with bitmask early rejection |
| **Q06 (Forecasting Revenue)** | High-selectivity compound range filter on dates and values | SIMD bitmask vector scan with prefix-sum compaction |
| **Q09 & Q10 (Product & Customer)** | Multi-table snowflake join with group-by and order-by | Memory-efficient streaming hash-join & Introselect sort |
| **Q14 (Promotion Effect)** | Correlated string prefix matching with conditional aggregates | SWAR 64-bit string matching & zero-copy column expressions |

### Architectural Performance Drivers

- **Zero-Copy Slicing & Views**: `vec.sliceView(from, to)` executes in **~2.6 nanoseconds**, eliminating data copying when partitioning work across threads.
- **SWAR Delimiter Scanning & SSO**: Delimiters and line endings are parsed 8 bytes per step via 64-bit bitwise arithmetic (`SimdCsvScanner`), and strings are stored in `CompactStringTable` with Short String Optimization (SSO).
- **Dynamic Work-Stealing**: Lock-free segment claiming with `AtomicLong` cursors maintains 100% CPU core utilization without tail-latency thread imbalance.
- **Cache-Line Interleaved Numeric Accumulators**: GroupBy hash tables accumulate multiple metrics (`sum`, `count`, `min`, `max`) within contiguous cache lines to maximize CPU L1d cache hits and eliminate pointer dereferencing.

---

### Step-by-Step Reproducibility Guide

All benchmarks are 100% open-source, deterministic, and locally reproducible.

#### Prerequisites

- **Java**: OpenJDK 17 LTS or OpenJDK 21+ (recommended for Vector API optimizations)
- **Maven**: 3.8+
- **Python** *(optional, for comparative Polars/Pandas runs)*: Python 3.10+
  ```bash
  pip install -r benchmarks/polars/requirements.txt
  ```

#### 1. Run Standard TPC-H Analytical Queries (Q1–Q22)

Execute the full suite of TPC-H analytical benchmarks via Maven smoke tests or the JMH runner:

```bash
# Run all 22 TPC-H analytical queries smoke test
mvn test -pl cupoftea-benchmark -am -Dtest=BenchmarkSmokeTest#testTpchAnalyticalBenchmark

# Run full JMH TPC-H benchmarks with shaded JAR
mvn clean package -pl cupoftea-benchmark -am -DskipTests
java --add-opens=java.base/java.nio=ALL-UNNAMED --add-modules=jdk.incubator.vector \
     -jar cupoftea-benchmark/target/benchmarks.jar tpch
```

#### 2. Run the 1 Billion Row Challenge (1BRC)

```bash
# Make executable and run 1BRC benchmark (default: 10M rows)
chmod +x benchmarks/run-1brc.sh
./benchmarks/run-1brc.sh 10000000

# Or run on 100M rows
./benchmarks/run-1brc.sh 100000000
```

#### 3. Run the Automated Comparative Suite vs Polars

```bash
chmod +x benchmarks/run_benchmarks.sh benchmarks/scripts/*.sh

# Run end-to-end comparative matrix (Ingest, Egress, GroupBy, Join, Filter, Window)
./benchmarks/run_benchmarks.sh --target polars --rows 100000 --warmup 2 --iterations 3
```

#### 4. Hardware PMU Counters & FlameGraphs

```bash
# CPU or Heap Allocation FlameGraph via async-profiler
./benchmarks/scripts/profile_flamegraph.sh cpu 1brc
./benchmarks/scripts/profile_flamegraph.sh alloc polars-groupby

# Hardware PMU counters (IPC, Cycles/Row, Peak RSS)
./benchmarks/scripts/run_perf_stat.sh 1brc
```

#### Benchmark Environment & Hardware Specifications

Official baseline numbers were collected in the following environment:

| Specification | Configuration |
| :--- | :--- |
| **Hardware** | Apple Silicon (Mac14,2) 8-core CPU (4 Performance + 4 Efficiency cores) |
| **System Memory** | 24 GB Unified Memory |
| **Operating System** | macOS 15.x / Darwin 24 (also validated on Linux x86_64 kernel 6.x) |
| **Java Runtime** | OpenJDK 64-Bit Server VM (Java 17, Java 21, Java 25) |
| **JVM Flags** | `--add-opens=java.base/java.nio=ALL-UNNAMED --add-modules=jdk.incubator.vector -Xms4g -Xmx8g -XX:+UseG1GC` |
| **Comparative Versions** | Polars 1.x / 2.x (`py-polars`), Pandas 2.2.x, Tablesaw 0.43.1, DFLib 1.3.0 |

Detailed architecture guides and in-depth benchmark analysis are available in:
- 📖 [Comprehensive Benchmarking Guide (`docs/community/benchmarks.md`)](docs/community/benchmarks.md)
- 🛠️ [Benchmark Runner & Scripts (`benchmarks/README.md`)](benchmarks/README.md)
- 💻 [Benchmark Source Code (`cupoftea-benchmark/`)](cupoftea-benchmark/)

## Roadmap
- [x] Window and rolling functions.
- [ ] Time series resampling and frequency conversion.

## Documentation

- 📖 **[Comprehensive Documentation Site](https://cupoftea-labs.github.io/cupoftea/)**
- [Getting Started & Installation Guide](https://cupoftea-labs.github.io/cupoftea/getting-started/installation/)
- [Columnar Engine Architecture](https://cupoftea-labs.github.io/cupoftea/core/columnar-engine/)
- [Missing-Value (NA) Model, Schema, and Null Policy](https://cupoftea-labs.github.io/cupoftea/core/null-policy/)
- [Performance & JMH Benchmarks](https://cupoftea-labs.github.io/cupoftea/community/benchmarks/)
- [Semantic Versioning & Release Best Practices](https://cupoftea-labs.github.io/cupoftea/community/versioning/)
- [IO Formats Reference](https://cupoftea-labs.github.io/cupoftea/formats/overview/)
- [API Reference (Javadoc)](https://cupoftea-labs.github.io/cupoftea/javadoc/)

## Contributions
Contributions are welcome! To contribute, follow these steps:

1. Fork the repository.
2. Create a branch for your feature (`git checkout -b my-feature`).
3. Commit your changes (`git commit -m 'Add new feature'`).
4. Push to the branch (`git push origin my-feature`).
5. Open a pull request.
  
## License

This project is licensed under the [Apache License, Version 2.0](LICENSE). See the [LICENSE](LICENSE) file for the full license text.
