# Filtering & Sorting

CupOfTea provides intuitive methods for filtering and ordering records.

---

## 🔍 Row Filtering

Filtering takes a predicate lambda `IFilter` operating on an immutable `Row`:

```java
// Keep only adults with non-null salary over $50,000
CupOfTea highEarners = df.filter(row -> {
    Integer age = row.getInt("age");
    Double salary = row.getDouble("salary");
    return age != null && age >= 18 && salary != null && salary > 50000.0;
});
```

### Clean Handling of Missing Values (`dropna` & `fillna`)

```java
// Drop any row that contains at least one null / NA value
CupOfTea completeOnly = df.copy().dropna();

// Impute missing values with a default value
CupOfTea imputed = df.copy().fillna("N/A");
```

---

## ↕️ Sorting & Ordering

Sort rows by one or more columns with ascending or descending order.

```java
import com.cupoftea.utils.interfaces.Order;

// Sort ascending by age
CupOfTea sortedByAge = df.orderBy("age", Order.ASCENDING);

// Sort descending by score
CupOfTea topScorers = df.orderBy("score", Order.DESCENDING);
```

### Null Value Ordering
CupOfTea implements standard SQL/pandas null sorting conventions:
- By default, missing values (`null`, `NA.NA`, `Double.NaN`) are placed **last** (`Comparator.nullsLast`), ensuring clean summaries when examining top or bottom entries.
