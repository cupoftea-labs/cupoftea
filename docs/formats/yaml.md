# YAML Module (`cupoftea-yaml`)

The `cupoftea-yaml` module provides parsing and dumping capabilities for YAML tabular datasets using [SnakeYAML](https://bitbucket.org/snakeyaml/snakeyaml).

---

## 📦 Dependency

```xml
<dependency>
    <groupId>com.cupoftea</groupId>
    <artifactId>cupoftea-yaml</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

---

## 📖 Reading YAML Files

```java
CupOfTea df = CupOfTea.read().yaml("config_matrix.yaml");
df.print();
```

---

## ✍️ Writing YAML Files

```java
df.write().yaml("output/exported_matrix.yaml");
```
