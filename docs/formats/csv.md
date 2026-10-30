# CSV Module (`cupoftea-csv`)

The `cupoftea-csv` module handles high-performance delimited text ingestion and egress.
Starting in **v0.16.0**, CupOfTea features a native multithreaded chunk-based reader (`FastCsvReader`) with direct byte-level parsing, NIO memory-mapping, zero intermediate `String[]` allocations, and transparent fallback to [OpenCSV](https://opencsv.sourceforge.net/) for complex legacy quoting.

---

## 📦 Dependency

```xml
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-csv</artifactId>
    <version>0.16.0</version>
</dependency>
```

---

## ⚡ High-Performance Parallel Ingestion (`FastCsvReader`)

By default, CupOfTea uses its native chunked parallel CSV reader:
- **Zero/Low Allocation:** Primitives (`int`, `long`, `double`, `float`, `boolean`) are parsed directly from raw byte buffers without intermediate `String` or boxing object creation.
- **Multithreaded Chunking:** Byte offsets are partitioned across available CPU cores via `ForkJoinPool.commonPool()`.
- **NIO Memory-Mapping:** Uses `FileChannel` and `MappedByteBuffer` for large files to maximize OS page cache throughput.
- **Categorical De-duplication:** Automatically pools repeated categorical strings without redundant heap allocations.

### Basic Ingestion

```java
CupOfTea df = CupOfTea.read().csv("data/customers.csv");
```

### Advanced Options & Configuration

You can configure delimiters, header presence, null string tokens, schema parsing, and toggle the fast parallel engine:

```java
import com.cupoftea.importers.configurations.CSVImporterConfiguration;
import com.cupoftea.table.Schema;

// Option 1: Fluent options
CupOfTea df = CupOfTea.read()
    .option("separator", ";")
    .option("quoteChar", "\"")
    .option("hasHeaders", true)
    .csv("data/customers.csv");

// Option 2: Typed configuration object
CSVImporterConfiguration config = new CSVImporterConfiguration();
config.setDelimiter(';');
config.setHasHeaders(true);
config.setFastEngine(true); // Enabled by default
config.setNaStrings(Set.of("NA", "N/A", "null", "EMPTY"));
config.setPreserveEmptyStrings(true);

CupOfTea df2 = CupOfTea.read()
    .option("configuration", config)
    .csv("data/customers.csv");
```

---

## ✍️ Writing CSV Files

```java
import com.cupoftea.exporters.configurations.CSVExporterConfiguration;

// Simple export
df.write().csv("output/result.csv");

// Export with custom settings
CSVExporterConfiguration exportConfig = new CSVExporterConfiguration();
exportConfig.setSeparator(';');
exportConfig.setIncludeHeaders(true);

df.write()
    .option("configuration", exportConfig)
    .csv("output/result_custom.csv");
```
