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
import com.cupoftea.table.Schema;

/**
 * Standard One Billion Row Challenge (1BRC) benchmark suite for CupOfTea.
 *
 * <p>Evaluates end-to-end memory-mapped SIMD CSV scanning ({@code SimdCsvScanner}),
 * dynamic work-stealing ({@code CsvMmapPartitionReader}), SWAR 64-bit string hashing
 * ({@code CompactStringTable}), and contiguous cache-line interleaved accumulation
 * ({@code InterleavedNumericAccumulator}) across:</p>
 * <ul>
 *   <li><b>Standard 1-decimal fixed-point mode</b> (e.g. {@code 20.5})</li>
 *   <li><b>2-decimal fixed-point mode</b> (e.g. {@code 20.53})</li>
 *   <li><b>Hybrid 3+ decimal fallback mode</b> (injecting values like {@code 12.345} to test
 *       adaptive promotion from scaled integer to double accumulation and partition merge)</li>
 * </ul>
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
    "-Xmx12g",
    "-XX:+UseG1GC"
})
public class OneBrcStandardBenchmark {

    @Param({"1000000", "10000000"})
    public int rows;

    public File fileScale1;
    public File fileScale2;
    public File fileHybrid3Decimals;

    private static final Schema BRC_SCHEMA = Schema.of("station", Schema.STRING, "measure", Schema.DOUBLE);

    @Setup(Level.Trial)
    public void setupTrial() throws IOException {
        fileScale1 = SyntheticDataGenerator.generateTemp1BrcFile(rows, 1, 0);
        fileScale2 = SyntheticDataGenerator.generateTemp1BrcFile(rows, 2, 0);
        fileHybrid3Decimals = SyntheticDataGenerator.generateTemp1BrcFile(rows, 1, Math.max(100, rows / 20));
    }

    @TearDown(Level.Trial)
    public void tearDownTrial() {
        safeDelete(fileScale1);
        safeDelete(fileScale2);
        safeDelete(fileHybrid3Decimals);
    }

    private void safeDelete(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    @Benchmark
    public void oneBrcLazyStreamingScale1Standard(Blackhole bh) {
        CupOfTea res = execute1BrcQuery(fileScale1);
        bh.consume(res.size());
        bh.consume(res.getColumnVector("min_measure"));
        bh.consume(res.getColumnVector("mean_measure"));
        bh.consume(res.getColumnVector("max_measure"));
    }

    @Benchmark
    public void oneBrcLazyStreamingScale2Cents(Blackhole bh) {
        CupOfTea res = execute1BrcQuery(fileScale2);
        bh.consume(res.size());
        bh.consume(res.getColumnVector("min_measure"));
        bh.consume(res.getColumnVector("mean_measure"));
        bh.consume(res.getColumnVector("max_measure"));
    }

    @Benchmark
    public void oneBrcLazyStreamingHybrid3DecimalsFallback(Blackhole bh) {
        CupOfTea res = execute1BrcQuery(fileHybrid3Decimals);
        bh.consume(res.size());
        bh.consume(res.getColumnVector("min_measure"));
        bh.consume(res.getColumnVector("mean_measure"));
        bh.consume(res.getColumnVector("max_measure"));
    }

    public static CupOfTea execute1BrcQuery(File file) {
        return CupOfTea.scan()
                .delimiter(';')
                .hasHeaders(false)
                .schema(BRC_SCHEMA)
                .csv(file)
                .groupBy("station")
                .agg(
                    Aggregations.min("measure").as("min_measure"),
                    Aggregations.mean("measure").as("mean_measure"),
                    Aggregations.max("measure").as("max_measure")
                )
                .orderBy("station")
                .collect();
    }
}
