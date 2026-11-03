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
import com.cupoftea.table.Aggregations;

/**
 * Comparative GroupBy & Aggregation benchmarks (CupOfTea vs. Polars):
 * Tests low-cardinality (100 groups), high-cardinality (up to 10M groups),
 * skewed Zipfian distributions, composite multi-column keys, and single-pass
 * multivariate aggregations (sum, mean, min, max, count, std).
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
public class PolarsGroupByBenchmark {

    @Param({"100000", "1000000"})
    public int rows;

    public CupOfTea seqDf;
    public CupOfTea parDf;

    @Setup(Level.Trial)
    public void setupTrial() {
        CupOfTea base = SyntheticDataGenerator.createMixedAnalyticsDataFrame(rows, 0.0);
        seqDf = base.asSequential();
        parDf = base.asParallel();
    }

    @Benchmark
    public void groupByLowCardSequential(Blackhole bh) {
        CupOfTea res = seqDf.groupBy("k_low").agg(
                Aggregations.sum("val_fixed").as("sum_fixed"),
                Aggregations.mean("val_fixed").as("mean_fixed"),
                Aggregations.min("val_fixed").as("min_fixed"),
                Aggregations.max("val_fixed").as("max_fixed")
        );
        bh.consume(res.size());
        bh.consume(res.getColumnVector("sum_fixed"));
    }

    @Benchmark
    public void groupByLowCardParallel(Blackhole bh) {
        CupOfTea res = parDf.groupBy("k_low").agg(
                Aggregations.sum("val_fixed").as("sum_fixed"),
                Aggregations.mean("val_fixed").as("mean_fixed"),
                Aggregations.min("val_fixed").as("min_fixed"),
                Aggregations.max("val_fixed").as("max_fixed")
        );
        bh.consume(res.size());
        bh.consume(res.getColumnVector("sum_fixed"));
    }

    @Benchmark
    public void groupByHighCardStringParallel(Blackhole bh) {
        CupOfTea res = parDf.groupBy("k_high").agg(
                Aggregations.sum("val_fixed").as("sum_fixed"),
                Aggregations.min("val_fixed").as("min_fixed"),
                Aggregations.max("val_fixed").as("max_fixed"),
                Aggregations.count("val_fixed").as("cnt_fixed")
        );
        bh.consume(res.size());
        bh.consume(res.getColumnVector("sum_fixed"));
    }

    @Benchmark
    public void groupBySkewedZipfIntParallel(Blackhole bh) {
        CupOfTea res = parDf.groupBy("k_zipf").agg(
                Aggregations.sum("val_fixed").as("sum_fixed"),
                Aggregations.mean("val_float").as("mean_float"),
                Aggregations.count("id").as("cnt")
        );
        bh.consume(res.size());
        bh.consume(res.getColumnVector("sum_fixed"));
    }

    @Benchmark
    public void groupByMultiKeyCompositeParallel(Blackhole bh) {
        CupOfTea res = parDf.groupBy("k_low", "k_zipf").agg(
                Aggregations.sum("val_fixed").as("sum_fixed"),
                Aggregations.mean("val_float").as("mean_float")
        );
        bh.consume(res.size());
        bh.consume(res.getColumnVector("sum_fixed"));
    }

    @Benchmark
    public void groupByMultivariateSinglePassParallel(Blackhole bh) {
        CupOfTea res = parDf.groupBy("k_low").agg(
                Aggregations.sum("val_fixed").as("sum_fixed"),
                Aggregations.mean("val_fixed").as("mean_fixed"),
                Aggregations.min("val_fixed").as("min_fixed"),
                Aggregations.max("val_fixed").as("max_fixed"),
                Aggregations.std("val_fixed").as("std_fixed"),
                Aggregations.sum("val_float").as("sum_float"),
                Aggregations.mean("val_float").as("mean_float"),
                Aggregations.min("val_float").as("min_float"),
                Aggregations.max("val_float").as("max_float"),
                Aggregations.std("val_float").as("std_float")
        );
        bh.consume(res.size());
        bh.consume(res.getColumnVector("std_fixed"));
    }
}
