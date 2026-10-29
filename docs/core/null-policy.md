# Missing-Value (NA) Model, Schema, and Null Policy

## 1. Missing Values (NA) vs. Empty String

CupOfTea introduces a first-class missing-value model (`com.cupoftea.table.NA`) representing absent or missing data, distinct from empty strings (`""`).

| Concept | Representation | Meaning | Example |
|---|---|---|---|
| **Missing Value** | `null` or `NA.NA` | Value is absent, missing, or unknown | Unpopulated cell, `"NA"`, `"null"`, `null` |
| **Empty String** | `""` | A present, valid String of length 0 | User entered empty text `""` |
| **NaN** | `Double.NaN`, `Float.NaN` | Not-a-Number (treated as missing by `NA.isNA`) | `0.0 / 0.0` |

### Testing for Missing Values
Use `NA.isNA(value)` or `NA.isMissing(value)`:
```java
NA.isNA(null);          // true
NA.isNA(NA.NA);         // true
NA.isNA(Double.NaN);    // true
NA.isNA("");            // false (empty string is NOT missing)
NA.isValue("");         // true
```

## 2. Ingest Policies

### CSV Ingest
- **NA Strings**: By default, values matching `"NA"`, `"N/A"`, `"null"`, `"NULL"`, or `"#N/A"` are parsed as `null` (missing).
- **Empty String Preservation**: For string columns, empty string values `""` are preserved as `""` rather than coerced to `null`.
- **Configurable**: Use `CSVImporterConfiguration.setNaStrings(...)` and `setPreserveEmptyStrings(boolean)`.

### JSON Ingest
- `JSONObject.NULL` and missing JSON fields normalize to `null`.
- JSON string `""` is preserved as `""`.
- Primitives (`Integer`, `Long`, `Double`, `Boolean`) are preserved.

### Excel Ingest
- Blank cells (`CellType.BLANK`), error cells, or unpopulated cells become `null`.
- String cells containing `""` remain `""`.
- Boolean cells (`CellType.BOOLEAN`) are read directly as `Boolean`.
- Date-formatted numeric cells (`DateUtil.isCellDateFormatted(cell)`) are read as `java.time.LocalDate` or `java.time.LocalDateTime`.

## 3. Operations and Aggregations

- **Sorting (`orderBy`)**: Missing values (`null`, `NA.NA`, `Double.NaN`) sort last by default (`Comparator.nullsLast`).
- **Aggregations (`sum`, `average`, `maximum`, `minimum`, `count`)**: Missing values are skipped (ignored), matching standard SQL and pandas behavior.

## 4. Explicit Schema

You can define schemas explicitly on construction or ingest:
```java
import static com.cupoftea.table.Schema.*;

Schema schema = Schema.of(
    "year", INT,
    "price", DOUBLE,
    "salary", DECIMAL,
    "created", DATE,
    "timestamp", INSTANT
);

CupOfTea df = new CupOfTea(schema);
df.importFromCsv(file, schema);
```

Available `DataType` constants on `Schema`:
- `INT` (`Integer.class`)
- `LONG` (`Long.class`)
- `DOUBLE` (`Double.class`)
- `FLOAT` (`Float.class`)
- `DECIMAL` / `BIG_DECIMAL` (`BigDecimal.class`)
- `BOOLEAN` (`Boolean.class`)
- `STRING` (`String.class`)
- `DATE` (`LocalDate.class`)
- `DATETIME` (`LocalDateTime.class`)
- `INSTANT` (`Instant.class`)
- `OBJECT` (`Object.class`)

## 5. Temporal Types: `java.time` vs Legacy `java.util.Date`

- Prefer `java.time.LocalDate`, `java.time.LocalDateTime`, and `java.time.Instant`.
- Legacy `java.util.Date` and `DateConverter` are deprecated. Converters support bidirectional conversion between `Date` and `java.time` classes.

## 6. Currency and Floating-Point Precision: `BigDecimal` vs `Double`

- `Double` and `Float` use IEEE 754 binary floating-point representation and are subject to representation noise (e.g. `0.1 + 0.2 != 0.3`).
- For currency, financial amounts, or exact decimal precision, use `BigDecimal` (`Schema.DECIMAL`).
- CupOfTea aggregations (`sum`, `average`, `maximum`, `minimum`) natively support `BigDecimal` with exact arithmetic.
