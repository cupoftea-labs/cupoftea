# Row & DataFrame API

The core data structures in CupOfTea are `CupOfTea` (fluent user facade), `IDataFrame` (contract implementation), and `Row` (immutable row projection).

---

## 📋 The `Row` API

A `Row` represents a single record in the tabular structure. In CupOfTea, rows are **strongly typed and immutable**.

### Typed Accessors

Rather than retrieving generic `Object` instances and casting manually, `Row` provides zero-risk typed getters:

```java
Row row = df.iloc(0);

// Typed getters:
String name           = row.getString("name");
Integer age           = row.getInt("age");
Long count            = row.getLong("count");
Double price          = row.getDouble("price");
Float weight          = row.getFloat("weight");
Boolean active        = row.getBoolean("active");
BigDecimal amount     = row.getBigDecimal("amount");
LocalDate date        = row.getLocalDate("date");
LocalDateTime dateTime = row.getLocalDateTime("dateTime");
Instant timestamp     = row.getInstant("timestamp");
```

### Missing Value Handling

If the cell value is null, missing (`NA.NA`), or Not-a-Number, typed getters safely return Java `null`:

```java
if (row.getInt("optional_code") == null) {
    // value is missing
}
```

---

## 📊 The `CupOfTea` Facade

The `CupOfTea` class wraps an underlying `IDataFrame` and provides pandas-like operations:

### Inspection Methods

```java
// Dimensions
int rowCount = df.rowCount();
int colCount = df.columnCount();
List<String> columns = df.getColumnNames();

// Pretty Print (stdout)
df.print();

// Preview
CupOfTea top5 = df.head(5);
CupOfTea bottom3 = df.tail(3);

// Slicing
CupOfTea middle = df.slice(10, 20); // [fromRow, toRow)
```

### Schema & Metadata

```java
Schema schema = df.getSchema();
DataType type = df.getColumnType("salary");
```

### Column Modifications

```java
// Add columns
df.addColumn("new_column", listOfValues);

// Drop columns
df.drop("obsolete_column");

// Rename column
df.renameColumn("old_name", "new_name");
```
