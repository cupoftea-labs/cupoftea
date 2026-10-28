# CupOfTea

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
    <groupId>com.cupoftea</groupId>
    <artifactId>cupoftea-core</artifactId>
    <version>0.6.0</version>
</dependency>
```

Add optional format modules as needed (CSV, JSON, Excel, Parquet, HTML, YAML, XML):

```xml
<!-- Example: Add CSV and JSON format support -->
<dependency>
    <groupId>com.cupoftea</groupId>
    <artifactId>cupoftea-csv</artifactId>
    <version>0.6.0</version>
</dependency>
<dependency>
    <groupId>com.cupoftea</groupId>
    <artifactId>cupoftea-json</artifactId>
    <version>0.6.0</version>
</dependency>

<!-- Or import all formats with the aggregator module: -->
<!--
<dependency>
    <groupId>com.cupoftea</groupId>
    <artifactId>cupoftea-all</artifactId>
    <version>0.6.0</version>
</dependency>
-->
```

### Gradle

**Groovy DSL (`build.gradle`):**

```groovy
// Core library
implementation 'com.cupoftea:cupoftea-core:0.5.0'

// Optional format modules
implementation 'com.cupoftea:cupoftea-csv:0.5.0'
implementation 'com.cupoftea:cupoftea-json:0.5.0'

// Or all formats at once:
// implementation 'com.cupoftea:cupoftea-all:0.5.0'
```

**Kotlin DSL (`build.gradle.kts`):**

```kotlin
// Core library
implementation("com.cupoftea:cupoftea-core:0.5.0")

// Optional format modules
implementation("com.cupoftea:cupoftea-csv:0.5.0")
implementation("com.cupoftea:cupoftea-json:0.5.0")

// Or all formats at once:
// implementation("com.cupoftea:cupoftea-all:0.5.0")
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
## Roadmap
- [x] Window and rolling functions.
- [ ] Time series resampling and frequency conversion.

## Documentation
- [Performance & JMH Benchmarks](docs/benchmarks.md)
- [Missing-Value (NA) Model, Schema, and Null Policy](docs/null_policy.md)
- [Semantic Versioning & Release Best Practices](docs/versioning.md)

## Contributions
Contributions are welcome! To contribute, follow these steps:

1. Fork the repository.
2. Create a branch for your feature (`git checkout -b my-feature`).
3. Commit your changes (`git commit -m 'Add new feature'`).
4. Push to the branch (`git push origin my-feature`).
5. Open a pull request.
  
## License

This project is licensed under the [Apache License, Version 2.0](LICENSE). See the [LICENSE](LICENSE) file for the full license text.
