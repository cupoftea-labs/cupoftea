package com.cupoftea.table;

import static org.assertj.core.api.Assertions.assertThat;
import static com.cupoftea.table.Aggregations.count;
import static com.cupoftea.table.Aggregations.sum;

import java.util.BitSet;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

public class InterleavedPrefetchJoinGroupByTest {

    @Test
    @DisplayName("Large IntColumnVector Join (>32k threshold) with unique build keys validates interleaved probing")
    void testLargeIntJoinInterleaved() {
        int n = 70_000;
        int[] leftKeys = new int[n];
        int[] rightKeys = new int[n];
        long[] leftVals = new long[n];
        long[] rightVals = new long[n];

        Random rnd = new Random(42);
        for (int i = 0; i < n; i++) {
            leftKeys[i] = rnd.nextInt(100_000);
            rightKeys[i] = i; // unique build keys
            leftVals[i] = i * 2L;
            rightVals[i] = i * 10L;
        }

        CupOfTea leftDf = CupOfTea.fromVectors(List.of(
            new IntColumnVector("k", false, leftKeys, new BitSet(), n),
            new LongColumnVector("val_l", false, leftVals, new BitSet(), n)
        ));

        CupOfTea rightDf = CupOfTea.fromVectors(List.of(
            new IntColumnVector("k", false, rightKeys, new BitSet(), n),
            new LongColumnVector("val_r", false, rightVals, new BitSet(), n)
        ));

        // Test inner join
        CupOfTea inner = leftDf.innerJoin(rightDf, "k");
        assertThat(inner.size()).isGreaterThan(0);
        for (int r = 0; r < Math.min(1000, inner.size()); r++) {
            int k = (int) inner.getRow(r).get("k");
            long valR = (long) inner.getRow(r).get("val_r");
            assertThat(valR).isEqualTo(k * 10L);
        }

        // Test left join (exercises probeUniqueLeft interleaved execution)
        CupOfTea leftJoined = leftDf.leftJoin(rightDf, "k");
        assertThat(leftJoined.size()).isEqualTo(n);
        for (int r = 0; r < Math.min(1000, leftJoined.size()); r++) {
            int k = leftKeys[r];
            Object valR = leftJoined.getRow(r).get("val_r");
            if (k < n) {
                assertThat(valR).isEqualTo(k * 10L);
            } else {
                assertThat(valR).isNull();
            }
        }
    }

    @Test
    @DisplayName("Large LongColumnVector Join (>32k threshold) validates interleaved probing")
    void testLargeLongJoinInterleaved() {
        int n = 60_000;
        long[] leftKeys = new long[n];
        long[] rightKeys = new long[n];
        long[] leftVals = new long[n];

        Random rnd = new Random(99);
        for (int i = 0; i < n; i++) {
            leftKeys[i] = rnd.nextInt(80_000);
            rightKeys[i] = i;
            leftVals[i] = i * 3L;
        }

        CupOfTea leftDf = CupOfTea.fromVectors(List.of(
            new LongColumnVector("id", false, leftKeys, new BitSet(), n),
            new LongColumnVector("val_l", false, leftVals, new BitSet(), n)
        ));

        CupOfTea rightDf = CupOfTea.fromVectors(List.of(
            new LongColumnVector("id", false, rightKeys, new BitSet(), n),
            new LongColumnVector("val_r", false, leftVals, new BitSet(), n)
        ));

        CupOfTea leftJoined = leftDf.leftJoin(rightDf, "id");
        assertThat(leftJoined.size()).isEqualTo(n);

        CupOfTea innerJoined = leftDf.innerJoin(rightDf, "id");
        assertThat(innerJoined.size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Large StringColumnVector Join (>32k threshold) with SSO and non-SSO keys validates interleaved probing")
    void testLargeStringJoinInterleaved() {
        int n = 50_000;
        String[] leftKeys = new String[n];
        String[] rightKeys = new String[n];
        int[] leftVals = new int[n];

        Random rnd = new Random(12345);
        for (int i = 0; i < n; i++) {
            int num = rnd.nextInt(70_000);
            leftKeys[i] = (i % 2 == 0) ? ("short_" + num) : ("longer_than_sixteen_bytes_key_" + num);
            rightKeys[i] = (i % 2 == 0) ? ("short_" + i) : ("longer_than_sixteen_bytes_key_" + i);
            leftVals[i] = i;
        }

        CupOfTea leftDf = CupOfTea.fromVectors(List.of(
            new StringColumnVector("skey", false, leftKeys, new BitSet(), n),
            new IntColumnVector("val_l", false, leftVals, new BitSet(), n)
        ));

        CupOfTea rightDf = CupOfTea.fromVectors(List.of(
            new StringColumnVector("skey", false, rightKeys, new BitSet(), n),
            new IntColumnVector("val_r", false, leftVals, new BitSet(), n)
        ));

        CupOfTea leftJoined = leftDf.leftJoin(rightDf, "skey");
        assertThat(leftJoined.size()).isEqualTo(n);

        CupOfTea innerJoined = leftDf.innerJoin(rightDf, "skey");
        assertThat(innerJoined.size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Large GroupBy with findOrInsertBatch validates sequential and parallel partitioning")
    void testLargeGroupByBatchInterleaved() {
        int n = 100_000;
        int[] keys = new int[n];
        double[] vals = new double[n];

        Random rnd = new Random(777);
        for (int i = 0; i < n; i++) {
            keys[i] = rnd.nextInt(1000);
            vals[i] = 1.0;
        }

        CupOfTea dfSeq = CupOfTea.fromVectors(List.of(
            new IntColumnVector("grp", false, keys, new BitSet(), n),
            new DoubleColumnVector("v", false, vals, new BitSet(), n)
        ));

        CupOfTea aggSeq = dfSeq.groupBy("grp").agg(count("v").as("cnt"), sum("v").as("total"));
        assertThat(aggSeq.size()).isEqualTo(1000);

        CupOfTea dfPar = dfSeq.asParallel().withParallelThreshold(10_000);
        CupOfTea aggPar = dfPar.groupBy("grp").agg(count("v").as("cnt"), sum("v").as("total"));
        assertThat(aggPar.size()).isEqualTo(1000);

        for (int r = 0; r < aggSeq.size(); r++) {
            assertThat(((Number) aggSeq.getRow(r).get("cnt")).doubleValue())
                .isEqualTo(((Number) aggSeq.getRow(r).get("total")).doubleValue());
        }
    }
}
