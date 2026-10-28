# Mutation Modes & Immutability

CupOfTea supports two distinct mutation strategies: **In-Place (Mutable)** and **Copy-On-Write (Immutable Chains)**.

---

## ⚙️ Mutation Modes Overview

The enum `com.cupoftea.MutationMode` defines the behavior of transformation operations:

```java
public enum MutationMode {
    IN_PLACE,
    COPY_ON_WRITE
}
```

### 1. `IN_PLACE` (Default)
In default mode, operations such as adding a column, renaming a column, or replacing values modify the existing DataFrame instance directly.

**When to use:**
- High-throughput batch processing where allocating new DataFrame objects would cause unnecessary heap churn.
- Sequential ingestion or ETL pipelines where you don't need intermediate snapshots.

```java
CupOfTea df = CupOfTea.read().csv("data.csv"); // default: IN_PLACE

// Modifies df directly:
df.renameColumn("old_col", "new_col");
df.addColumn("calculated", newValues);
```

### 2. `COPY_ON_WRITE`
In copy-on-write mode, every mutating transformation returns a new DataFrame instance with the modifications applied, preserving the original DataFrame untouched.

**When to use:**
- Functional programming pipelines.
- Multi-branch exploratory data analysis where the base dataset must remain immutable.
- Concurrent read pipelines.

```java
CupOfTea baseDf = CupOfTea.read().csv("data.csv").asCopyOnWrite();

// baseDf is never mutated; each operation returns a fresh DataFrame
CupOfTea branchA = baseDf.removeColumn("salary");
CupOfTea branchB = baseDf.addColumn("bonus", bonusList);
```

---

## 🔀 Switching Modes

You can freely toggle between modes using fluent helper methods:

```java
CupOfTea cow = df.asCopyOnWrite();
CupOfTea inPlace = cow.asInPlace();
```

---

## 📑 Explicit Deep Copies (`.copy()`)

Regardless of mutation mode, you can always take an explicit, independent deep copy of any `CupOfTea` or `IDataFrame` instance:

```java
CupOfTea independentClone = df.copy();
```

Modifying `independentClone` will never affect the source `df`.
