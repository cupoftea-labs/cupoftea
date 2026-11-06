# Excel Module (`cupoftea-excel`)

The `cupoftea-excel` module provides ingestion for Microsoft Excel spreadsheets (`.xlsx` and legacy `.xls`) using [Apache POI](https://poi.apache.org/).

---

## 📦 Dependency

```xml
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-excel</artifactId>
    <version>0.17.0</version>
</dependency>
```

---

## 📖 Reading Excel Files

CupOfTea automatically inspects POI cell types (`NUMERIC`, `STRING`, `BOOLEAN`, `BLANK`, `FORMULA`), properly preserves date formatting (`LocalDate` / `LocalDateTime`), and normalizes empty or blank cells according to the library's null policy.

### Reading the First Sheet

```java
CupOfTea df = CupOfTea.read().excel("finance_report.xlsx");
```

### Reading a Specific Sheet by Name or Index

```java
import com.cupoftea.importers.configurations.ExcelImporterConfiguration;

ExcelImporterConfiguration config = new ExcelImporterConfiguration();
config.setSheetName("Q3_Metrics"); // or setSheetIndex(2)

CupOfTea q3 = CupOfTea.read()
    .option("configuration", config)
    .excel("quarterly_reports.xlsx");
```

---

## ⚡ Lazy Scan Pipelines

Stream and scan large Excel workbooks without materializing the full dataset into memory:

```java
CupOfTea summary = CupOfTea.scan().excel("massive_dataset.xlsx")
    .select("department", "salary")
    .collect();
```

---

## ✍️ High-Performance Streaming Excel Writing (SXSSF)

Export DataFrames directly to `.xlsx` using sliding window buffers (`SXSSFWorkbook`):

```java
// Fluent API write
df.write().excel("output_report.xlsx");

// Or using ExcelExporterConfiguration
import com.cupoftea.exporters.configurations.ExcelExporterConfiguration;

ExcelExporterConfiguration exportConfig = new ExcelExporterConfiguration()
    .setSheetName("Summary")
    .setRowAccessWindowSize(100)
    .setCompressTempFiles(true);

df.write().excel("output_report.xlsx", exportConfig);
```
