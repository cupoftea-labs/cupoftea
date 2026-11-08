package com.cupoftea.lazy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.table.Aggregate;
import com.cupoftea.table.Aggregations;
import com.cupoftea.table.Column;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

public class RadixPartitionedAggTableTest {

    @Test
    @DisplayName("Should distribute distinct keys across 32 radix partitions according to hash bits")
    void testRadixPartitionDistribution() {
        RadixPartitionedAggTable table = new RadixPartitionedAggTable(16);

        int totalKeys = 1000;
        for (int i = 0; i < totalKeys; i++) {
            byte[] kb = ("Station_" + i).getBytes(StandardCharsets.UTF_8);
            table.accumulate(kb, 0, kb.length, (double) i);
        }

        assertThat(table.numGroups()).isEqualTo(totalKeys);

        // Verify partitions are populated
        int partitionsWithKeys = 0;
        for (int p = 0; p < RadixPartitionedAggTable.NUM_PARTITIONS; p++) {
            if (table.getPartition(p).numGroups() > 0) {
                partitionsWithKeys++;
            }
        }
        assertThat(partitionsWithKeys).isGreaterThanOrEqualTo(30);

        // Verify finishKeyVectors
        var keyVectors = table.finishKeyVectors(new String[]{"station"});
        assertThat(keyVectors).hasSize(1);
        ColumnVector kv = keyVectors.get(0);
        assertThat(kv).isInstanceOf(StringColumnVector.class);
        assertThat(kv.size()).isEqualTo(totalKeys);
    }

