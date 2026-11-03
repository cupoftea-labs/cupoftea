package com.cupoftea.benchmark.polars;

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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import com.cupoftea.CupOfTea;
import com.cupoftea.benchmark.data.SyntheticDataGenerator;

/**
 * Comparative Filter & Vectorized Expression benchmarks (CupOfTea vs. Polars):
 * Evaluates compound boolean predicates at low (1%), medium (50% - maximum branch misprediction),
 * and high (90%) selectivity, as well as vectorized mathematical transformations.
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
public class PolarsFilterVectorBenchmark {

    @Param({"1000000", "10000000"})
    public int rows;

    public CupOfTea seqDf;
    public CupOfTea parDf;

    @Setup(Level.Trial)
    public void setupTrial() {
        CupOfTea base = SyntheticDataGenerator.createNumericDataFrame(rows);
        seqDf = base.asSequential();
        parDf = base.asParallel();
    }

    @Benchmark
    public void filterLowSelectivity1PctParallel(Blackhole bh) {
        // ~1% selectivity: (id % 100 == 0) && val > 0
        CupOfTea res = parDf.filter(r -> (r.getInt("id") % 100) == 0 && r.getDouble("val") > 0.0);
        bh.consume(res.size());
    }

    @Benchmark
    public void filterMediumSelectivity50PctParallel(Blackhole bh) {
        // ~50% selectivity: worst case for scalar branch predictor
        CupOfTea res = parDf.filter(r -> r.getBoolean("flag") && r.getDouble("val") >= 1.25);
        bh.consume(res.size());
    }

    @Benchmark
    public void filterHighSelectivity90PctParallel(Blackhole bh) {
        // ~90% selectivity: heavy compaction & vector materialization
        CupOfTea res = parDf.filter(r -> (r.getInt("id") % 10) != 0 || r.getDouble("val") > 250.0);
        bh.consume(res.size());
    }

    @Benchmark
    public void filterCompoundBooleanExpressionParallel(Blackhole bh) {
        CupOfTea res = parDf.filter(r ->
                (r.getInt("id") > 100 && r.getDouble("val") <= 350.0)
                || (r.getBoolean("flag") && r.getLong("amount") > 50000L));
        bh.consume(res.size());
    }

    @Benchmark
    public void vectorizedMathExpressionSequential(Blackhole bh) {
        CupOfTea res = seqDf.mapDouble("val", v -> v * 1.15 + Math.sqrt(Math.abs(v)));
        bh.consume(res.size());
        bh.consume(res.getColumnVector("val"));
    }

    @Benchmark
    public void vectorizedMathExpressionParallel(Blackhole bh) {
        CupOfTea res = parDf.mapDouble("val", v -> v * 1.15 + Math.sqrt(Math.abs(v)));
        bh.consume(res.size());
        bh.consume(res.getColumnVector("val"));
    }
}
