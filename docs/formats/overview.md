# IO Formats & Service Provider Interface (SPI)

CupOfTea uses a pluggable, modular IO architecture powered by `java.util.ServiceLoader`.

---

## 🧩 Architecture Overview

```mermaid
flowchart TD
    App[User Application: CupOfTea.read() / .write()] --> Engine[Core SPI FormatRegistry]
    Engine -->|Discovers via ServiceLoader| CSV[cupoftea-csv: CsvFormatReader / Writer]
    Engine -->|Discovers via ServiceLoader| JSON[cupoftea-json: JsonFormatReader / Writer]
    Engine -->|Discovers via ServiceLoader| Excel[cupoftea-excel: ExcelFormatReader]
    Engine -->|Discovers via ServiceLoader| Parquet[cupoftea-parquet: ParquetFormatReader / Writer]
    Engine -->|Discovers via ServiceLoader| HTML[cupoftea-html: HtmlFormatReader]
    Engine -->|Discovers via ServiceLoader| YAML[cupoftea-yaml: YamlFormatReader / Writer]
    Engine -->|Discovers via ServiceLoader| XML[cupoftea-xml: XmlFormatReader]
```

---

## 📦 Supported Formats Matrix

| Format | Module | Read Support | Write Support | Underlying Engine |
| :--- | :--- | :---: | :---: | :--- |
| **CSV** | `cupoftea-csv` | Yes | Yes | Native `FastCsvReader` (NIO/SIMD-ready) / OpenCSV fallback |
| **JSON** | `cupoftea-json` | Yes | Yes | Jackson Databind |
| **Excel** | `cupoftea-excel` | Yes (`.xlsx`, `.xls`) | In Progress | Apache POI |
| **Parquet** | `cupoftea-parquet` | Yes | Yes | Apache Parquet / Hadoop |
| **HTML** | `cupoftea-html` | Yes (Tables) | - | Jsoup |
| **YAML** | `cupoftea-yaml` | Yes | Yes | SnakeYAML |
| **XML** | `cupoftea-xml` | Yes | In Progress | Standard Java DOM |

---

## ⚙️ The Fluent Read & Write API

### Reading Files

```java
// Automatic detection or explicit format call
CupOfTea df1 = CupOfTea.read().csv("data.csv");
CupOfTea df2 = CupOfTea.read().json("data.json");
CupOfTea df3 = CupOfTea.read().excel("data.xlsx");
CupOfTea df4 = CupOfTea.read().parquet("data.parquet");

// Generic format invocation with options
CupOfTea df5 = CupOfTea.read()
    .format("csv")
    .option("separator", ";")
    .load("data.csv");
```

### Writing Files

```java
df.write().csv("output.csv");
df.write().json("output.json");
df.write().parquet("output.parquet");
df.write().yaml("output.yaml");
```

---

## 🛠️ Implementing a Custom Format Provider

You can add support for proprietary or new file formats without modifying CupOfTea's core codebase!

Simply implement `FormatReader` and/or `FormatWriter`:

```java
package com.example;

import com.cupoftea.spi.FormatReader;
import com.cupoftea.spi.ReadOptions;
import com.cupoftea.table.interfaces.IDataFrame;

public class MyCustomReader implements FormatReader {
    @Override
    public String format() {
        return "custom";
    }

    @Override
    public IDataFrame read(ReadOptions options) {
        // read file or input stream from options.getFile()
        // return populated IDataFrame
    }
}
```

Register your implementation in `META-INF/services/com.cupoftea.spi.FormatReader`.
CupOfTea will discover it automatically on classpath initialization.
