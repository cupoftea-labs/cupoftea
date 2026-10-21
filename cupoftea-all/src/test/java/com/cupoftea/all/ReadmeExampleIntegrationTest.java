package com.cupoftea.all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileWriter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.Row;

public class ReadmeExampleIntegrationTest {

    @TempDir
    File tempDir;

    @Test
    @DisplayName("Verify README usage example compiles and runs end-to-end")
    void testReadmeUsageExample() throws Exception {
        // Prepare sample CSV file
        File csvFile = new File(tempDir, "data.csv");
        try (FileWriter writer = new FileWriter(csvFile)) {
            writer.write("id,name,age,department,salary\n");
            writer.write("101,Alice,32,Engineering,75000\n");
            writer.write("102,Bob,28,Marketing,NA\n");
            writer.write("103,Charlie,45,Engineering,90000\n");
            writer.write("104,Diana,35,HR,60000\n");
        }

        // Read CSV using fluent API
        CupOfTea df = CupOfTea.read().csv(csvFile);
        assertThat(df.size()).isEqualTo(4);

        // Perform filtering with immutable Row
        CupOfTea filtered = df.filter(row -> row.getInt("age") != null && row.getInt("age") > 30);
        assertThat(filtered.size()).isEqualTo(3);

        // Selection and dropping of columns (using copy() for non-destructive operations)
        CupOfTea selected = df.copy().select("name", "age", "salary");
        assertThat(selected.getHeaders()).hasSize(3);

        CupOfTea withoutDept = df.copy().drop("department");
        assertThat(withoutDept.getHeaders()).hasSize(4);

        // Inspection: head, tail, and slicing
        CupOfTea head = df.head(5);
        CupOfTea tail = df.tail(3);
        CupOfTea slice = df.slice(1, 3);
        assertThat(head.size()).isEqualTo(4);
        assertThat(tail.size()).isEqualTo(3);
        assertThat(slice.size()).isEqualTo(2);

        // Position-based indexing (iloc) with negative index support
        Row lastRow = df.iloc(-1);
        assertThat(lastRow.getString("name")).isEqualTo("Diana");
        Object cell = df.iloc(0, 1);
        assertThat(cell).isEqualTo("Alice");
        CupOfTea subset = df.iloc().slice(0, 2, 0, 2);
        assertThat(subset.size()).isEqualTo(2);

        // Index-based indexing (loc)
        df.setIndex("id");
        Row person = df.loc(101);
        assertThat(person.getString("name")).isEqualTo("Alice");
        Object name = df.loc(101, "name");
        assertThat(name).isEqualTo("Alice");

        // Missing value imputation & cleaning (using copy() for independent demonstrations)
        CupOfTea filled = df.copy().fillna(0);
        assertThat(filled.getRow(1).get("salary")).isEqualTo(0);

        CupOfTea cleaned = df.copy().dropna();
        assertThat(cleaned.size()).isEqualTo(3);

        // Write to multiple formats
        File outCsv = new File(tempDir, "filtered.csv");
        File outJson = new File(tempDir, "filtered.json");
        filtered.write().csv(outCsv);
        filtered.write().json(outJson);
        assertThat(outCsv.exists()).isTrue();
        assertThat(outJson.exists()).isTrue();

        // Or switch to COPY_ON_WRITE for immutable transformation chains
        CupOfTea cowDf = df.copy().asCopyOnWrite();
        CupOfTea withoutSalary = cowDf.removeColumn("salary");
        assertThat(withoutSalary.getHeaders()).extracting("columnName").doesNotContain("salary");

        // Relational joins
        CupOfTea depts = new CupOfTea();
        depts.addColumn("department", String.class).addColumn("budget", Double.class);
        depts.addRow(java.util.Map.of("department", "Engineering", "budget", 500000.0));
        CupOfTea joined = df.innerJoin(depts, "department");
        assertThat(joined.size()).isEqualTo(2); // Alice and Charlie
    }
}
