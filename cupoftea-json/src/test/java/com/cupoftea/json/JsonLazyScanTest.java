package com.cupoftea.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cupoftea.CupOfTea;
import com.cupoftea.importers.configurations.JsonImporterConfiguration;
import com.cupoftea.lazy.LazyCupOfTea;
import com.cupoftea.table.Aggregations;
import com.cupoftea.table.Schema;

public class JsonLazyScanTest {

    @TempDir
    File tempDir;

    @Test
    @DisplayName("Should scan standard JSON array file lazily and collect results")
    void testScanJsonArrayFile() {
        File sample = new File("src/test/resources/testFiles/json_sample.json");
        if (!sample.exists()) {
            sample = new File("cupoftea-json/src/test/resources/testFiles/json_sample.json");
        }

        LazyCupOfTea lazy = CupOfTea.scan().json(sample);
        assertThat(lazy).isNotNull();

        CupOfTea df = lazy.collect();
        assertThat(df.size()).isEqualTo(4);
        assertThat(df.getColumnHeader("car").columnType()).isEqualTo(String.class);
        assertThat(df.getColumnHeader("year").columnType()).isEqualTo(Integer.class);
        assertThat(df.getRow(0).get("car")).isEqualTo("Mustang");
        assertThat(df.getRow(0).get("year")).isEqualTo(2019);
    }

    @Test
    @DisplayName("Should support column projection pushdown on JSON array scan")
    void testJsonProjectionPushdown() {
        File sample = new File("src/test/resources/testFiles/json_sample.json");
        if (!sample.exists()) {
            sample = new File("cupoftea-json/src/test/resources/testFiles/json_sample.json");
        }

        CupOfTea df = CupOfTea.scan()
                .json(sample)
                .select("car", "year")
                .collect();

        assertThat(df.size()).isEqualTo(4);
        assertThat(df.getHeaders()).hasSize(2);
        assertThat(df.getColumnHeader("color")).isNull();
        assertThat(df.getRow(0).get("car")).isEqualTo("Mustang");
        assertThat(df.getRow(0).get("year")).isEqualTo(2019);
    }

    @Test
    @DisplayName("Should support head / limit pushdown on JSON scan")
    void testJsonLimitPushdown() {
        File sample = new File("src/test/resources/testFiles/json_sample.json");
        if (!sample.exists()) {
            sample = new File("cupoftea-json/src/test/resources/testFiles/json_sample.json");
        }

        CupOfTea df = CupOfTea.scan()
                .json(sample)
                .limit(2)
                .collect();

        assertThat(df.size()).isEqualTo(2);
        assertThat(df.getRow(0).get("car")).isEqualTo("Mustang");
        assertThat(df.getRow(1).get("car")).isEqualTo("fusca");
    }

    @Test
    @DisplayName("Should scan NDJSON / JSON Lines file in parallel chunks and perform streaming aggregation")
    void testScanJsonLinesParallelChunked() throws IOException {
        File ndjsonFile = new File(tempDir, "large_records.jsonl");
        int totalRows = 5_000;

        try (FileWriter writer = new FileWriter(ndjsonFile)) {
            for (int i = 0; i < totalRows; i++) {
                String dept = (i % 3 == 0) ? "Engineering" : (i % 3 == 1) ? "Marketing" : "Sales";
                double salary = 50_000.0 + (i * 10.0);
                writer.write("{\"id\": " + i + ", \"dept\": \"" + dept + "\", \"salary\": " + salary + ", \"active\": " + (i % 2 == 0) + "}\n");
            }
        }

        JsonImporterConfiguration config = JsonImporterConfiguration.builder()
                .lines(true)
                .batchSize(1024);

        // 1. Full scan collect
        CupOfTea df = CupOfTea.scan()
                .chunkSize(64 * 1024) // 64 KB segments to force multiple parallel chunks
                .json(ndjsonFile, config)
                .collect();

        assertThat(df.size()).isEqualTo(totalRows);
        assertThat(df.getColumnHeader("dept").columnType()).isEqualTo(String.class);
        assertThat(df.getColumnHeader("salary").columnType()).isEqualTo(Double.class);
        assertThat(df.getColumnHeader("active").columnType()).isEqualTo(Boolean.class);

        // 2. Projection pushdown
        CupOfTea projected = CupOfTea.scan()
                .chunkSize(64 * 1024)
                .json(ndjsonFile, config)
                .select("dept", "salary")
                .collect();

        assertThat(projected.size()).isEqualTo(totalRows);
        assertThat(projected.getHeaders()).hasSize(2);
        assertThat(projected.getColumnHeader("id")).isNull();
        assertThat(projected.getColumnHeader("active")).isNull();

        // 3. Lazy Group By aggregation
        CupOfTea agg = CupOfTea.scan()
                .chunkSize(64 * 1024)
                .json(ndjsonFile, config)
                .groupBy("dept")
                .agg(Aggregations.sum("salary").as("totalSalary"), Aggregations.count("id").as("cnt"))
                .collect();

        assertThat(agg.size()).isEqualTo(3);
        double totalSum = 0;
        for (int r = 0; r < agg.size(); r++) {
            totalSum += ((Number) agg.getRow(r).get("totalSalary")).doubleValue();
        }
        double expectedSum = 0;
        for (int i = 0; i < totalRows; i++) {
            expectedSum += (50_000.0 + (i * 10.0));
        }
        assertThat(totalSum).isEqualTo(expectedSum);
    }

    @Test
    @DisplayName("Should support CupOfTea.scanJson convenience static methods")
    void testScanJsonConvenienceMethods() {
        File sample = new File("src/test/resources/testFiles/json_sample.json");
        if (!sample.exists()) {
            sample = new File("cupoftea-json/src/test/resources/testFiles/json_sample.json");
        }

        CupOfTea df1 = CupOfTea.scanJson(sample.getPath()).collect();
        assertThat(df1.size()).isEqualTo(4);

        CupOfTea df2 = CupOfTea.scanJson(sample).collect();
        assertThat(df2.size()).isEqualTo(4);

        Schema explicit = Schema.of("car", Schema.STRING, "year", Schema.INT, "color", Schema.STRING);
        CupOfTea df3 = CupOfTea.scanJson(sample.getPath(), explicit).collect();
        assertThat(df3.getHeaders()).hasSize(3);
        assertThat(df3.getColumnHeader("car").columnType()).isEqualTo(String.class);
        assertThat(df3.getColumnHeader("year").columnType()).isEqualTo(Integer.class);
        assertThat(df3.getColumnHeader("color").columnType()).isEqualTo(String.class);
    }
}
