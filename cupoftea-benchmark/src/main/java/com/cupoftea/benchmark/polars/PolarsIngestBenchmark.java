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
import com.cupoftea.importers.configurations.CSVImporterConfiguration;
import com.cupoftea.table.Aggregations;

/**
 * Comparative ingestion & parsing benchmarks (CupOfTea vs. Polars):
 * Evaluates CSV, TSV, and Parquet ingestion with mixed data types (integers, floats, timestamps,
 * quoted strings with embedded delimiters, and nulls) across Eager and Lazy Streaming modes.
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
public class PolarsIngestBenchmark {

    @Param({"100000", "1000000"})
    public int rows;

    @Param({"0.05"})
    public double nullRatio;

    public File csvFile;
    public File tsvFile;
    public File parquetFile;

    @Setup(Level.Trial)
    public void setupTrial() throws IOException {
        csvFile = SyntheticDataGenerator.generateTempMixedCsv(rows, ',', nullRatio);
        tsvFile = SyntheticDataGenerator.generateTempMixedCsv(rows, '\t', nullRatio);
        parquetFile = SyntheticDataGenerator.generateTempParquet(Math.min(rows, 100_000));
    }

    @TearDown(Level.Trial)
    public void tearDownTrial() {
        safeDelete(csvFile);
        safeDelete(tsvFile);
        safeDelete(parquetFile);
    }

    private void safeDelete(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    @Benchmark
    public void ingestMixedCsvEager(Blackhole bh) {
        CupOfTea df = CupOfTea.read().csv(csvFile);
        bh.consume(df.size());
        bh.consume(df.getColumnVector("val_fixed"));
    }

    @Benchmark
    public void ingestMixedCsvLazyScan(Blackhole bh) {
        CupOfTea df = CupOfTea.scan()
                .csv(csvFile)
                .collect();
        bh.consume(df.size());
        bh.consume(df.getColumnVector("val_fixed"));
    }

    @Benchmark
    public void ingestMixedCsvLazyFusedAgg(Blackhole bh) {
        CupOfTea df = CupOfTea.scan()
                .csv(csvFile)
                .groupBy("k_low")
                .agg(
                    Aggregations.sum("val_fixed").as("sum_fixed"),
                    Aggregations.mean("val_float").as("mean_float"),
                    Aggregations.min("val_fixed").as("min_fixed"),
                    Aggregations.max("val_fixed").as("max_fixed")
                )
                .collect();
        bh.consume(df.size());
        bh.consume(df.getColumnVector("sum_fixed"));
    }

    @Benchmark
    public void ingestMixedTsvEager(Blackhole bh) {
        CSVImporterConfiguration tsvConfig = new CSVImporterConfiguration().setDelimiter('\t');
        CupOfTea df = CupOfTea.read().csv(tsvFile, tsvConfig);
        bh.consume(df.size());
        bh.consume(df.getColumnVector("val_fixed"));
    }

    @Benchmark
    public void ingestMixedTsvLazyScan(Blackhole bh) {
        CupOfTea df = CupOfTea.scan()
                .delimiter('\t')
                .csv(tsvFile)
                .collect();
        bh.consume(df.size());
        bh.consume(df.getColumnVector("val_fixed"));
    }

    @Benchmark
    public void ingestParquetEager(Blackhole bh) {
        if (parquetFile != null && parquetFile.exists()) {
            CupOfTea df = CupOfTea.read().parquet(parquetFile);
            bh.consume(df.size());
            bh.consume(df.getColumnVector("val"));
        }
    }

    @Benchmark
    public void ingestParquetLazyPushdown(Blackhole bh) {
        if (parquetFile != null && parquetFile.exists()) {
            CupOfTea df = CupOfTea.scan()
                    .parquet(parquetFile)
                    .select("id", "val", "category")
                    .filter(r -> r.getDouble("val") > 100.0)
                    .collect();
            bh.consume(df.size());
        }
    }
}
