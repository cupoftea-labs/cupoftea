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
import com.cupoftea.utils.interfaces.IColumnCreator;

/**
 * End-to-end analytical pipeline macro-benchmarks:
 * Missing value imputation (fillna, dropna) -> Row predicate filter -> Computed column derivation (addColumn) ->
 * Group-by aggregation (low & high cardinality) -> Parquet / JSON serialization.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 2, time = 1)
@Fork(1)
public class EndToEndPipelineBenchmark {

    @Param({"10000", "100000"})
    public int rows;

    public CupOfTea baseDf;
    public File outParquet;
    public File outJson;

    @Setup(Level.Trial)
    public void setupTrial() throws IOException {
        baseDf = SyntheticDataGenerator.createNullableDataFrame(rows, 0.05);
        outJson = File.createTempFile("pipeline_out_", ".json");
        outJson.deleteOnExit();

        if (SyntheticDataGenerator.isHadoopSupported()) {
            outParquet = File.createTempFile("pipeline_out_", ".parquet");
            if (outParquet.exists()) outParquet.delete();
            outParquet.deleteOnExit();
        }
    }

    @TearDown(Level.Trial)
    public void tearDownTrial() {
        if (outParquet != null && outParquet.exists()) outParquet.delete();
        if (outJson != null && outJson.exists()) outJson.delete();
    }

    @Benchmark
    public void fullPipelineSequential(Blackhole bh) {
        CupOfTea df = baseDf.asSequential()
                .fillna("val", 0.0)
                .dropna("id")
                .filter(r -> r.getDouble("val") > 50.0)
                .addColumn("derived", (IColumnCreator) r -> r.getDouble("val") * 1.05);

        CupOfTea lowCardAgg = df.groupBy("category").sum("derived");
        CupOfTea highCardAgg = df.groupBy("id").count();

        lowCardAgg.write().json(outJson);
        if (outParquet != null) {
            if (outParquet.exists()) outParquet.delete();
            lowCardAgg.write().parquet(outParquet);
        }

        bh.consume(lowCardAgg.size());
        bh.consume(highCardAgg.size());
    }

    @Benchmark
    public void fullPipelineParallel(Blackhole bh) {
        CupOfTea df = baseDf.asParallel()
                .fillna("val", 0.0)
                .dropna("id")
                .filter(r -> r.getDouble("val") > 50.0)
                .addColumn("derived", (IColumnCreator) r -> r.getDouble("val") * 1.05);

        CupOfTea lowCardAgg = df.groupBy("category").sum("derived");
        CupOfTea highCardAgg = df.groupBy("id").count();

        lowCardAgg.write().json(outJson);
        if (outParquet != null) {
            if (outParquet.exists()) outParquet.delete();
            lowCardAgg.write().parquet(outParquet);
        }

        bh.consume(lowCardAgg.size());
        bh.consume(highCardAgg.size());
    }
}
