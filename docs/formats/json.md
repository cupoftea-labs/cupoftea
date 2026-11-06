# JSON Module (`cupoftea-json`)

The `cupoftea-json` module provides high-performance zero-DOM streaming ingestion and lazy evaluation for JSON and NDJSON / JSON Lines datasets via [Jackson Databind](https://github.com/FasterXML/jackson).

---

## 📦 Dependency

```xml
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-json</artifactId>
    <version>0.25.0</version>
</dependency>
```

---

## 📖 Reading JSON Files (Streaming & Zero DOM Allocations)

The JSON reader parses tabular JSON tokens directly into typed `ColumnVector` instances without building intermediate DOM structures (`JsonNode` or `Map<String, Object>`):

```json
[
  {"id": 1, "name": "Ada Lovelace", "score": 98.4},
  {"id": 2, "name": "Alan Turing", "score": 99.1}
]
```

### Ingestion Example

```java
CupOfTea df = CupOfTea.read().json("records.json");
```

---

## ⚡ Lazy Scanning & NDJSON / JSON Lines (`scanJson`)

For out-of-core and large JSON or NDJSON / JSON Lines files, `CupOfTea.scan().json(...)` and `CupOfTea.scanJson(...)` provide lazy evaluation with projection pushdown, limit pushdown, and parallel chunked partition processing:

```java
// Lazy streaming query with projection pushdown and group-by aggregation
CupOfTea summary = CupOfTea.scanJson("large_events.jsonl")
    .select("user_id", "category", "amount")
    .groupBy("category")
    .agg(Aggregations.sum("amount").as("total_spent"), Aggregations.count("user_id").as("event_count"))
    .collect();
```

---

## ✍️ Writing JSON Files

Export the contents of your DataFrame to a formatted JSON array or NDJSON lines:

```java
// Standard compact or pretty JSON
df.write().json("output/records.json");

// NDJSON / JSON Lines
JsonExporterConfiguration config = JsonExporterConfiguration.builder()
    .mode(JsonExporterConfiguration.Mode.LINES);
df.write().json("output/records.jsonl", config);
```
