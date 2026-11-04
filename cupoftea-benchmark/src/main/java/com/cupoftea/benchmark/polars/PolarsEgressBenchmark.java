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
import com.cupoftea.exporters.configurations.CSVExporterConfiguration;

/**
 * Comparative File Creation & Export (Egress) benchmarks (CupOfTea vs. Polars):
 * Evaluates CSV, TSV, Parquet, and JSON file generation/serialization across
 * mixed columnar datasets.
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
public class PolarsEgressBenchmark {

    @Param({"100000"})
    public int rows;

    public CupOfTea sourceDf;
    public File outCsvFile;
    public File outTsvFile;
    public File outParquetFile;
    public File outJsonFile;

    private final CSVExporterConfiguration tsvConfig = new CSVExporterConfiguration().setDelimiter("\t");

    @Setup(Level.Trial)
    public void setupTrial() throws IOException {
        sourceDf = SyntheticDataGenerator.createMixedAnalyticsDataFrame(rows, 0.05);

        outCsvFile = File.createTempFile("polars_egress_test_", ".csv");
        outTsvFile = File.createTempFile("polars_egress_test_", ".tsv");
        outParquetFile = File.createTempFile("polars_egress_test_", ".parquet");
        safeDelete(outParquetFile); // Parquet writer expects file to not exist initially
        outJsonFile = File.createTempFile("polars_egress_test_", ".json");
    }

    @TearDown(Level.Trial)
    public void tearDownTrial() {
        safeDelete(outCsvFile);
        safeDelete(outTsvFile);
        safeDelete(outParquetFile);
        safeDelete(outJsonFile);
    }

    private void safeDelete(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    @Benchmark
    public void writeCsv(Blackhole bh) {
        sourceDf.write().csv(outCsvFile);
        bh.consume(outCsvFile.length());
    }

    @Benchmark
    public void writeTsv(Blackhole bh) {
        sourceDf.exportToCsv(outTsvFile, tsvConfig);
        bh.consume(outTsvFile.length());
    }

    @Benchmark
    public void writeParquet(Blackhole bh) {
        safeDelete(outParquetFile);
        sourceDf.write().parquet(outParquetFile);
        bh.consume(outParquetFile.length());
    }

    @Benchmark
    public void writeJson(Blackhole bh) {
        sourceDf.write().json(outJsonFile);
        bh.consume(outJsonFile.length());
    }
}
