# CupOfTea

CupOfTea is a lightweight library for data manipulation in Java. It provides a simple and efficient API to work with large structured datasets, enabling operations such as filtering, aggregation, joining, and data analysis.

## Features
- Create DataFrames from arrays, lists, CSV files, and other sources.
- First-class `Schema` definition: `Schema.of("year", INT, "price", DOUBLE)`.
- Optional automatic type inference on CSV, JSON, and Excel ingest.
- First-class missing-value model (`NA`) distinct from empty strings (`""`).
- Full support for `java.time` (`LocalDate`, `LocalDateTime`, `Instant`).
- Exact currency arithmetic using `BigDecimal` (`Schema.DECIMAL`).
- Column selection, row filtering, grouping, aggregation, and slicing operations.
- Read and write CSV, JSON, Excel, Parquet, and YAML files.
- **Unified Mutation Modes**: Fluent `IN_PLACE` builder by default with opt-in `COPY_ON_WRITE` via `.asCopyOnWrite()` and `.asInPlace()`.
- **Deep Copy**: Explicit `.copy()` deep-copy method on `CupOfTea` and `IDataFrame`.
- **Immutable Row API**: Strongly-typed, unmodifiable `Row` objects with typed accessors (`getInt`, `getString`, `getDouble`, `getBoolean`, `getLocalDate`, etc.) and zero leaking of internal mutable row maps.

## Future Features
- Statistical and aggregation operations (mean, sum, standard deviation, etc.). (WIP)
- Support for join and merge operations on DataFrames.

## Installation (WIP)

### Maven
Add the following dependency to your pom.xml file :

```xml
<dependency>
    <groupId></groupId>
    <artifactId></artifactId>
    <version></version>
</dependency>
```

### Gradle:

```groovy
implementation ''
```

## Usage Example
Here’s a basic example of how to use the Java DataFrame library:

```java
import java.io.File;
import com.CupOfTea.CupOfTea;
import com.CupOfTea.table.Row;
import com.CupOfTea.table.Schema;
import static com.CupOfTea.table.Schema.*;

public class Main {
    public static void main(String[] args) {
        // Define an explicit schema
        Schema schema = Schema.of(
            "name", STRING,
            "age", INT,
            "salary", DECIMAL,
            "hireDate", DATE
        );

        // Import CSV with schema
        File file = new File("employees.csv");
        CupOfTea df = new CupOfTea(schema).importFromCsv(file, schema);
        df.print();

        // Filter returns a new DataFrame with matching rows
        CupOfTea filteredDf = df.filter(row -> row.getInt("age") > 30);
        filteredDf.print();

        // Typed row access
        Row firstRow = filteredDf.getRow(0);
        String name = firstRow.getString("name");
        int age = firstRow.getInt("age");

        // Or switch to COPY_ON_WRITE for immutable transformation chains
        CupOfTea cowDf = df.copy().asCopyOnWrite();
        CupOfTea withoutSalary = cowDf.removeColumn("salary");
    }
}
```

## Documentation
- [Missing-Value (NA) Model, Schema, and Null Policy](docs/null_policy.md)

## Contributions
Contributions are welcome! To contribute, follow these steps:

- Fork the repository.
- Create a branch for your feature (git checkout -b my-feature).
- Commit your changes (git commit -m 'Add new feature').
- Push to the branch (git push origin my-feature).
- Open a pull request.
  
## License (WIP)
This project is licensed under the ... License. See the LICENSE file for more details.
