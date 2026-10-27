package com.cupoftea.benchmark.macro;

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

/**
 * Macro-benchmarks for format ingest (CSV, JSON, Parquet, Excel, XML) and
 * format egress/serialization (CSV, JSON, Parquet).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 2, time = 1)
@Fork(1)
public class EndToEndIoBenchmark {

    @Param({"10000"})
    public int rows;

    public File csvFile;
    public File jsonFile;
    public File parquetFile;
    public File excelFile;
    public File xmlFile;

    public File outCsvFile;
    public File outJsonFile;
    public File outParquetFile;

    public CupOfTea df;

    @Setup(Level.Trial)
    public void setupTrial() throws IOException {
        csvFile = SyntheticDataGenerator.generateTempCsv(rows);
        jsonFile = SyntheticDataGenerator.generateTempJson(rows);
        parquetFile = SyntheticDataGenerator.generateTempParquet(rows);
        excelFile = SyntheticDataGenerator.generateTempExcel(rows);
        xmlFile = SyntheticDataGenerator.generateTempXml(rows);

        outCsvFile = File.createTempFile("out_", ".csv");
        outCsvFile.deleteOnExit();
        outJsonFile = File.createTempFile("out_", ".json");
        outJsonFile.deleteOnExit();

        if (SyntheticDataGenerator.isHadoopSupported()) {
            outParquetFile = File.createTempFile("out_", ".parquet");
            outParquetFile.deleteOnExit();
        }

        df = SyntheticDataGenerator.createNumericDataFrame(rows);
    }

    @TearDown(Level.Trial)
    public void tearDownTrial() {
        safeDelete(csvFile);
        safeDelete(jsonFile);
        safeDelete(parquetFile);
        safeDelete(excelFile);
        safeDelete(xmlFile);
        safeDelete(outCsvFile);
        safeDelete(outJsonFile);
        safeDelete(outParquetFile);
    }

    private void safeDelete(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    // --- INGEST ---

    @Benchmark
    public void ingestCsv(Blackhole bh) {
        CupOfTea res = CupOfTea.read().csv(csvFile);
        bh.consume(res.size());
    }

    @Benchmark
    public void ingestJson(Blackhole bh) {
        CupOfTea res = CupOfTea.read().json(jsonFile);
        bh.consume(res.size());
    }

    @Benchmark
    public void ingestParquet(Blackhole bh) {
        if (parquetFile != null && parquetFile.exists()) {
            CupOfTea res = CupOfTea.read().parquet(parquetFile);
            bh.consume(res.size());
        }
    }

    @Benchmark
    public void ingestExcel(Blackhole bh) {
        CupOfTea res = CupOfTea.read().excel(excelFile);
        bh.consume(res.size());
    }

    @Benchmark
    public void ingestXml(Blackhole bh) {
        CupOfTea res = CupOfTea.read().xml(xmlFile);
        bh.consume(res.size());
    }

    // --- EGRESS ---

    @Benchmark
    public void egressCsv(Blackhole bh) {
        df.write().csv(outCsvFile);
        bh.consume(outCsvFile.length());
    }

    @Benchmark
    public void egressJson(Blackhole bh) {
        df.write().json(outJsonFile);
        bh.consume(outJsonFile.length());
    }

    @Benchmark
    public void egressParquet(Blackhole bh) {
        if (outParquetFile != null) {
            df.write().parquet(outParquetFile);
            bh.consume(outParquetFile.length());
        }
    }
}