    @Test
    @DisplayName("Should merge another RadixPartitionedAggTable correctly")
    void testMergeRadixTables() {
        RadixPartitionedAggTable t1 = new RadixPartitionedAggTable(16);
        RadixPartitionedAggTable t2 = new RadixPartitionedAggTable(16);

        byte[] k1 = "StationA".getBytes(StandardCharsets.UTF_8);
        byte[] k2 = "StationB".getBytes(StandardCharsets.UTF_8);

        t1.accumulate(k1, 0, k1.length, 10.0);
        t1.accumulate(k1, 0, k1.length, 20.0);

        t2.accumulate(k1, 0, k1.length, 5.0);
        t2.accumulate(k1, 0, k1.length, 30.0);
        t2.accumulate(k2, 0, k2.length, 50.0);

        t1.merge(t2);
        assertThat(t1.numGroups()).isEqualTo(2);

        double[] stats = new double[t1.numGroups() * InterleavedNumericAccumulator.STATS_SIZE];
        boolean[] hv = new boolean[t1.numGroups()];
        t1.populateStats(stats, hv);

        // Find StationA and StationB in finishKeyVectors
        var keyVectors = t1.finishKeyVectors(new String[]{"station"});
        StringColumnVector kv = (StringColumnVector) keyVectors.get(0);
        int idxA = -1;
        int idxB = -1;
        for (int i = 0; i < kv.size(); i++) {
            if ("StationA".equals(kv.getString(i))) idxA = i;
            if ("StationB".equals(kv.getString(i))) idxB = i;
        }

        assertThat(idxA).isNotNegative();
        assertThat(idxB).isNotNegative();

        int bA = idxA * InterleavedNumericAccumulator.STATS_SIZE;
        assertThat(stats[bA + InterleavedNumericAccumulator.OFF_MIN]).isEqualTo(5.0);
        assertThat(stats[bA + InterleavedNumericAccumulator.OFF_MAX]).isEqualTo(30.0);
        assertThat(stats[bA + InterleavedNumericAccumulator.OFF_SUM]).isEqualTo(65.0);
        assertThat(stats[bA + InterleavedNumericAccumulator.OFF_COUNT]).isEqualTo(4.0);

        int bB = idxB * InterleavedNumericAccumulator.STATS_SIZE;
        assertThat(stats[bB + InterleavedNumericAccumulator.OFF_MIN]).isEqualTo(50.0);
        assertThat(stats[bB + InterleavedNumericAccumulator.OFF_MAX]).isEqualTo(50.0);
        assertThat(stats[bB + InterleavedNumericAccumulator.OFF_SUM]).isEqualTo(50.0);
        assertThat(stats[bB + InterleavedNumericAccumulator.OFF_COUNT]).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should merge FusedGroupAggTable into RadixPartitionedAggTable seamlessly")
    void testMergeFusedIntoRadix() {
        RadixPartitionedAggTable radix = new RadixPartitionedAggTable(16);
        FusedGroupAggTable fused = new FusedGroupAggTable(16);

        byte[] k1 = "Hamburg".getBytes(StandardCharsets.UTF_8);
        byte[] k2 = "Tokyo".getBytes(StandardCharsets.UTF_8);

        fused.accumulate(k1, 0, k1.length, 12.0);
        fused.accumulate(k1, 0, k1.length, 18.0);
        fused.accumulate(k2, 0, k2.length, 25.0);

        radix.merge(fused);
        assertThat(radix.numGroups()).isEqualTo(2);

        double[] stats = new double[radix.numGroups() * InterleavedNumericAccumulator.STATS_SIZE];
        boolean[] hv = new boolean[radix.numGroups()];
        radix.populateStats(stats, hv);

        var keyVectors = radix.finishKeyVectors(new String[]{"station"});
        StringColumnVector kv = (StringColumnVector) keyVectors.get(0);
        int idxHamburg = -1;
        int idxTokyo = -1;
        for (int i = 0; i < kv.size(); i++) {
            if ("Hamburg".equals(kv.getString(i))) idxHamburg = i;
            if ("Tokyo".equals(kv.getString(i))) idxTokyo = i;
        }

        assertThat(idxHamburg).isNotNegative();
        assertThat(idxTokyo).isNotNegative();

        int bH = idxHamburg * InterleavedNumericAccumulator.STATS_SIZE;
        assertThat(stats[bH + InterleavedNumericAccumulator.OFF_MIN]).isEqualTo(12.0);
        assertThat(stats[bH + InterleavedNumericAccumulator.OFF_MAX]).isEqualTo(18.0);
        assertThat(stats[bH + InterleavedNumericAccumulator.OFF_SUM]).isEqualTo(30.0);
        assertThat(stats[bH + InterleavedNumericAccumulator.OFF_COUNT]).isEqualTo(2.0);
    }

    @Test
    @DisplayName("StreamingDirectAggregator L1 micro-cache should accelerate skewed Pareto workloads")
    void testStreamingDirectAggregatorMicroCache() {
        Aggregate[] aggs = new Aggregate[]{
            Aggregations.min("val").as("min_val"),
            Aggregations.max("val").as("max_val"),
            Aggregations.sum("val").as("sum_val"),
            Aggregations.count("val").as("count_val")
        };
        java.util.List<Column> headers = java.util.List.of(
            new Column("station", String.class),
            new Column("val", double.class)
        );

        StreamingDirectAggregator agg = new StreamingDirectAggregator(new String[]{"station"}, aggs, headers);

        byte[] hotStation1 = "HotStation1".getBytes(StandardCharsets.UTF_8);
        byte[] hotStation2 = "HotStation2".getBytes(StandardCharsets.UTF_8);

        // Repeat updates to trigger L1 micro-cache hits
        for (int i = 0; i < 1000; i++) {
            agg.accumulate1Brc(hotStation1, 0, hotStation1.length, 10.0 + (i % 5));
            agg.accumulate1BrcScaled(hotStation2, 0, hotStation2.length, 200 + (i % 10), 1);
        }

        var res = agg.finish();
        assertThat(res.size()).isEqualTo(2);

        var minVec = res.getColumnVector("min_val");
        var maxVec = res.getColumnVector("max_val");
        var countVec = res.getColumnVector("count_val");

        int idx1 = "HotStation1".equals(res.getColumnVector("station").getString(0)) ? 0 : 1;
        int idx2 = 1 - idx1;

        assertThat(countVec.getDouble(idx1)).isEqualTo(1000.0);
        assertThat(minVec.getDouble(idx1)).isCloseTo(10.0, offset(1e-4));
        assertThat(maxVec.getDouble(idx1)).isCloseTo(14.0, offset(1e-4));

        assertThat(countVec.getDouble(idx2)).isEqualTo(1000.0);
        assertThat(minVec.getDouble(idx2)).isCloseTo(20.0, offset(1e-4));
        assertThat(maxVec.getDouble(idx2)).isCloseTo(20.9, offset(1e-4));
    }
}
