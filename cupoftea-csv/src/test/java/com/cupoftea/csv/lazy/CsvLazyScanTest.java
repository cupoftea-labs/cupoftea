package com.cupoftea.csv.lazy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.Aggregation;
import com.cupoftea.table.Aggregations;
import com.cupoftea.table.Schema;

public class CsvLazyScanTest {

    @TempDir
    File tempDir;

    private File create1BrcSampleFile() throws IOException {
        File file = new File(tempDir, "measurements_sample.txt");
        try (FileWriter fw = new FileWriter(file)) {
            // station;temperature
            fw.write("Hamburg;12.0\n");
            fw.write("Bulawayo;8.9\n");
            fw.write("Hamburg;14.5\n");
            fw.write("Palembang;38.8\n");
            fw.write("Bulawayo;10.1\n");
            fw.write("Hamburg;9.2\n");
            fw.write("Palembang;29.5\n");
            fw.write("Hamburg;15.0\n");
        }
        return file;
    }

    private File createStandardCsvFile() throws IOException {
        File file = new File(tempDir, "employees.csv");
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("name,dept,salary,age\n");
            fw.write("Alice,IT,5000.0,30\n");
            fw.write("Bob,HR,4000.0,35\n");
            fw.write("Charlie,IT,6000.0,28\n");
            fw.write("Dave,IT,5500.0,32\n");
            fw.write("Eve,HR,4500.0,40\n");
            fw.write("Frank,Sales,3000.0,25\n");
        }
        return file;
    }

    @Test
    @DisplayName("Should evaluate 1BRC-style query lazily through CupOfTea.scan().csv()")
    void test_1brc_style_lazy_scan() throws IOException {
        File file = create1BrcSampleFile();

        CupOfTea result = CupOfTea.scan()
                .delimiter(';')
                .hasHeaders(false)
                .schema(Schema.builder()
                        .add("station", String.class)
                        .add("temperature", Double.class)
                        .build())
                .csv(file)
                .groupBy("station")
                .agg(
                    Aggregations.min("temperature").as("min_temp"),
                    Aggregations.mean("temperature").as("mean_temp"),
                    Aggregations.max("temperature").as("max_temp")
                )
                .collect()
                .orderBy("station");

        assertThat(result.size()).isEqualTo(3);

        CupOfTea hamburg = result.filter(r -> "Hamburg".equals(r.get("station")));
        assertThat(((Number) hamburg.getRow(0).get("min_temp")).doubleValue()).isCloseTo(9.2, offset(1e-4));
        assertThat(((Number) hamburg.getRow(0).get("max_temp")).doubleValue()).isCloseTo(15.0, offset(1e-4));
        assertThat(((Number) hamburg.getRow(0).get("mean_temp")).doubleValue()).isCloseTo(12.675, offset(1e-4));

        CupOfTea bulawayo = result.filter(r -> "Bulawayo".equals(r.get("station")));
        assertThat(((Number) bulawayo.getRow(0).get("min_temp")).doubleValue()).isCloseTo(8.9, offset(1e-4));
        assertThat(((Number) bulawayo.getRow(0).get("max_temp")).doubleValue()).isCloseTo(10.1, offset(1e-4));
        assertThat(((Number) bulawayo.getRow(0).get("mean_temp")).doubleValue()).isCloseTo(9.5, offset(1e-4));
    }

    @Test
    @DisplayName("Should evaluate standard CSV with headers lazily")
    void test_standard_csv_with_headers_lazy_scan() throws IOException {
        File file = createStandardCsvFile();

        CupOfTea result = CupOfTea.scan()
                .csv(file)
                .groupBy("dept")
                .agg(
                    Aggregations.sum("salary").as("total_salary"),
                    Aggregations.mean("salary").as("avg_salary"),
                    Aggregations.count("salary").as("emp_count")
                )
                .collect()
                .orderBy("dept");

        assertThat(result.size()).isEqualTo(3);

        CupOfTea it = result.filter(r -> "IT".equals(r.get("dept")));
        assertThat(((Number) it.getRow(0).get("total_salary")).doubleValue()).isCloseTo(16500.0, offset(1e-4));
        assertThat(((Number) it.getRow(0).get("avg_salary")).doubleValue()).isCloseTo(5500.0, offset(1e-4));
        assertThat(((Number) it.getRow(0).get("emp_count")).longValue()).isEqualTo(3L);

        CupOfTea hr = result.filter(r -> "HR".equals(r.get("dept")));
        assertThat(((Number) hr.getRow(0).get("total_salary")).doubleValue()).isCloseTo(8500.0, offset(1e-4));
        assertThat(((Number) hr.getRow(0).get("avg_salary")).doubleValue()).isCloseTo(4250.0, offset(1e-4));
        assertThat(((Number) hr.getRow(0).get("emp_count")).longValue()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should support filtering in lazy scan pipeline")
    void test_filter_in_lazy_scan() throws IOException {
        File file = createStandardCsvFile();

        CupOfTea result = CupOfTea.scan()
                .csv(file)
                .filter(r -> "IT".equals(r.get("dept")))
                .select("name", "salary")
                .collect();

        assertThat(result.size()).isEqualTo(3);
        assertThat(result.getHeaders()).hasSize(2);
        assertThat(result.getHeaders().get(0).columnName()).isEqualTo("name");
        assertThat(result.getHeaders().get(1).columnName()).isEqualTo("salary");
    }

    @Test
    @DisplayName("Should support limit on lazy scan")
    void test_limit_lazy_scan() throws IOException {
        File file = createStandardCsvFile();

        CupOfTea result = CupOfTea.scan()
                .csv(file)
                .limit(2)
                .collect();

        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should count matching rows lazily")
    void test_count_lazy_scan() throws IOException {
        File file = createStandardCsvFile();

        long total = CupOfTea.scan().csv(file).count();
        assertThat(total).isEqualTo(6L);

        long itCount = CupOfTea.scan()
                .csv(file)
                .filter(r -> "IT".equals(r.get("dept")))
                .count();
        assertThat(itCount).isEqualTo(3L);
    }

    @Test
    @DisplayName("Should support convenience CupOfTea.scanCsv()")
    void test_scanCsv_convenience() throws IOException {
        File file = createStandardCsvFile();

        CupOfTea result = CupOfTea.scanCsv(file)
                .groupBy("dept")
                .agg(Aggregation.SUM.of("salary"))
                .collect();

        assertThat(result.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should evaluate multi-chunk large dataset in parallel with memory-mapped partitions")
    void test_multi_chunk_parallel_scan() throws IOException {
        File file = new File(tempDir, "large_measurements.txt");
        int rows = 20_000;
        try (FileWriter fw = new FileWriter(file)) {
            for (int i = 0; i < rows; i++) {
                String st = (i % 4 == 0) ? "Hamburg" : (i % 4 == 1) ? "Bulawayo" : (i % 4 == 2) ? "Palembang" : "Tokyo";
                double temp = 10.0 + ((i / 4) % 50) * 0.5;
                fw.write(st + ";" + temp + "\n");
            }
        }

        // Force smaller chunk size to trigger multi-partition parallel execution
        CupOfTea result = CupOfTea.scan()
                .delimiter(';')
                .hasHeaders(false)
                .chunkSize(64 * 1024) // 64 KB chunks
                .parallel(true)
                .schema(Schema.builder().add("station", String.class).add("temp", Double.class).build())
                .csv(file)
                .groupBy("station")
                .agg(
                    Aggregations.count("temp").as("cnt"),
                    Aggregations.min("temp").as("min_t"),
                    Aggregations.max("temp").as("max_t")
                )
                .collect()
                .orderBy("station");

        assertThat(result.size()).isEqualTo(4);

        for (int i = 0; i < 4; i++) {
            assertThat(((Number) result.getRow(i).get("cnt")).longValue()).isEqualTo(rows / 4);
            assertThat(((Number) result.getRow(i).get("min_t")).doubleValue()).isCloseTo(10.0, offset(1e-4));
            assertThat(((Number) result.getRow(i).get("max_t")).doubleValue()).isCloseTo(34.5, offset(1e-4));
        }
    }

    @Test
    @DisplayName("Should evaluate fused scan-aggregate with multi-column group keys")
    void test_fused_scan_multi_column_keys() throws IOException {
        File file = createStandardCsvFile();

        CupOfTea result = CupOfTea.scan()
                .csv(file)
                .groupBy("dept", "age")
                .agg(Aggregations.sum("salary").as("tot_sal"))
                .collect()
                .orderBy("dept", "age");

        assertThat(result.size()).isEqualTo(6);
    }

    @Test
    @DisplayName("Should evaluate fused scan-aggregate for global aggregations without group columns")
    void test_fused_scan_global_aggregation() throws IOException {
        File file = createStandardCsvFile();

        CupOfTea result = CupOfTea.scan()
                .csv(file)
                .agg(
                    Aggregations.sum("salary").as("total_sal"),
                    Aggregations.mean("salary").as("mean_sal"),
                    Aggregations.count("salary").as("count_sal")
                )
                .collect();

        assertThat(result.size()).isEqualTo(1);
        assertThat(((Number) result.getRow(0).get("total_sal")).doubleValue()).isCloseTo(28000.0, offset(1e-4));
        assertThat(((Number) result.getRow(0).get("mean_sal")).doubleValue()).isCloseTo(28000.0 / 6.0, offset(1e-4));
        assertThat(((Number) result.getRow(0).get("count_sal")).longValue()).isEqualTo(6L);
    }

    @Test
    @DisplayName("Should evaluate fused scan-aggregate on CSV with quoted fields")
    void test_fused_scan_quoted_csv() throws IOException {
        File file = new File(tempDir, "quoted_employees.csv");
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("\"dept\",\"salary\"\n");
            fw.write("\"Engineering\",\"10000.0\"\n");
            fw.write("\"Marketing\",\"8000.0\"\n");
            fw.write("\"Engineering\",\"12000.0\"\n");
        }

        CupOfTea result = CupOfTea.scan()
                .csv(file)
                .groupBy("dept")
                .agg(
                    Aggregations.sum("salary").as("total_sal"),
                    Aggregations.count("salary").as("cnt")
                )
                .collect()
                .orderBy("dept");

        assertThat(result.size()).isEqualTo(2);
        CupOfTea eng = result.filter(r -> "Engineering".equals(r.get("dept")));
        assertThat(((Number) eng.getRow(0).get("total_sal")).doubleValue()).isCloseTo(22000.0, offset(1e-4));
        assertThat(((Number) eng.getRow(0).get("cnt")).longValue()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should evaluate fused scan-aggregate with integer group keys")
    void test_fused_scan_integer_group_key() throws IOException {
        File file = createStandardCsvFile();

        CupOfTea result = CupOfTea.scan()
                .schema(Schema.builder()
                        .add("name", String.class)
                        .add("dept", String.class)
                        .add("salary", Double.class)
                        .add("age", Integer.class)
                        .build())
                .csv(file)
                .groupBy("age")
                .agg(Aggregations.sum("salary").as("total_sal"))
                .collect()
                .orderBy("age");

        assertThat(result.size()).isEqualTo(6);
    }
}
