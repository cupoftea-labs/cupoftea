package com.cupoftea.lazy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

public class FusedGroupAggTableTest {

    @Test
    @DisplayName("Should accumulate double metrics directly into contiguous slots for short keys (SSO)")
    void testAccumulateShortKeys() {
        FusedGroupAggTable table = new FusedGroupAggTable(16);
        byte[] hamburg = "Hamburg".getBytes(StandardCharsets.UTF_8);
        byte[] bulawayo = "Bulawayo".getBytes(StandardCharsets.UTF_8);

        table.accumulate(hamburg, 0, hamburg.length, 12.0);
        table.accumulate(hamburg, 0, hamburg.length, 14.5);
        table.accumulate(hamburg, 0, hamburg.length, 9.2);
        table.accumulate(hamburg, 0, hamburg.length, 15.0);

        table.accumulate(bulawayo, 0, bulawayo.length, 8.9);
        table.accumulate(bulawayo, 0, bulawayo.length, 10.1);

        assertThat(table.numGroups()).isEqualTo(2);

        double[] stats = new double[table.numGroups() * InterleavedNumericAccumulator.STATS_SIZE];
        boolean[] hv = new boolean[table.numGroups()];
        table.populateStats(stats, hv);

        int gHamburg = table.findOrInsertString("Hamburg");
        int gBulawayo = table.findOrInsertString("Bulawayo");

        // Hamburg stats
        int hBase = gHamburg * InterleavedNumericAccumulator.STATS_SIZE;
        assertThat(stats[hBase + InterleavedNumericAccumulator.OFF_MIN]).isCloseTo(9.2, offset(1e-4));
        assertThat(stats[hBase + InterleavedNumericAccumulator.OFF_MAX]).isCloseTo(15.0, offset(1e-4));
        assertThat(stats[hBase + InterleavedNumericAccumulator.OFF_SUM]).isCloseTo(50.7, offset(1e-4));
        assertThat(stats[hBase + InterleavedNumericAccumulator.OFF_COUNT]).isEqualTo(4.0);

        // Bulawayo stats
        int bBase = gBulawayo * InterleavedNumericAccumulator.STATS_SIZE;
        assertThat(stats[bBase + InterleavedNumericAccumulator.OFF_MIN]).isCloseTo(8.9, offset(1e-4));
        assertThat(stats[bBase + InterleavedNumericAccumulator.OFF_MAX]).isCloseTo(10.1, offset(1e-4));
        assertThat(stats[bBase + InterleavedNumericAccumulator.OFF_SUM]).isCloseTo(19.0, offset(1e-4));
        assertThat(stats[bBase + InterleavedNumericAccumulator.OFF_COUNT]).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Should accumulate long keys (> 16 bytes) and trigger table resizing")
    void testAccumulateLongKeysAndResize() {
        FusedGroupAggTable table = new FusedGroupAggTable(16);

        // Insert many keys exceeding 16 bytes to force resizing and hash collisions
        int n = 100;
        for (int i = 0; i < n; i++) {
            String keyStr = "VeryLongStationIdentifierName_" + i;
            byte[] kb = keyStr.getBytes(StandardCharsets.UTF_8);
            table.accumulate(kb, 0, kb.length, (double) i);
            table.accumulate(kb, 0, kb.length, (double) (i + 10));
        }

        assertThat(table.numGroups()).isEqualTo(n);

        double[] stats = new double[table.numGroups() * InterleavedNumericAccumulator.STATS_SIZE];
        boolean[] hv = new boolean[table.numGroups()];
        table.populateStats(stats, hv);

        for (int i = 0; i < n; i++) {
            String keyStr = "VeryLongStationIdentifierName_" + i;
            int gid = table.findOrInsertString(keyStr);
            int base = gid * InterleavedNumericAccumulator.STATS_SIZE;

            assertThat(hv[gid]).isTrue();
            assertThat(stats[base + InterleavedNumericAccumulator.OFF_MIN]).isEqualTo((double) i);
            assertThat(stats[base + InterleavedNumericAccumulator.OFF_MAX]).isEqualTo((double) (i + 10));
            assertThat(stats[base + InterleavedNumericAccumulator.OFF_SUM]).isEqualTo((double) (2 * i + 10));
            assertThat(stats[base + InterleavedNumericAccumulator.OFF_COUNT]).isEqualTo(2.0);
        }
    }

    @Test
    @DisplayName("Should accumulate fixed-point scaled values with various decimal scales")
    void testAccumulateScaled() {
        FusedGroupAggTable table = new FusedGroupAggTable(16);
        byte[] station = "Reykjavik".getBytes(StandardCharsets.UTF_8);

        // 15.3, -4.5, 20.0
        table.accumulateScaled(station, 0, station.length, 153, 1);
        table.accumulateScaled(station, 0, station.length, -45, 1);
        table.accumulateScaled(station, 0, station.length, 200, 1);

        assertThat(table.numGroups()).isEqualTo(1);

        double[] stats = new double[table.numGroups() * InterleavedNumericAccumulator.STATS_SIZE];
        boolean[] hv = new boolean[table.numGroups()];
        table.populateStats(stats, hv);

        int base = 0;
        assertThat(stats[base + InterleavedNumericAccumulator.OFF_MIN]).isCloseTo(-4.5, offset(1e-4));
        assertThat(stats[base + InterleavedNumericAccumulator.OFF_MAX]).isCloseTo(20.0, offset(1e-4));
        assertThat(stats[base + InterleavedNumericAccumulator.OFF_SUM]).isCloseTo(30.8, offset(1e-4));
        assertThat(stats[base + InterleavedNumericAccumulator.OFF_COUNT]).isEqualTo(3.0);
    }

    @Test
    @DisplayName("Should merge two FusedGroupAggTables correctly")
    void testMergeTables() {
        FusedGroupAggTable table1 = new FusedGroupAggTable(16);
        FusedGroupAggTable table2 = new FusedGroupAggTable(16);

        byte[] k1 = "Station1".getBytes(StandardCharsets.UTF_8);
        byte[] k2 = "Station2".getBytes(StandardCharsets.UTF_8);

        table1.accumulate(k1, 0, k1.length, 10.0);
        table1.accumulate(k1, 0, k1.length, 20.0);

        table2.accumulate(k1, 0, k1.length, 5.0);
        table2.accumulate(k1, 0, k1.length, 30.0);
        table2.accumulate(k2, 0, k2.length, 100.0);

        table1.merge(table2);

        assertThat(table1.numGroups()).isEqualTo(2);

        double[] stats = new double[table1.numGroups() * InterleavedNumericAccumulator.STATS_SIZE];
        boolean[] hv = new boolean[table1.numGroups()];
        table1.populateStats(stats, hv);

        int g1 = table1.findOrInsertString("Station1");
        int g2 = table1.findOrInsertString("Station2");

        int b1 = g1 * InterleavedNumericAccumulator.STATS_SIZE;
        assertThat(stats[b1 + InterleavedNumericAccumulator.OFF_MIN]).isEqualTo(5.0);
        assertThat(stats[b1 + InterleavedNumericAccumulator.OFF_MAX]).isEqualTo(30.0);
        assertThat(stats[b1 + InterleavedNumericAccumulator.OFF_SUM]).isEqualTo(65.0);
        assertThat(stats[b1 + InterleavedNumericAccumulator.OFF_COUNT]).isEqualTo(4.0);

        int b2 = g2 * InterleavedNumericAccumulator.STATS_SIZE;
        assertThat(stats[b2 + InterleavedNumericAccumulator.OFF_MIN]).isEqualTo(100.0);
        assertThat(stats[b2 + InterleavedNumericAccumulator.OFF_MAX]).isEqualTo(100.0);
        assertThat(stats[b2 + InterleavedNumericAccumulator.OFF_SUM]).isEqualTo(100.0);
        assertThat(stats[b2 + InterleavedNumericAccumulator.OFF_COUNT]).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should generate StringColumnVector correctly on finishKeyVectors")
    void testFinishKeyVectors() {
        FusedGroupAggTable table = new FusedGroupAggTable(16);
        table.findOrInsertString("Alpha");
        table.findOrInsertString("Beta");
        table.findOrInsertNull();

        assertThat(table.numGroups()).isEqualTo(3);

        var vecs = table.finishKeyVectors(new String[]{"station"});
        assertThat(vecs).hasSize(1);

        ColumnVector vec = vecs.get(0);
        assertThat(vec).isInstanceOf(StringColumnVector.class);
        assertThat(vec.getString(0)).isEqualTo("Alpha");
        assertThat(vec.getString(1)).isEqualTo("Beta");
        assertThat(vec.isNull(2)).isTrue();
    }
}
