package com.cupoftea.benchmark.micro;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import com.cupoftea.CupOfTea;
import com.cupoftea.benchmark.data.SyntheticDataGenerator;
import com.cupoftea.utils.interfaces.IFilter;

/**
 * Micro-benchmarks comparing single-threaded vs. parallel prefix-sum consolidation
 * filter performance across varying selectivity rates (1%, 10%, 50%, 90%).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class ParallelScanAndFilterBenchmark {

    @Param({"100000", "1000000"})
    public int rows;

    @Param({"0.01", "0.10", "0.50", "0.90"})
    public String selectivity;

    public CupOfTea df;
    public IFilter predicate;

    @Setup
    public void setup() {
        df = SyntheticDataGenerator.createNumericDataFrame(rows);
        int thresholdPct = (int) Math.round(Double.parseDouble(selectivity) * 100.0);
        predicate = row -> (row.getInt("id") % 100) < thresholdPct;
    }

    @Benchmark
    public void filterSingleThread(Blackhole bh) {
        CupOfTea filtered = df.asSequential().filter(predicate);
        bh.consume(filtered.size());
    }

    @Benchmark
    public void filterParallel(Blackhole bh) {
        CupOfTea filtered = df.asParallel().filter(predicate);
        bh.consume(filtered.size());
    }
}
