# CupOfTea

CupOfTea is a lightweight library for data manipulation in Java. It provides a simple and efficient API to work with large structured datasets, enabling operations such as filtering, aggregation, joining, and data analysis.

## Features
- Create DataFrames from arrays, lists, CSV files, and other sources.
- Column selection, row filtering, and slicing operations.
- Read and write CSV files.

## Future Features
- Statistical and aggregation operations (mean, sum, standard deviation, etc.). (WIP)
- Support for join and merge operations on DataFrames.
- Handling of missing data (null values).

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
import ...;

public class Main {
    public static void main(String[] args) {
        File file = new File("csv_file.csv");
        CupOfTea df = new CupOfTea();
        df.importFromCsv(file, true);

        df.print();  
        CupOfTea filtered_df = df.filter(row -> row.getInt("age") > 30);
        filtered_df.print();
    }
}
```

## Documentation (WIP)
Full documentation of the library, including all functions and usage examples, can be found here.

## Contributions
Contributions are welcome! To contribute, follow these steps:

- Fork the repository.
- Create a branch for your feature (git checkout -b my-feature).
- Commit your changes (git commit -m 'Add new feature').
- Push to the branch (git push origin my-feature).
- Open a pull request.
  
## License (WIP)
This project is licensed under the ... License. See the LICENSE file for more details.
