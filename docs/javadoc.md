# API Reference (Javadoc)

CupOfTea publishes aggregate Javadoc API references for all modules with every documentation build.

---

## 🔗 Browse Javadoc

The full Javadoc documentation generated across all modules (`cupoftea-core`, `cupoftea-csv`, `cupoftea-excel`, `cupoftea-json`, `cupoftea-parquet`, `cupoftea-html`, `cupoftea-yaml`, `cupoftea-xml`, and `cupoftea-all`) is published alongside this website:

👉 **[Open API Reference / Javadoc (Full Screen)](apidocs/index.html)**

---

## 🔍 Key Classes & Interfaces

| Class / Interface | Package | Description |
| :--- | :--- | :--- |
| `CupOfTea` | `com.cupoftea` | Primary high-level DataFrame facade |
| `IDataFrame` | `com.cupoftea.table.interfaces` | Core tabular interface |
| `Row` | `com.cupoftea.table` | Immutable typed row representation |
| `Schema` | `com.cupoftea.table` | DataFrame schema specification |
| `DataType` | `com.cupoftea.table` | Enumeration of supported data types |
| `ColumnVector` | `com.cupoftea.table.vector` | Primitive-backed columnar storage vector |
| `NA` | `com.cupoftea.table` | Missing-value utilities and constants |
| `MutationMode` | `com.cupoftea` | `IN_PLACE` vs `COPY_ON_WRITE` execution modes |
| `FormatReader` | `com.cupoftea.spi` | SPI interface for IO format readers |
| `FormatWriter` | `com.cupoftea.spi` | SPI interface for IO format writers |
| `Aggregations` | `com.cupoftea.table` | Aggregate calculation builder (`sum`, `average`, `count`, etc.) |
