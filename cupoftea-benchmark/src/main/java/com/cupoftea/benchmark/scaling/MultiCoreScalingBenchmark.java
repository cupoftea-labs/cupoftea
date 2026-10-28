package com.cupoftea.benchmark.scaling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 * Scaling benchmarks measuring multi-core CPU efficiency across 1, 2, 4, 8, and 16 worker threads
 * using df.parallel(executor).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class MultiCoreScalingBenchmark {

    @Param({"1", "2", "4", "8", "16"})
    public int threads;

    @Param({"500000"})
    public int rows;

    public ExecutorService executor;
    public CupOfTea df;

    @Setup(Level.Trial)
    public void setupTrial() {
        executor = Executors.newFixedThreadPool(threads);
        df = SyntheticDataGenerator.createNumericDataFrame(rows).parallel(executor);
    }

    @TearDown(Level.Trial)
    public void tearDownTrial() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Benchmark
    public void parallelAggregation(Blackhole bh) {
        bh.consume(df.sumOf("val"));
        bh.consume(df.meanOf("val"));
    }

    @Benchmark
    public void parallelFilter(Blackhole bh) {
        CupOfTea filtered = df.filter(r -> (r.getInt("id") & 1) == 0 && r.getDouble("val") > 50.0);
        bh.consume(filtered.size());
    }

    @Benchmark
    public void parallelVectorMap(Blackhole bh) {
        CupOfTea mapped = df.mapDouble("val", v -> Math.sin(v) * Math.cos(v) + Math.sqrt(v));
        bh.consume(mapped.size());
    }
}
