package com.cupoftea.benchmark.micro;

import java.util.BitSet;
import java.util.List;
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

import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.ColumnVectorSlice;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.IntColumnVector;

/**
 * Micro-benchmarks validating zero-copy slicing and partitioning overhead on ColumnVectors
 * (sliceView vs slice, partition chunking, and sub-view traversal).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class ZeroCopySlicingBenchmark {

    @Param({"100000", "1000000"})
    public int size;

    public IntColumnVector intVec;
    public DoubleColumnVector doubleVec;

    public int from;
    public int to;
    public int chunks = 8;

    @Setup
    public void setup() {
        int[] ints = new int[size];
        double[] doubles = new double[size];
        for (int i = 0; i < size; i++) {
            ints[i] = i;
            doubles[i] = (i % 1000) * 0.5 + 1.25;
        }

        intVec = new IntColumnVector("id", ints, new BitSet(), size);
        doubleVec = new DoubleColumnVector("val", doubles, new BitSet(), size);

        from = size / 4;
        to = (size * 3) / 4;
    }

    @Benchmark
    public void sliceCopyInt(Blackhole bh) {
        ColumnVector copied = intVec.slice(from, to);
        bh.consume(copied);
    }

    @Benchmark
    public void sliceViewInt(Blackhole bh) {
        ColumnVectorSlice slice = intVec.sliceView(from, to);
        bh.consume(slice);
    }

    @Benchmark
    public void sliceCopyDouble(Blackhole bh) {
        ColumnVector copied = doubleVec.slice(from, to);
        bh.consume(copied);
    }

    @Benchmark
    public void sliceViewDouble(Blackhole bh) {
        ColumnVectorSlice slice = doubleVec.sliceView(from, to);
        bh.consume(slice);
    }

    @Benchmark
    public void partitionIntChunks(Blackhole bh) {
        List<ColumnVectorSlice> partitions = intVec.partition(chunks);
        bh.consume(partitions);
    }

    @Benchmark
    public void partitionDoubleChunks(Blackhole bh) {
        List<ColumnVectorSlice> partitions = doubleVec.partition(chunks);
        bh.consume(partitions);
    }

    @Benchmark
    public void scanSliceViewInt(Blackhole bh) {
        ColumnVectorSlice slice = intVec.sliceView(from, to);
        long sum = 0;
        int len = slice.size();
        for (int i = 0; i < len; i++) {
            sum += slice.getInt(i);
        }
        bh.consume(sum);
    }
}
