# JSON Module (`cupoftea-json`)

The `cupoftea-json` module parses and serializes JSON tabular datasets via [Jackson Databind](https://github.com/FasterXML/jackson).

---

## 📦 Dependency

```xml
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-json</artifactId>
    <version>0.16.0</version>
</dependency>
```

---

## 📖 Reading JSON Files

The JSON importer parses array-of-objects tabular payloads:

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

## ✍️ Writing JSON Files

Export the contents of your DataFrame to a formatted JSON array:

```java
df.write().json("output/records.json");
```
