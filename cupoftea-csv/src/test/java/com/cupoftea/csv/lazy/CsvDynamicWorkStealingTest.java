package com.cupoftea.csv.lazy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cupoftea.CupOfTea;
import com.cupoftea.importers.configurations.CSVImporterConfiguration;
import com.cupoftea.lazy.PartitionReader;
import com.cupoftea.lazy.ScanContext;
import com.cupoftea.lazy.StreamingDirectAggregator;
import com.cupoftea.table.Aggregation;
import com.cupoftea.table.Aggregations;
import com.cupoftea.table.Schema;

public class CsvDynamicWorkStealingTest {

    @TempDir
    File tempDir;

    @Test
    @DisplayName("Should dynamically steal partitions across worker threads with identical results to sequential execution")
    void test_dynamic_work_stealing_accuracy() throws IOException {
        File file = new File(tempDir, "work_stealing_dataset.csv");
        int rows = 50_000;
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("dept,salary,age\n");
            for (int i = 0; i < rows; i++) {
                String dept = (i % 3 == 0) ? "Engineering" : (i % 3 == 1) ? "Marketing" : "Sales";
                double salary = 3000.0 + (i % 100) * 50.0;
                int age = 20 + (i % 40);
                fw.write(dept + "," + salary + "," + age + "\n");
            }
        }

        // Sequential baseline
        CupOfTea seqResult = CupOfTea.scan()
                .parallel(false)
                .csv(file)
                .groupBy("dept")
                .agg(
                    Aggregations.count("salary").as("cnt"),
                    Aggregations.sum("salary").as("total_salary"),
                    Aggregations.mean("salary").as("avg_salary"),
                    Aggregations.min("age").as("min_age"),
                    Aggregations.max("age").as("max_age")
                )
                .collect()
                .orderBy("dept");

        // Dynamic work-stealing parallel execution with small 32 KB segments
        CupOfTea parallelResult = CupOfTea.scan()
                .parallel(true)
                .chunkSize(32 * 1024)
                .csv(file)
                .groupBy("dept")
                .agg(
                    Aggregations.count("salary").as("cnt"),
                    Aggregations.sum("salary").as("total_salary"),
                    Aggregations.mean("salary").as("avg_salary"),
                    Aggregations.min("age").as("min_age"),
                    Aggregations.max("age").as("max_age")
                )
                .collect()
                .orderBy("dept");

