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

/**
 * Micro-benchmarks measuring latency and throughput for primitive map-reduce aggregations:
 * sum, mean, min, max, median, std, and count.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class MapReduceAggregationBenchmark {

    @Param({"100000", "1000000"})
    public int rows;

    public CupOfTea seqDf;
    public CupOfTea parDf;

    @Setup
    public void setup() {
        CupOfTea base = SyntheticDataGenerator.createNumericDataFrame(rows);
        seqDf = base.asSequential();
        parDf = base.asParallel();
    }

    // --- SUM ---
    @Benchmark
    public void sumSequential(Blackhole bh) {
        bh.consume(seqDf.sumOf("val"));
    }

    @Benchmark
    public void sumParallel(Blackhole bh) {
        bh.consume(parDf.sumOf("val"));
    }

    // --- MEAN ---
    @Benchmark
    public void meanSequential(Blackhole bh) {
        bh.consume(seqDf.meanOf("val"));
    }

    @Benchmark
    public void meanParallel(Blackhole bh) {
        bh.consume(parDf.meanOf("val"));
    }

    // --- MIN ---
    @Benchmark
    public void minSequential(Blackhole bh) {
        bh.consume(seqDf.minOf("val"));
    }

    @Benchmark
    public void minParallel(Blackhole bh) {
        bh.consume(parDf.minOf("val"));
    }

    // --- MAX ---
    @Benchmark
    public void maxSequential(Blackhole bh) {
        bh.consume(seqDf.maxOf("val"));
    }

    @Benchmark
    public void maxParallel(Blackhole bh) {
        bh.consume(parDf.maxOf("val"));
    }

    // --- MEDIAN ---
    @Benchmark
    public void medianSequential(Blackhole bh) {
        bh.consume(seqDf.median("val"));
    }

    @Benchmark
    public void medianParallel(Blackhole bh) {
        bh.consume(parDf.median("val"));
    }

    // --- STD ---
    @Benchmark
    public void stdSequential(Blackhole bh) {
        bh.consume(seqDf.std("val"));
    }

    @Benchmark
    public void stdParallel(Blackhole bh) {
        bh.consume(parDf.std("val"));
    }

    // --- COUNT ---
    @Benchmark
    public void countSequential(Blackhole bh) {
        bh.consume(seqDf.countOf("val"));
    }

    @Benchmark
    public void countParallel(Blackhole bh) {
        bh.consume(parDf.countOf("val"));
    }
}
