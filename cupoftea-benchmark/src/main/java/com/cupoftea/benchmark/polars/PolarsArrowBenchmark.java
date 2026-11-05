package com.cupoftea.benchmark.polars;

import java.util.concurrent.TimeUnit;

import org.apache.arrow.c.ArrowArray;
import org.apache.arrow.c.ArrowSchema;
import org.apache.arrow.c.CDataDictionaryProvider;
import org.apache.arrow.c.Data;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.message.ArrowRecordBatch;
import org.apache.arrow.vector.types.pojo.Schema;
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
import com.cupoftea.arrow.ArrowVectorMapper;
import com.cupoftea.benchmark.data.SyntheticDataGenerator;
import com.cupoftea.table.interfaces.IDataFrame;

/**
 * Comparative Arrow RecordBatch &amp; C Data conversion benchmark (CupOfTea vs. Polars):
 * Evaluates bidirectional zero-copy conversions between CupOfTea DataFrame and Apache Arrow
 * (VectorSchemaRoot, ArrowRecordBatch, and C Data Interface ArrowArray/ArrowSchema).
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
public class PolarsArrowBenchmark {

    @Param({"100000"})
    public int rows;

    public CupOfTea sourceDf;
    public BufferAllocator allocator;

    public VectorSchemaRoot preparedRoot;
    public ArrowRecordBatch preparedBatch;
    public Schema preparedSchema;

    @Setup(Level.Trial)
    public void setupTrial() {
        allocator = new RootAllocator(Long.MAX_VALUE);
        sourceDf = SyntheticDataGenerator.createMixedAnalyticsDataFrame(rows, 0.05);

        // Pre-convert for deserialization / import benchmarks
        preparedRoot = ArrowVectorMapper.toVectorSchemaRoot(sourceDf.getDataFrame(), allocator);
        preparedBatch = ArrowVectorMapper.toRecordBatch(sourceDf.getDataFrame(), allocator);
        preparedSchema = preparedRoot.getSchema();
    }

    @TearDown(Level.Trial)
    public void tearDownTrial() {
        if (preparedBatch != null) {
            preparedBatch.close();
        }
        if (preparedRoot != null) {
            preparedRoot.close();
        }
        if (allocator != null) {
            allocator.close();
        }
    }

    @Benchmark
    public void exportToVectorSchemaRoot(Blackhole bh) {
        try (VectorSchemaRoot root = ArrowVectorMapper.toVectorSchemaRoot(sourceDf.getDataFrame(), allocator)) {
            bh.consume(root.getRowCount());
        }
    }

    @Benchmark
    public void importFromVectorSchemaRoot(Blackhole bh) {
        IDataFrame df = ArrowVectorMapper.fromVectorSchemaRoot(preparedRoot);
        bh.consume(df.size());
    }

    @Benchmark
    public void exportToArrowRecordBatch(Blackhole bh) {
        try (ArrowRecordBatch batch = ArrowVectorMapper.toRecordBatch(sourceDf.getDataFrame(), allocator)) {
            bh.consume(batch.getLength());
        }
    }

    @Benchmark
    public void importFromArrowRecordBatch(Blackhole bh) {
        IDataFrame df = ArrowVectorMapper.fromRecordBatch(preparedBatch, preparedSchema, allocator);
        bh.consume(df.size());
    }

    @Benchmark
    public void exportToCDataInterface(Blackhole bh) {
        try (ArrowArray array = ArrowArray.allocateNew(allocator);
             ArrowSchema schema = ArrowSchema.allocateNew(allocator)) {
            ArrowVectorMapper.exportToCData(sourceDf.getDataFrame(), allocator, array, schema);
            bh.consume(array.memoryAddress());
            bh.consume(schema.memoryAddress());
            array.release();
            schema.release();
        }
    }

    @Benchmark
    public void importFromCDataInterface(Blackhole bh) {
        try (ArrowArray array = ArrowArray.allocateNew(allocator);
             ArrowSchema schema = ArrowSchema.allocateNew(allocator)) {
            ArrowVectorMapper.exportToCData(sourceDf.getDataFrame(), allocator, array, schema);
            IDataFrame df = ArrowVectorMapper.importFromCData(array, schema, allocator);
            bh.consume(df.size());
        }
    }
}
