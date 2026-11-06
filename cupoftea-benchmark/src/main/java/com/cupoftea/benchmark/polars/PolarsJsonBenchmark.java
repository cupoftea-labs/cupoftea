package com.cupoftea.benchmark.polars;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import com.cupoftea.CupOfTea;
import com.cupoftea.benchmark.data.SyntheticDataGenerator;
import com.cupoftea.table.Aggregations;

/**
 * Ingestion and scanning benchmarks for JSON and NDJSON / JSON Lines:
 * Evaluates streaming columnar parser vs. lazy scan with projection pushdown and streaming aggregations.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(value = 1, jvmArgsAppend = {
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--add-modules=jdk.incubator.vector",
    "-Xms4g",
    "-Xmx8g",
    "-XX:+UseG1GC"
})
public class PolarsJsonBenchmark {

    @Param({"10000", "100000"})
    public int rows;

    public File jsonArrayFile;
    public File jsonLinesFile;

    @Setup(Level.Trial)
    public void setupTrial() throws IOException {
        jsonArrayFile = SyntheticDataGenerator.generateTempJson(rows);
        jsonLinesFile = SyntheticDataGenerator.generateTempJsonLines(rows);
    }

    @TearDown(Level.Trial)
    public void tearDownTrial() {
        safeDelete(jsonArrayFile);
        safeDelete(jsonLinesFile);
    }

    private void safeDelete(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    @Benchmark
    public void ingestJsonArrayEager(Blackhole bh) {
        CupOfTea df = CupOfTea.read().json(jsonArrayFile);
        bh.consume(df.size());
        bh.consume(df.getColumnVector("val"));
    }

    @Benchmark
    public void ingestJsonArrayLazyScan(Blackhole bh) {
        CupOfTea df = CupOfTea.scanJson(jsonArrayFile).collect();
        bh.consume(df.size());
        bh.consume(df.getColumnVector("val"));
    }

    @Benchmark
    public void ingestJsonArrayLazyProjected(Blackhole bh) {
        CupOfTea df = CupOfTea.scanJson(jsonArrayFile)
                .select("id", "val", "category")
                .collect();
        bh.consume(df.size());
        bh.consume(df.getColumnVector("val"));
    }

    @Benchmark
    public void ingestJsonLinesLazyScanParallel(Blackhole bh) {
        CupOfTea df = CupOfTea.scanJson(jsonLinesFile).collect();
        bh.consume(df.size());
        bh.consume(df.getColumnVector("val"));
    }

    @Benchmark
    public void ingestJsonLinesLazyAggregation(Blackhole bh) {
        CupOfTea df = CupOfTea.scanJson(jsonLinesFile)
                .groupBy("category")
                .agg(
                    Aggregations.sum("amount").as("sum_amount"),
                    Aggregations.mean("val").as("mean_val"),
                    Aggregations.count("id").as("cnt")
                )
                .collect();
        bh.consume(df.size());
    }
}
