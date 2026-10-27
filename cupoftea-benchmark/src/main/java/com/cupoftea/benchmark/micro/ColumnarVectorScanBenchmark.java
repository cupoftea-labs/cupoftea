package com.cupoftea.benchmark.micro;

import java.util.BitSet;
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

import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * Micro-benchmarks comparing sequential row-at-a-time scan speeds vs. vectorized contiguous bulk
 * operations across primitive ColumnVector implementations.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class ColumnarVectorScanBenchmark {

    @Param({"100000", "1000000"})
    public int size;

    public IntColumnVector intVec;
    public DoubleColumnVector doubleVec;
    public LongColumnVector longVec;
    public FloatColumnVector floatVec;
    public BooleanColumnVector booleanVec;
    public StringColumnVector stringVec;

    @Setup
    public void setup() {
        int[] ints = new int[size];
        double[] doubles = new double[size];
        long[] longs = new long[size];
        float[] floats = new float[size];
        boolean[] booleans = new boolean[size];
        String[] strings = new String[size];

        for (int i = 0; i < size; i++) {
            ints[i] = i;
            doubles[i] = (i % 1000) * 0.5 + 1.25;
            longs[i] = (long) i * 100L;
            floats[i] = (float) ((i % 500) * 0.1f);
            booleans[i] = (i % 2) == 0;
            strings[i] = "CAT_" + (i % 10);
        }

        intVec = new IntColumnVector("id", ints, new BitSet(), size);
        doubleVec = new DoubleColumnVector("val", doubles, new BitSet(), size);
        longVec = new LongColumnVector("amount", longs, new BitSet(), size);
        floatVec = new FloatColumnVector("ratio", floats, new BitSet(), size);
        booleanVec = new BooleanColumnVector("flag", booleans, new BitSet(), size);
        stringVec = new StringColumnVector("category", strings, new BitSet(), size);
    }

    // --- IntColumnVector ---

    @Benchmark
    public void scanSequentialInt(Blackhole bh) {
        long sum = 0;
        for (int i = 0; i < size; i++) {
            sum += intVec.getInt(i);
        }
        bh.consume(sum);
    }

    @Benchmark
    public void scanVectorizedInt(Blackhole bh) {
        bh.consume(intVec.sumLong());
    }

    // --- DoubleColumnVector ---

    @Benchmark
    public void scanSequentialDouble(Blackhole bh) {
        double sum = 0;
        for (int i = 0; i < size; i++) {
            sum += doubleVec.getDouble(i);
        }
        bh.consume(sum);
    }

    @Benchmark
    public void scanVectorizedDouble(Blackhole bh) {
        bh.consume(doubleVec.sumDouble());
    }

    // --- LongColumnVector ---

    @Benchmark
    public void scanSequentialLong(Blackhole bh) {
        long sum = 0;
        for (int i = 0; i < size; i++) {
            sum += longVec.getLong(i);
        }
        bh.consume(sum);
    }

    @Benchmark
    public void scanVectorizedLong(Blackhole bh) {
        bh.consume(longVec.sumLong());
    }

    // --- FloatColumnVector ---

    @Benchmark
    public void scanSequentialFloat(Blackhole bh) {
        float sum = 0;
        for (int i = 0; i < size; i++) {
            sum += floatVec.getFloat(i);
        }
        bh.consume(sum);
    }

    @Benchmark
    public void scanVectorizedFloat(Blackhole bh) {
        bh.consume(floatVec.sum());
    }

    // --- BooleanColumnVector ---

    @Benchmark
    public void scanSequentialBoolean(Blackhole bh) {
        int trueCount = 0;
        for (int i = 0; i < size; i++) {
            if (booleanVec.getBoolean(i)) {
                trueCount++;
            }
        }
        bh.consume(trueCount);
    }

    @Benchmark
    public void scanVectorizedBoolean(Blackhole bh) {
        // Primitive copy / bulk array check
        boolean[] raw = booleanVec.getRawData();
        int trueCount = 0;
        for (int i = 0; i < size; i++) {
            if (raw[i]) {
                trueCount++;
            }
        }
        bh.consume(trueCount);
    }

    // --- StringColumnVector ---

    @Benchmark
    public void scanSequentialString(Blackhole bh) {
        long totalLen = 0;
        for (int i = 0; i < size; i++) {
            totalLen += stringVec.getString(i).length();
        }
        bh.consume(totalLen);
    }

    @Benchmark
    public void scanVectorizedString(Blackhole bh) {
        bh.consume(stringVec.length().sumLong());
    }
}
