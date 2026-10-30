# CSV Module (`cupoftea-csv`)

The `cupoftea-csv` module handles high-performance delimited text ingestion and egress.
Starting in **v0.16.0** (and accelerated with SIMD in **v0.17.0**), CupOfTea features a native multithreaded chunk-based reader (`FastCsvReader`) with hardware-accelerated SIMD structural scanning (`SimdCsvScanner`), direct byte-level parsing, NIO memory-mapping, zero intermediate `String[]` allocations, and transparent fallback to [OpenCSV](https://opencsv.sourceforge.net/) for complex legacy quoting.

---

## 📦 Dependency

```xml
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-csv</artifactId>
    <version>0.17.0</version>
</dependency>
```

---

## ⚡ High-Performance Parallel & SIMD Ingestion (`FastCsvReader`)

By default, CupOfTea uses its native chunked parallel CSV reader:
- **Two-Stage SIMD Structural Scanner (`SimdCsvScanner`):** Uses the Java Vector API (`jdk.incubator.vector.ByteVector`, e.g., AVX-512, AVX2, or ARM NEON) with 4× loop unrolling, branchless parallel prefix-XOR quote parity resolution, and `TZCNT`/`BLSR` bit-scanning intrinsics to locate field and row boundaries at multi-GB/s throughput. Automatically falls back to the scalar byte scanner if the Vector API incubator module is not enabled at runtime.
- **Zero/Low Allocation:** Primitives (`int`, `long`, `double`, `float`, `boolean`) are parsed directly from raw byte buffers without intermediate `String` or boxing object creation.
- **Multithreaded Chunking:** Byte offsets are partitioned across available CPU cores via `ForkJoinPool.commonPool()` with quote-parity-aware chunk boundary alignment.
- **NIO Memory-Mapping:** Uses `FileChannel` and `MappedByteBuffer` for large files to maximize OS page cache throughput.
- **Categorical De-duplication:** Automatically pools repeated categorical strings without redundant heap allocations.

### Enabling Hardware SIMD Acceleration

To unlock hardware SIMD vectorization (`ByteVector.SPECIES_PREFERRED`) at runtime, pass the incubator vector module flag to the JVM:

```bash
java --add-modules jdk.incubator.vector -jar your-app.jar
```

You can inspect runtime SIMD support programmatically via `SimdDetector`:

```java
import com.cupoftea.csv.fast.SimdDetector;

boolean supported = SimdDetector.isSupported();
String species = SimdDetector.getSpeciesDescription(); // e.g. "Species[byte, 16, S_128_BIT]" or "Species[byte, 32, S_256_BIT]"
```

### Basic Ingestion

```java
CupOfTea df = CupOfTea.read().csv("data/customers.csv");
```

### Advanced Options & Configuration

You can configure delimiters, header presence, null string tokens, schema parsing, and toggle the fast parallel engine or SIMD scanner:

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
config.setFastEngine(true);  // Enabled by default
config.setSimdEnabled(true); // Enabled by default (uses SIMD when available)
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
