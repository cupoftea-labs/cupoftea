# Installation Guide

CupOfTea is modular. You can import only the core engine and whichever IO format modules your application requires, or include the aggregator artifact `cupoftea-all`.

## Prerequisites

- **Java**: Java 17 LTS, Java 21 LTS, or later.
- **Build Tool**: Maven 3.8+ or Gradle 7+.

---

## Maven

### 1. Minimal Core

To use CupOfTea's in-memory columnar DataFrame engine without external IO dependencies:

```xml
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. Adding Format Modules

Add format modules as needed. The core engine dynamically discovers format readers and writers via `java.util.ServiceLoader`:

```xml
<!-- CSV Support (OpenCSV) -->
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-csv</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<!-- JSON Support (Jackson) -->
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-json</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<!-- Excel Support (.xlsx / .xls via Apache POI) -->
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-excel</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<!-- Parquet Support (Apache Parquet / Hadoop) -->
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-parquet</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<!-- HTML Table Ingestion (Jsoup) -->
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-html</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<!-- YAML Support (SnakeYAML) -->
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-yaml</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<!-- XML Support (Standard Java DOM) -->
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-xml</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 3. All-In-One Meta-Module

To include all format adapters in one go:

```xml
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-all</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

---

## Gradle

### Groovy DSL (`build.gradle`)

```groovy
dependencies {
    // Core Engine
    implementation 'io.github.cupoftea-labs:cupoftea-core:1.0-SNAPSHOT'

    // Add format modules as needed:
    implementation 'io.github.cupoftea-labs:cupoftea-csv:1.0-SNAPSHOT'
    implementation 'io.github.cupoftea-labs:cupoftea-json:1.0-SNAPSHOT'

    // Or import all modules:
    // implementation 'io.github.cupoftea-labs:cupoftea-all:1.0-SNAPSHOT'
}
```

### Kotlin DSL (`build.gradle.kts`)

```kotlin
dependencies {
    // Core Engine
    implementation("io.github.cupoftea-labs:cupoftea-core:1.0-SNAPSHOT")

    // Add format modules as needed:
    implementation("io.github.cupoftea-labs:cupoftea-csv:1.0-SNAPSHOT")
    implementation("io.github.cupoftea-labs:cupoftea-json:1.0-SNAPSHOT")

    // Or import all modules:
    // implementation("io.github.cupoftea-labs:cupoftea-all:1.0-SNAPSHOT")
}
```
