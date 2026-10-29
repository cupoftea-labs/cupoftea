# HTML Module (`cupoftea-html`)

The `cupoftea-html` module allows extracting structured tabular data directly from HTML documents and web pages using [Jsoup](https://jsoup.org/).

---

## 📦 Dependency

```xml
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-html</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

---

## 📖 Reading HTML Tables

CupOfTea locates `<table>` elements within the HTML document and parses `<th>` headers and `<tr><td>` row records:

```java
CupOfTea df = CupOfTea.read().html("scraped_page.html");
df.print();
```