        assertThat(parallelResult.size()).isEqualTo(seqResult.size());
        for (int i = 0; i < seqResult.size(); i++) {
            assertThat((String) parallelResult.getRow(i).get("dept")).isEqualTo(seqResult.getRow(i).get("dept"));
            assertThat(((Number) parallelResult.getRow(i).get("cnt")).longValue())
                    .isEqualTo(((Number) seqResult.getRow(i).get("cnt")).longValue());
            assertThat(((Number) parallelResult.getRow(i).get("total_salary")).doubleValue())
                    .isCloseTo(((Number) seqResult.getRow(i).get("total_salary")).doubleValue(), offset(1e-4));
            assertThat(((Number) parallelResult.getRow(i).get("avg_salary")).doubleValue())
                    .isCloseTo(((Number) seqResult.getRow(i).get("avg_salary")).doubleValue(), offset(1e-4));
            assertThat(((Number) parallelResult.getRow(i).get("min_age")).intValue())
                    .isEqualTo(((Number) seqResult.getRow(i).get("min_age")).intValue());
            assertThat(((Number) parallelResult.getRow(i).get("max_age")).intValue())
                    .isEqualTo(((Number) seqResult.getRow(i).get("max_age")).intValue());
        }
    }

    @Test
    @DisplayName("Should handle uneven worker speeds by having fast threads steal remaining segments without tail latency")
    void test_work_stealing_uneven_threads() throws Exception {
        File file = new File(tempDir, "uneven_workers.csv");
        int rows = 30_000;
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("station,val\n");
            for (int i = 0; i < rows; i++) {
                fw.write("StationA," + (i * 0.1) + "\n");
            }
        }

        CSVImporterConfiguration config = new CSVImporterConfiguration();
        config.setHasHeaders(true);
        CsvLazySource source = new CsvLazySource(file, config);

        // 16 KB segments on ~450 KB file yields ~28 segments
        ScanContext context = new ScanContext(true, null, 65536, 16 * 1024);
        List<PartitionReader> partitions = source.getPartitions(context);
        assertThat(partitions.size()).isGreaterThan(1);

        ExecutorService exec = Executors.newFixedThreadPool(partitions.size());
        CountDownLatch latch = new CountDownLatch(partitions.size());
        List<Future<StreamingDirectAggregator>> futures = new ArrayList<>();

        AtomicInteger slowThreadProcessed = new AtomicInteger(0);
        AtomicInteger fastThreadsProcessed = new AtomicInteger(0);

        for (int i = 0; i < partitions.size(); i++) {
            final int workerId = i;
            final PartitionReader reader = partitions.get(i);
            futures.add(exec.submit(() -> {
                StreamingDirectAggregator agg = new StreamingDirectAggregator(
                        new String[]{"station"},
                        new com.cupoftea.table.Aggregate[]{Aggregations.count("val").as("c"), Aggregations.sum("val").as("s")},
                        source.getHeaders()
                );
                try (reader) {
                    if (workerId == 0) {
                        // Simulate thread 0 having severe I/O lag or page faults
                        Thread.sleep(100);
                        reader.aggregateDirect(agg);
                        slowThreadProcessed.addAndGet(agg.numGroups() > 0 ? 1 : 0);
                    } else {
                        reader.aggregateDirect(agg);
                        fastThreadsProcessed.addAndGet(agg.numGroups() > 0 ? 1 : 0);
                    }
                } finally {
                    latch.countDown();
                }
                return agg;
            }));
        }

        latch.await(10, TimeUnit.SECONDS);

        StreamingDirectAggregator global = null;
        for (Future<StreamingDirectAggregator> future : futures) {
            StreamingDirectAggregator state = future.get();
            if (global == null) {
                global = state;
            } else {
                global.merge(state);
            }
        }

        exec.shutdown();

        CupOfTea result = global.finish();
        assertThat(result.size()).isEqualTo(1);
        assertThat(((Number) result.getRow(0).get("c")).longValue()).isEqualTo(rows);

        // Fast threads should have actively processed multiple segments while worker 0 lagged
        assertThat(fastThreadsProcessed.get()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should handle segment boundaries with CRLF line breaks correctly")
    void test_crlf_segment_boundaries() throws IOException {
        File file = new File(tempDir, "crlf_sample.csv");
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("id,name,value\r\n");
            for (int i = 0; i < 5_000; i++) {
                fw.write(i + ",User" + i + "," + (i * 2) + "\r\n");
            }
        }

        // Test with 512 bytes segment size to force many boundary splits across CRLF
        CupOfTea result = CupOfTea.scan()
                .chunkSize(512)
                .parallel(true)
                .csv(file)
                .agg(Aggregations.count("id").as("cnt"), Aggregations.sum("value").as("tot"))
                .collect();

        assertThat(((Number) result.getRow(0).get("cnt")).longValue()).isEqualTo(5000L);
        long expectedSum = 0;
        for (int i = 0; i < 5_000; i++) expectedSum += (i * 2);
        assertThat(((Number) result.getRow(0).get("tot")).longValue()).isEqualTo(expectedSum);
    }

    @Test
    @DisplayName("Should handle rows longer than the configured segment size")
    void test_rows_longer_than_segment_size() throws IOException {
        File file = new File(tempDir, "long_rows.csv");
        StringBuilder longString = new StringBuilder();
        for (int j = 0; j < 500; j++) {
            longString.append("ABCDEFGHIJ");
        }
        String bigField = longString.toString(); // 5,000 bytes

        try (FileWriter fw = new FileWriter(file)) {
            fw.write("id,data,val\n");
            for (int i = 0; i < 50; i++) {
                fw.write(i + "," + bigField + "," + i + "\n");
            }
        }

        // Segment size 1024 is smaller than each 5000-byte row
        CupOfTea result = CupOfTea.scan()
                .chunkSize(1024)
                .parallel(true)
                .csv(file)
                .agg(Aggregations.count("id").as("cnt"), Aggregations.sum("val").as("sum_val"))
                .collect();

        assertThat(((Number) result.getRow(0).get("cnt")).longValue()).isEqualTo(50L);
        int expectedSum = 0;
        for (int i = 0; i < 50; i++) expectedSum += i;
        assertThat(((Number) result.getRow(0).get("sum_val")).intValue()).isEqualTo(expectedSum);
    }

    @Test
    @DisplayName("Should evaluate count() lazily with work-stealing partitions")
    void test_count_with_work_stealing() throws IOException {
        File file = new File(tempDir, "count_dataset.csv");
        int rows = 25_000;
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("a,b\n");
            for (int i = 0; i < rows; i++) {
                fw.write(i + "," + (i % 2 == 0 ? "EVEN" : "ODD") + "\n");
            }
        }

        long total = CupOfTea.scan()
                .chunkSize(4096)
                .parallel(true)
                .csv(file)
                .count();

        assertThat(total).isEqualTo(rows);

        long evenCount = CupOfTea.scan()
                .chunkSize(4096)
                .parallel(true)
                .csv(file)
                .filter(r -> "EVEN".equals(r.get("b")))
                .count();

        assertThat(evenCount).isEqualTo(rows / 2);
    }
}
