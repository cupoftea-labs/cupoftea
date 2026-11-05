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
 * Comparative Window Functions and Rolling Analytics benchmarks (CupOfTea vs. Polars):
 * Evaluates shift operations (lag, lead), cumulative functions (cumsum, cumprod, cummin, cummax),
 * fixed-size rolling windows (rolling_mean, rolling_sum), and partition ranking (row_number, rank, dense_rank)
 * in comparison with pl.col().over() and pl.col().rolling_*() of Polars.
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
public class PolarsWindowBenchmark {

    @Param({"100000", "1000000"})
    public int rows;

    public CupOfTea baseDf;

    @Setup(Level.Trial)
    public void setupTrial() {
        baseDf = SyntheticDataGenerator.createMixedAnalyticsDataFrame(rows, 0.0).asCopyOnWrite();
    }

    // --- Shift Operations (lag, lead) ---

    @Benchmark
    public void lagShift(Blackhole bh) {
        CupOfTea res = baseDf.lag("val_fixed", 1, "lag_val");
        bh.consume(res.size());
        bh.consume(res.getColumnVector("lag_val"));
    }

    @Benchmark
    public void leadShift(Blackhole bh) {
        CupOfTea res = baseDf.lead("val_fixed", 1, "lead_val");
        bh.consume(res.size());
        bh.consume(res.getColumnVector("lead_val"));
    }

    // --- Cumulative Functions (cumsum, cumprod, cummin, cummax) ---

    @Benchmark
    public void cumsum(Blackhole bh) {
        CupOfTea res = baseDf.cumsum("val_fixed");
        bh.consume(res.size());
        bh.consume(res.getColumnVector("val_fixed"));
    }

    @Benchmark
    public void cumprod(Blackhole bh) {
        CupOfTea res = baseDf.cumprod("val_fixed");
        bh.consume(res.size());
        bh.consume(res.getColumnVector("val_fixed"));
    }

    @Benchmark
    public void cummin(Blackhole bh) {
        CupOfTea res = baseDf.cummin("val_fixed");
        bh.consume(res.size());
        bh.consume(res.getColumnVector("val_fixed"));
    }

    @Benchmark
    public void cummax(Blackhole bh) {
        CupOfTea res = baseDf.cummax("val_fixed");
        bh.consume(res.size());
        bh.consume(res.getColumnVector("val_fixed"));
    }

    // --- Rolling Fixed Windows (rolling_mean, rolling_sum) ---

    @Benchmark
    public void rollingMeanFixedWindow(Blackhole bh) {
        CupOfTea res = baseDf.rolling_mean("val_fixed", 5, "roll_mean");
        bh.consume(res.size());
        bh.consume(res.getColumnVector("roll_mean"));
    }

    @Benchmark
    public void rollingSumFixedWindow(Blackhole bh) {
        CupOfTea res = baseDf.rolling_sum("val_fixed", 5, "roll_sum");
        bh.consume(res.size());
        bh.consume(res.getColumnVector("roll_sum"));
    }

    // --- Analytical Ranking & Partitioning (row_number, rank, dense_rank) ---

    @Benchmark
    public void rowNumber(Blackhole bh) {
        CupOfTea res = baseDf.rowNumber("row_num");
        bh.consume(res.size());
        bh.consume(res.getColumnVector("row_num"));
    }

    @Benchmark
    public void rank(Blackhole bh) {
        CupOfTea res = baseDf.rank("val_fixed", "rank_val");
        bh.consume(res.size());
        bh.consume(res.getColumnVector("rank_val"));
    }

    @Benchmark
    public void denseRank(Blackhole bh) {
        CupOfTea res = baseDf.denseRank("val_fixed", "dense_rank_val");
        bh.consume(res.size());
        bh.consume(res.getColumnVector("dense_rank_val"));
    }

    // --- Partitioned Window Functions (pl.col().over()) ---

    @Benchmark
    public void windowPartitionedRank(Blackhole bh) {
        CupOfTea res = baseDf.window().partitionBy("k_low").orderBy("val_fixed").rank("part_rank");
        bh.consume(res.size());
        bh.consume(res.getColumnVector("part_rank"));
    }

    @Benchmark
    public void windowPartitionedLag(Blackhole bh) {
        CupOfTea res = baseDf.window().partitionBy("k_low").lag("val_fixed", 1, "part_lag");
        bh.consume(res.size());
        bh.consume(res.getColumnVector("part_lag"));
    }
}
