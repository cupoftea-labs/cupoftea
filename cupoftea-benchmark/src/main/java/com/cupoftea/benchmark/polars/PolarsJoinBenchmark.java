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
 * Comparative Join benchmarks (CupOfTea vs. Polars):
 * Evaluates Inner and Left Outer Joins across integer (INT32) and alphanumeric UTF-8 (STRING) keys
 * under 1:1, 1:N (Star Schema fact-to-dimension), and N:M (many-to-many collision) cardinalities.
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
public class PolarsJoinBenchmark {

    @Param({"100000", "500000"})
    public int rows;

    public CupOfTea left1to1;
    public CupOfTea right1to1;

    public CupOfTea left1toN;
    public CupOfTea right1toN;

    public CupOfTea leftNtoM;
    public CupOfTea rightNtoM;

    @Setup(Level.Trial)
    public void setupTrial() {
        // 1:1 Join (Unique keys on both sides)
        left1to1 = SyntheticDataGenerator.createJoinLeftDataFrame(rows, rows).asParallel();
        right1to1 = SyntheticDataGenerator.createJoinRightDataFrame(rows, rows, "1:1").asParallel();

        // 1:N Join (Fact table of rows joining against Dimension table of rows / 10)
        int dimCard = Math.max(100, rows / 10);
        left1toN = SyntheticDataGenerator.createJoinLeftDataFrame(rows, dimCard).asParallel();
        right1toN = SyntheticDataGenerator.createJoinRightDataFrame(dimCard, dimCard, "1:N").asParallel();

        // N:M Join (Duplicate keys on both sides, smaller scale to bound output explosion)
        int nmRows = Math.max(1000, rows / 10);
        int nmCard = Math.max(250, nmRows / 2);
        leftNtoM = SyntheticDataGenerator.createJoinLeftDataFrame(nmRows, nmCard).asParallel();
        rightNtoM = SyntheticDataGenerator.createJoinRightDataFrame(nmRows, nmCard, "N:M").asParallel();
    }

    @Benchmark
    public void innerJoinInt1to1(Blackhole bh) {
        CupOfTea res = left1to1.innerJoin(right1to1, "key_int");
        bh.consume(res.size());
    }

    @Benchmark
    public void leftJoinInt1to1(Blackhole bh) {
        CupOfTea res = left1to1.leftJoin(right1to1, "key_int");
        bh.consume(res.size());
    }

    @Benchmark
    public void innerJoinString1to1(Blackhole bh) {
        CupOfTea res = left1to1.innerJoin(right1to1, "key_str");
        bh.consume(res.size());
    }

    @Benchmark
    public void leftJoinString1to1(Blackhole bh) {
        CupOfTea res = left1to1.leftJoin(right1to1, "key_str");
        bh.consume(res.size());
    }

    @Benchmark
    public void innerJoinInt1toN(Blackhole bh) {
        CupOfTea res = left1toN.innerJoin(right1toN, "key_int");
        bh.consume(res.size());
    }

    @Benchmark
    public void leftJoinString1toN(Blackhole bh) {
        CupOfTea res = left1toN.leftJoin(right1toN, "key_str");
        bh.consume(res.size());
    }

    @Benchmark
    public void innerJoinIntNtoM(Blackhole bh) {
        CupOfTea res = leftNtoM.innerJoin(rightNtoM, "key_int");
        bh.consume(res.size());
    }

    @Benchmark
    public void leftJoinStringNtoM(Blackhole bh) {
        CupOfTea res = leftNtoM.leftJoin(rightNtoM, "key_str");
        bh.consume(res.size());
    }
}
