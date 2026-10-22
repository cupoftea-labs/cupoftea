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
- Read and write CSV, JSON, Excel, Parquet, and YAML files.
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
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Add optional format modules as needed (CSV, JSON, Excel, Parquet, HTML, YAML, XML):

```xml
<!-- Example: Add CSV and JSON format support -->
<dependency>
    <groupId>com.cupoftea</groupId>
    <artifactId>cupoftea-csv</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.cupoftea</groupId>
    <artifactId>cupoftea-json</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<!-- Or import all formats with the aggregator module: -->
<!--
<dependency>
    <groupId>com.cupoftea</groupId>
    <artifactId>cupoftea-all</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
-->
```

### Gradle

**Groovy DSL (`build.gradle`):**

```groovy
// Core library
implementation 'com.cupoftea:cupoftea-core:1.0-SNAPSHOT'

// Optional format modules
implementation 'com.cupoftea:cupoftea-csv:1.0-SNAPSHOT'
implementation 'com.cupoftea:cupoftea-json:1.0-SNAPSHOT'

// Or all formats at once:
// implementation 'com.cupoftea:cupoftea-all:1.0-SNAPSHOT'
```

**Kotlin DSL (`build.gradle.kts`):**

```kotlin
// Core library
implementation("com.cupoftea:cupoftea-core:1.0-SNAPSHOT")

// Optional format modules
implementation("com.cupoftea:cupoftea-csv:1.0-SNAPSHOT")
implementation("com.cupoftea:cupoftea-json:1.0-SNAPSHOT")

// Or all formats at once:
// implementation("com.cupoftea:cupoftea-all:1.0-SNAPSHOT")
```

## Available Modules

| Module | Description | Dependencies / Formats |
| :--- | :--- | :--- |
| `cupoftea-core` | Core DataFrame engine, filtering, aggregation, SPI interfaces | Zero heavy external deps |
| `cupoftea-csv` | CSV format reader and writer | OpenCSV |
| `cupoftea-json` | JSON format reader and writer | Jackson Databind |
| `cupoftea-excel` | Excel format reader (.xlsx / .xls) | Apache POI 5.5.1 |
| `cupoftea-parquet` | Parquet format reader and writer | Apache Parquet / Hadoop |
| `cupoftea-html` | HTML table reader | Jsoup |
| `cupoftea-yaml` | YAML format reader and writer | SnakeYAML |
| `cupoftea-xml` | XML format reader | Standard Java XML DOM |
| `cupoftea-all` | Meta-module aggregating all format modules | All above formats |

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

## Roadmap
- Statistical operations (median, standard deviation, variance).
- Support for DataFrame join and merge operations.
- Window and rolling functions.

## Documentation
- [Missing-Value (NA) Model, Schema, and Null Policy](docs/null_policy.md)

## Contributions
Contributions are welcome! To contribute, follow these steps:

1. Fork the repository.
2. Create a branch for your feature (`git checkout -b my-feature`).
3. Commit your changes (`git commit -m 'Add new feature'`).
4. Push to the branch (`git push origin my-feature`).
5. Open a pull request.
  
## License

This project is licensed under the [Apache License, Version 2.0](LICENSE). See the [LICENSE](LICENSE) file for the full license text.
