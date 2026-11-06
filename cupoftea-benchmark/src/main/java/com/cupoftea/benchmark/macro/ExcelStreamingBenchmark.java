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
import com.cupoftea.importers.ExcelImporter;
import com.cupoftea.importers.configurations.ExcelImporterConfiguration;

/**
 * Dedicated benchmark comparing streaming SAX reading and SXSSF writing
 * for Excel (.xlsx) workbooks against baseline operations.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 2, time = 1)
@Fork(1)
public class ExcelStreamingBenchmark {

    @Param({"1000", "10000"})
    public int rows;

    public File excelFile;
    public File outExcelFile;
    public CupOfTea df;

    @Setup(Level.Trial)
    public void setupTrial() throws IOException {
        excelFile = SyntheticDataGenerator.generateTempExcel(rows);
        outExcelFile = File.createTempFile("bench_out_", ".xlsx");
        outExcelFile.deleteOnExit();
        df = SyntheticDataGenerator.createNumericDataFrame(rows);
    }

    @TearDown(Level.Trial)
    public void tearDownTrial() {
        safeDelete(excelFile);
        safeDelete(outExcelFile);
    }

    private void safeDelete(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    @Benchmark
    public void streamingSaxIngest(Blackhole bh) {
        CupOfTea res = CupOfTea.read().excel(excelFile);
        bh.consume(res.size());
    }

    @Benchmark
    public void sxssfStreamingEgress(Blackhole bh) {
        safeDelete(outExcelFile);
        df.write().excel(outExcelFile);
        bh.consume(outExcelFile.length());
    }

    @Benchmark
    public void lazyExcelScan(Blackhole bh) {
        CupOfTea res = CupOfTea.scan().excel(excelFile).collect();
        bh.consume(res.size());
    }
}
