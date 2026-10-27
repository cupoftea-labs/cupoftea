package com.cupoftea.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.BitSet;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.IntColumnVector;

/**
 * Micro-benchmarks comparing single-thread vs. parallel execution across
 * varying dataset sizes (10k, 100k, 1M, and 10M rows) for vectorized filter,
 * column projection/map, and aggregations.
 */
public class ParallelDataFrameBenchmarkTest {

    private static CupOfTea createNumericDataFrame(int rows) {
        int[] ids = new int[rows];
        double[] values = new double[rows];
        for (int i = 0; i < rows; i++) {
            ids[i] = i;
            values[i] = (i % 1000) * 0.5 + 1.25;
        }
        List<ColumnVector> vectors = List.of(
            new IntColumnVector("id", ids, new BitSet(), rows),
            new DoubleColumnVector("val", values, new BitSet(), rows)
        );
        return CupOfTea.fromVectors(vectors).withMutationMode(MutationMode.COPY_ON_WRITE);
    }

    @Test
    @DisplayName("Benchmark single-thread vs parallel across 10k, 100k, 1M, and 10M rows")
    void benchmarkSequentialVsParallelAcrossDatasetSizes() {
        int[] sizes = {10_000, 100_000, 1_000_000, 10_000_000};

        // Warmup JVM
        CupOfTea warmupDf = createNumericDataFrame(20_000);
        warmupDf.asSequential().sumOf("val");
        warmupDf.asParallel().sumOf("val");
        warmupDf.asSequential().filter(r -> r.getInt("id") % 2 == 0);
        warmupDf.asParallel().filter(r -> r.getInt("id") % 2 == 0);

        for (int rows : sizes) {
            CupOfTea df = createNumericDataFrame(rows);
            CupOfTea seq = df.asSequential();
            CupOfTea par = df.asParallel();

            // 1. Vectorized Aggregation (sum + mean + min + max)
            long t0 = System.nanoTime();
            Double seqSum = (Double) seq.sumOf("val");
            Double seqMean = seq.meanOf("val");
            Double seqMin = (Double) seq.minOf("val");
            Double seqMax = (Double) seq.maxOf("val");
            long seqAggNs = System.nanoTime() - t0;

            long t1 = System.nanoTime();
            Double parSum = (Double) par.sumOf("val");
            Double parMean = par.meanOf("val");
            Double parMin = (Double) par.minOf("val");
            Double parMax = (Double) par.maxOf("val");
            long parAggNs = System.nanoTime() - t1;

            assertThat(parSum).isEqualTo(seqSum);
            assertThat(parMean).isEqualTo(seqMean);
            assertThat(parMin).isEqualTo(seqMin);
            assertThat(parMax).isEqualTo(seqMax);

            // 2. Primitive Vector Map (compute-heavy math per element)
            long t2 = System.nanoTime();
            CupOfTea seqMapped = seq.mapDouble("val", v -> Math.sin(v) * Math.cos(v) + Math.sqrt(v));
            long seqMapNs = System.nanoTime() - t2;

            long t3 = System.nanoTime();
            CupOfTea parMapped = par.mapDouble("val", v -> Math.sin(v) * Math.cos(v) + Math.sqrt(v));
            long parMapNs = System.nanoTime() - t3;

            assertThat(parMapped.size()).isEqualTo(seqMapped.size());
            assertThat(parMapped.getColumnVector("val").getDouble(0))
                .isEqualTo(seqMapped.getColumnVector("val").getDouble(0));
            assertThat(parMapped.getColumnVector("val").getDouble(rows - 1))
                .isEqualTo(seqMapped.getColumnVector("val").getDouble(rows - 1));

            // 3. Vectorized Filter (prefix-sum compaction)
            long t4 = System.nanoTime();
            CupOfTea seqFiltered = seq.filter(r -> (r.getInt("id") & 3) == 0 && r.getDouble("val") > 100.0);
            long seqFilterNs = System.nanoTime() - t4;

            long t5 = System.nanoTime();
            CupOfTea parFiltered = par.filter(r -> (r.getInt("id") & 3) == 0 && r.getDouble("val") > 100.0);
            long parFilterNs = System.nanoTime() - t5;

            assertThat(parFiltered.size()).isEqualTo(seqFiltered.size());

            System.out.printf(
                "[Benchmark %,10d rows] Agg: seq=%.2fms par=%.2fms | Map: seq=%.2fms par=%.2fms | Filter: seq=%.2fms par=%.2fms%n",
                rows,
                seqAggNs / 1_000_000.0, parAggNs / 1_000_000.0,
                seqMapNs / 1_000_000.0, parMapNs / 1_000_000.0,
                seqFilterNs / 1_000_000.0, parFilterNs / 1_000_000.0
            );
        }
    }
}
