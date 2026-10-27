package com.cupoftea.benchmark.scaling;

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
 * Dataset scale benchmarks measuring execution speed and scaling across dataset scales
 * (10k, 100k, 1M, and 10M rows) for single-threaded vs. df.parallel() operations.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 2, time = 1)
@Fork(1)
public class DatasetScaleBenchmark {

    @Param({"10000", "100000", "1000000", "10000000"})
    public int rows;

    public CupOfTea seqDf;
    public CupOfTea parDf;

    @Setup
    public void setup() {
        CupOfTea base = SyntheticDataGenerator.createNumericDataFrame(rows);
        seqDf = base.asSequential();
        parDf = base.asParallel();
    }

    // --- AGGREGATION ---

    @Benchmark
    public void aggregationSequential(Blackhole bh) {
        bh.consume(seqDf.sumOf("val"));
        bh.consume(seqDf.meanOf("val"));
        bh.consume(seqDf.minOf("val"));
        bh.consume(seqDf.maxOf("val"));
    }

    @Benchmark
    public void aggregationParallel(Blackhole bh) {
        bh.consume(parDf.sumOf("val"));
        bh.consume(parDf.meanOf("val"));
        bh.consume(parDf.minOf("val"));
        bh.consume(parDf.maxOf("val"));
    }

    // --- FILTER ---

    @Benchmark
    public void filterSequential(Blackhole bh) {
        CupOfTea res = seqDf.filter(r -> (r.getInt("id") & 3) == 0 && r.getDouble("val") > 100.0);
        bh.consume(res.size());
    }

    @Benchmark
    public void filterParallel(Blackhole bh) {
        CupOfTea res = parDf.filter(r -> (r.getInt("id") & 3) == 0 && r.getDouble("val") > 100.0);
        bh.consume(res.size());
    }

    // --- PRIMITIVE VECTOR MAP ---

    @Benchmark
    public void mapSequential(Blackhole bh) {
        CupOfTea res = seqDf.mapDouble("val", v -> Math.sin(v) * Math.cos(v) + Math.sqrt(v));
        bh.consume(res.size());
    }

    @Benchmark
    public void mapParallel(Blackhole bh) {
        CupOfTea res = parDf.mapDouble("val", v -> Math.sin(v) * Math.cos(v) + Math.sqrt(v));
        bh.consume(res.size());
    }
}
