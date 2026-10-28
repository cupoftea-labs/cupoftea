# GroupBy & Aggregations

CupOfTea features a flexible GroupBy engine that lets you segment data by one or more categorical columns and calculate summary metrics.

---

## 👥 Grouping Data

To start a grouped query, call `df.groupBy(...)`:

```java
import com.cupoftea.table.GroupBy;

GroupBy grouped = df.groupBy("department");
```

---

## 📈 Aggregation Functions

The `com.cupoftea.table.Aggregations` utility provides standard aggregate builders:

| Function | Description |
| :--- | :--- |
| `Aggregations.count()` | Count rows in the group |
| `Aggregations.sum("col")` | Numeric sum |
| `Aggregations.average("col")` | Arithmetic mean |
| `Aggregations.maximum("col")` | Maximum value |
| `Aggregations.minimum("col")` | Minimum value |

### Complete Example

```java
import com.cupoftea.CupOfTea;
import com.cupoftea.table.Aggregations;

CupOfTea df = CupOfTea.read().csv("employees.csv");

// Group by department and aggregate metrics
CupOfTea summary = df.groupBy("department")
    .aggregate(
        Aggregations.count().as("total_staff"),
        Aggregations.average("salary").as("avg_salary"),
        Aggregations.maximum("salary").as("highest_salary"),
        Aggregations.minimum("age").as("youngest_employee")
    );

summary.print();
```

---

## 🛡️ Null Values in Aggregations

Consistent with SQL and pandas semantics:
- Missing/null values are **ignored** when computing aggregations (`sum`, `average`, `min`, `max`).
- An all-null column group returns `null` or `0` for count.
