# Quickstart Tutorial

This tutorial introduces you to the core workflows of **CupOfTea**: creating DataFrames, reading files, filtering, selecting, aggregating, and writing data out.

---

## 1. Creating a DataFrame Programmatically

You can create a DataFrame from primitive arrays, Java collections, or custom schemas.

```java
import com.cupoftea.CupOfTea;
import com.cupoftea.table.Schema;
import java.util.List;

public class Quickstart {
    public static void main(String[] args) {
        // Initialize an empty DataFrame
        CupOfTea df = new CupOfTea();

        // Populate with columns
        df.addColumn("name", List.of("Alice", "Bob", "Charlie", "Diana"));
        df.addColumn("age", List.of(28, 34, 45, 22));
        df.addColumn("score", List.of(88.5, 92.0, 79.5, 95.0));

        // Pretty print tabular data to the console
        df.print();
    }
}
```

---

## 2. Reading Data Files

Using the fluent IO API `CupOfTea.read()`:

```java
import com.cupoftea.CupOfTea;
import com.cupoftea.table.Schema;

// Basic CSV ingestion with auto-inference
CupOfTea sales = CupOfTea.read().csv("data/sales.csv");

// With custom CSV options and explicit schema
CupOfTea custom = CupOfTea.read()
    .option("separator", ";")
    .schema(Schema.of("order_id", Schema.LONG, "price", Schema.DOUBLE, "sku", Schema.STRING))
    .csv("data/orders.csv");
```

---

## 3. Row Filtering

Filter rows using lambda predicates operating on strongly-typed, immutable `Row` objects:

```java
CupOfTea experienced = df.filter(row -> {
    Integer age = row.getInt("age");
    return age != null && age >= 30;
});

experienced.print();
```

---

## 4. Column Operations (Selection, Dropping, Renaming)

```java
// Select specific columns
CupOfTea basicInfo = df.copy().select("name", "score");

// Drop columns
CupOfTea withoutAge = df.copy().drop("age");

// Rename columns
df.renameColumn("score", "test_score");
```

> [!TIP]
> Notice the use of `.copy()`. By default, CupOfTea operates with mutable builder operations (`IN_PLACE`) for performance. Calling `.copy()` creates an independent snapshot before branching. Alternatively, switch the instance to `COPY_ON_WRITE` mode.

---

## 5. Position Indexing (`iloc`) and Label Indexing (`loc`)

### `iloc` (Integer Position)
Supports 0-based and Python-like negative indexing:

```java
// Get the last row
Row lastRow = df.iloc(-1);
System.out.println("Last person: " + lastRow.getString("name"));

// Get specific cell: iloc(row, col)
Object firstCell = df.iloc(0, 0);

// Slice rows: slice(startRow, endRow)
CupOfTea firstTwo = df.slice(0, 2);
```

### `loc` (Label / Index Value)
Set a key column as index to perform label lookups:

```java
df.setIndex("name");

// Look up row with index key "Alice"
Row alice = df.loc("Alice");
System.out.println("Alice's age: " + alice.getInt("age"));
```

---

## 6. GroupBy & Aggregations

Compute group summaries easily:

```java
import com.cupoftea.table.Aggregations;

CupOfTea departmentStats = employeeDf.groupBy("department")
    .aggregate(
        Aggregations.average("salary").as("avg_salary"),
        Aggregations.maximum("salary").as("max_salary"),
        Aggregations.count().as("headcount")
    );

departmentStats.print();
```

---

## 7. Writing to Multiple Formats

Write transformed DataFrames to disk using `CupOfTea.write()`:

```java
// Write to CSV
df.write().csv("output/report.csv");

// Write to JSON
df.write().json("output/report.json");

// Write to Parquet (if cupoftea-parquet is on the classpath)
df.write().parquet("output/report.parquet");
```
