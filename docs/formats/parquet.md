# Parquet Module (`cupoftea-parquet`)

The `cupoftea-parquet` module integrates [Apache Parquet](https://parquet.apache.org/) columnar storage. This is ideal for large analytical datasets, data lake pipelines, and interoperability with tools like Apache Spark, DuckDB, or AWS Athena.

---

## 📦 Dependency

```xml
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-parquet</artifactId>
    <version>0.16.0</version>
</dependency>
```

---

## 📖 Reading Parquet Files

```java
CupOfTea df = CupOfTea.read().parquet("lake/events.parquet");
df.head(10).print();
```

---

## ✍️ Writing Parquet Files

```java
df.write().parquet("lake/processed_events.parquet");
```
