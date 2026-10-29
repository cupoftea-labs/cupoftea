# CSV Module (`cupoftea-csv`)

The `cupoftea-csv` module handles delimited text data using [OpenCSV](https://opencsv.sourceforge.net/).

---

## 📦 Dependency

```xml
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-csv</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

---

## 📖 Reading CSV Files

### Basic Ingestion

```java
CupOfTea df = CupOfTea.read().csv("data/customers.csv");
```

### Advanced Options & Configuration

You can configure delimiters, header presence, null string tokens, and schema parsing:

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
config.setSeparator(';');
config.setHasHeaders(true);
config.setNaStrings(List.of("NA", "N/A", "null", "EMPTY"));
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
