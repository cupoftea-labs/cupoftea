# XML Module (`cupoftea-xml`)

The `cupoftea-xml` module reads and writes hierarchical XML as flat DataFrames using the JDK's built-in StAX API (`XMLStreamReader` / `XMLStreamWriter`), with no third-party dependencies. Reading streams elements straight into columnar vectors, with no DOM tree.

---

## 📦 Dependency

```xml
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-xml</artifactId>
    <version>0.27.0</version>
</dependency>
```

---

## 📖 Reading XML Files

```xml
<records>
    <record>
        <id>1</id>
        <name>Alice</name>
    </record>
</records>
```

```java
CupOfTea df = CupOfTea.read().xml("dataset.xml");
```

Column types are inferred (Integer, Long, Double, Boolean, String). Attributes become `element.attribute` columns.

## ⚡ Lazy Scan

```java
CupOfTea result = CupOfTea.scanXml("big.xml")
        .select("id", "name")
        .limit(100)
        .collect();
```

Use `XmlImporterConfiguration.builder().rowElement("person").batchSize(50_000)` to choose the row tag and batch size.

## ✍️ Writing XML

```java
df.write().xml("out.xml");
df.exportToXml(new File("out.xml"));
String xml = new XmlExporter(df).exportToXml();
```

`XmlExporterConfiguration` controls root/row element names, indentation, charset, null text and buffer size. Output can go to a `File`, `OutputStream` or `Writer`.
