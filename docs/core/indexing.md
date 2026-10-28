# Indexing: loc and iloc

CupOfTea provides pandas-inspired position-based (`iloc`) and label-based (`loc`) indexing mechanisms.

---

## 🔢 Position-Based Indexing: `iloc`

Use `iloc` when referencing rows and columns by their zero-based integer indices.

### Negative Indexing
Just like Python and pandas, negative integers count backwards from the end of the DataFrame:

```java
// Access the very last row
Row lastRow = df.iloc(-1);

// Access second-to-last row
Row secondToLast = df.iloc(-2);
```

### Cell Access
Retrieve a single cell by `(rowIndex, columnIndex)`:

```java
Object cell = df.iloc(0, 1);
```

### Rectangular Slicing
Extract a 2D subset specifying `(rowStart, rowEnd, colStart, colEnd)`:

```java
// Extract rows 0..9 and columns 0..2
CupOfTea subTable = df.iloc().slice(0, 10, 0, 3);
```

---

## 🏷️ Label-Based Indexing: `loc`

Use `loc` when rows are indexed by a specific key column (e.g., ID, SKU, or UUID).

### Setting the Index

```java
// Designate "user_id" as the index column
df.setIndex("user_id");
```

### Retrieving Rows by Index Key

```java
// Look up user with ID 4201
Row userRow = df.loc(4201);
System.out.println("User Name: " + userRow.getString("name"));
```

### Retrieving Specific Cell Value

```java
// df.loc(indexKey, columnName)
Object email = df.loc(4201, "email");
```
