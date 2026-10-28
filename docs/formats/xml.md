# XML Module (`cupoftea-xml`)

The `cupoftea-xml` module parses hierarchical XML data structures into flat 2D DataFrames using standard Java XML DOM parser implementations without third-party XML dependencies.

---

## 📦 Dependency

```xml
<dependency>
    <groupId>com.cupoftea</groupId>
    <artifactId>cupoftea-xml</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

---

## 📖 Reading XML Files

Given an XML file structure such as:

```xml
<records>
    <record>
        <id>1</id>
        <name>Alice</name>
        <status>active</status>
    </record>
    <record>
        <id>2</id>
        <name>Bob</name>
        <status>pending</status>
    </record>
</records>
```

Read into a DataFrame:

```java
CupOfTea df = CupOfTea.read().xml("dataset.xml");
df.print();
```
