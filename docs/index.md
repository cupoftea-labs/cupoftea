# CupOfTea ☕

**CupOfTea** is a lightweight, modular, and high-performance columnar data manipulation library for Java 17+.

It provides a rich, pandas-inspired fluent API for filtering, aggregation, slicing, indexing, and tabular data transformation across various file formats—all while keeping memory consumption low through primitive columnar storage.

---

## ⚡ Highlights at a Glance

```mermaid
flowchart LR
    A[Inputs: CSV / JSON / Excel / Parquet / HTML / YAML / XML] --> B[CupOfTea Core Engine]
    B --> C[ColumnVector Layout: int[], double[], String[]...]
    B --> D[Fluent Query & Transformation API]
    D --> E[Outputs / Exports / Analytics]
```

- 🚀 **High-Performance Columnar Engine**: Contiguous memory primitives (`int[]`, `double[]`, `long[]`, `float[]`, `boolean[]`, `String[]`) with `BitSet` null bitmasks, eliminating per-cell boxed object overhead.
- 📐 **First-Class Schema System**: Explicit data types (`INT`, `LONG`, `DOUBLE`, `FLOAT`, `BOOLEAN`, `STRING`, `DECIMAL`, `DATE`, `DATETIME`, `INSTANT`) or automatic type inference on ingest.
- 🎯 **Predictable Null & Missing-Value Semantics**: First-class `NA` representation cleanly distinguishing missing or undefined data from empty strings (`""`).
- 🔒 **Unified Mutation Modes**: Flexible in-place builder mode (`MutationMode.IN_PLACE`) by default for speed, alongside opt-in copy-on-write (`MutationMode.COPY_ON_WRITE`) and explicit `.copy()`.
- 🧩 **Zero-Bloat SPI Modularity**: Pick only the format modules you need, or include `cupoftea-all` to support everything.

---

## ☕ Quick Example

```java
import com.cupoftea.CupOfTea;
import com.cupoftea.table.Row;
import static com.cupoftea.table.Schema.*;

public class QuickExample {
    public static void main(String[] args) {
        // Read dataset
        CupOfTea df = CupOfTea.read().csv("sales_data.csv");

        // Filter and inspect
        CupOfTea highValue = df.filter(row -> row.getDouble("revenue") != null && row.getDouble("revenue") > 1000.0);
        highValue.head(5).print();

        // Write results to JSON
        highValue.write().json("high_value_sales.json");
    }
}
```

---

## 🧭 Navigation Guide

- **[Installation](getting-started/installation.md)**: Maven & Gradle setup for core and format modules.
- **[Quickstart Tutorial](getting-started/quickstart.md)**: Hands-on walk-through of essential operations.
- **[Columnar Engine](core/columnar-engine.md)**: Deep dive into the internal memory layout and vectors.
- **[Schema & Data Types](core/schema-and-types.md)**: Supported data types, converters, and inference.
- **[IO Formats & SPI](formats/overview.md)**: CSV, JSON, Excel, Parquet, HTML, YAML, and XML integration.
- **[API Reference (Javadoc)](javadoc.md)**: Aggregate Javadoc API documentation.
