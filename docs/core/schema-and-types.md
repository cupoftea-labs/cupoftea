# Schema & Data Types

CupOfTea features a strongly-typed schema model that defines the name and data type of every column in a DataFrame.

---

## 🏷️ Supported Data Types

The enum `com.cupoftea.table.DataType` specifies the supported logical types:

| DataType | Java Class / Representation | Internal ColumnVector | Notes |
| :--- | :--- | :--- | :--- |
| `INT` | `java.lang.Integer` | `IntColumnVector` (`int[]`) | 32-bit signed integer |
| `LONG` | `java.lang.Long` | `LongColumnVector` (`long[]`) | 64-bit signed integer |
| `DOUBLE` | `java.lang.Double` | `DoubleColumnVector` (`double[]`) | 64-bit floating point |
| `FLOAT` | `java.lang.Float` | `FloatColumnVector` (`float[]`) | 32-bit floating point |
| `BOOLEAN` | `java.lang.Boolean` | `BooleanColumnVector` (`boolean[]`) | Logical boolean |
| `STRING` | `java.lang.String` | `StringColumnVector` (`String[]`) | Text data |
| `DECIMAL` | `java.math.BigDecimal` | `ObjectColumnVector` | High-precision / financial calculations |
| `DATE` | `java.time.LocalDate` | `ObjectColumnVector` | Calendar dates (`YYYY-MM-DD`) |
| `DATETIME` | `java.time.LocalDateTime` | `ObjectColumnVector` | Date and time without timezone |
| `INSTANT` | `java.time.Instant` | `ObjectColumnVector` | UTC timestamp |
| `OBJECT` | `java.lang.Object` | `ObjectColumnVector` | Generic fallback |

---

## 🛠️ Defining a Schema

You can define a schema fluently using `Schema.of(...)`:

```java
import com.cupoftea.table.Schema;
import static com.cupoftea.table.Schema.*;

// Fluent varargs definition: name, type, name, type...
Schema schema = Schema.of(
    "id", INT,
    "product", STRING,
    "price", DOUBLE,
    "active", BOOLEAN,
    "created_at", DATETIME
);
```

Or using the `Schema.Builder`:

```java
Schema schema = Schema.builder()
    .add("employee_id", DataType.LONG)
    .add("salary", DataType.DECIMAL)
    .add("hire_date", DataType.DATE)
    .build();
```

---

## 🔄 Automatic Type Inference

When importing untyped data (such as CSV or JSON) without an explicit schema, CupOfTea performs automatic type inference:

1. Checks if all non-null values parse as integer (`INT` or `LONG`).
2. Checks for floating point (`DOUBLE`).
3. Checks for ISO date/time representations (`DATE`, `DATETIME`, `INSTANT`).
4. Checks for boolean flags (`true`, `false`).
5. Falls back to `STRING` if ambiguous or heterogeneous.

To bypass inference for predictable performance and strict validation, supply an explicit schema to the reader:

```java
CupOfTea df = CupOfTea.read()
    .schema(schema)
    .csv("large_dataset.csv");
```
