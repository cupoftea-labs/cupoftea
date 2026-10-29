# Excel Module (`cupoftea-excel`)

The `cupoftea-excel` module provides ingestion for Microsoft Excel spreadsheets (`.xlsx` and legacy `.xls`) using [Apache POI](https://poi.apache.org/).

---

## 📦 Dependency

```xml
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-excel</artifactId>
    <version>1.0-SNAPSHOT</version>
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
