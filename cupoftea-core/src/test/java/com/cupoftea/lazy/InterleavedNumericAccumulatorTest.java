package com.cupoftea.lazy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.table.Aggregate;
import com.cupoftea.table.Aggregations;
import com.cupoftea.table.Column;
import com.cupoftea.table.vector.ColumnVector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class InterleavedNumericAccumulatorTest {

    @Test
    @DisplayName("Should maintain contiguous min, max, sum, and count statistics for each group")
    void testContiguousGroupInterleavedStats() {
        Aggregate[] aggs = new Aggregate[] {
            Aggregations.min("temperature").as("min_t"),
            Aggregations.max("temperature").as("max_t"),
            Aggregations.sum("temperature").as("sum_t"),
            Aggregations.count("temperature").as("cnt_t"),
            Aggregations.mean("temperature").as("mean_t")
        };

        List<Column> headers = List.of(
            new Column("station", String.class),
            new Column("temperature", Double.class)
        );

        StreamingDirectAggregator agg = new StreamingDirectAggregator(new String[]{"station"}, aggs, headers);

        byte[] stationA = "Berlin".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] stationB = "Paris".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // Group 0: Berlin -> 12.0, 18.5, 9.5
        agg.accumulate1Brc(stationA, 0, stationA.length, 12.0);
        agg.accumulate1Brc(stationA, 0, stationA.length, 18.5);
        agg.accumulate1Brc(stationA, 0, stationA.length, 9.5);

        // Group 1: Paris -> 22.0, 24.0
        agg.accumulate1Brc(stationB, 0, stationB.length, 22.0);
        agg.accumulate1Brc(stationB, 0, stationB.length, 24.0);

        var result = agg.finish().orderBy("station");
        assertThat(result.size()).isEqualTo(2);

        // Berlin
        var berlin = result.filter(r -> "Berlin".equals(r.get("station"))).getRow(0);
        assertThat(((Number) berlin.get("min_t")).doubleValue()).isCloseTo(9.5, offset(1e-4));
        assertThat(((Number) berlin.get("max_t")).doubleValue()).isCloseTo(18.5, offset(1e-4));
        assertThat(((Number) berlin.get("sum_t")).doubleValue()).isCloseTo(40.0, offset(1e-4));
        assertThat(((Number) berlin.get("cnt_t")).longValue()).isEqualTo(3L);
        assertThat(((Number) berlin.get("mean_t")).doubleValue()).isCloseTo(40.0 / 3.0, offset(1e-4));

        // Paris
        var paris = result.filter(r -> "Paris".equals(r.get("station"))).getRow(0);
        assertThat(((Number) paris.get("min_t")).doubleValue()).isCloseTo(22.0, offset(1e-4));
        assertThat(((Number) paris.get("max_t")).doubleValue()).isCloseTo(24.0, offset(1e-4));
        assertThat(((Number) paris.get("sum_t")).doubleValue()).isCloseTo(46.0, offset(1e-4));
        assertThat(((Number) paris.get("cnt_t")).longValue()).isEqualTo(2L);
        assertThat(((Number) paris.get("mean_t")).doubleValue()).isCloseTo(23.0, offset(1e-4));
    }

    @Test
    @DisplayName("Should support fixed-point scaled accumulation via updateAllScaled")
    void testScaledInterleavedAccumulation() {
        Aggregate[] aggs = new Aggregate[] {
            Aggregations.min("temperature").as("min_t"),
            Aggregations.max("temperature").as("max_t"),
            Aggregations.sum("temperature").as("sum_t"),
            Aggregations.count("temperature").as("cnt_t"),
            Aggregations.mean("temperature").as("mean_t")
        };

        List<Column> headers = List.of(
            new Column("station", String.class),
            new Column("temperature", Double.class)
        );

        StreamingDirectAggregator agg = new StreamingDirectAggregator(new String[]{"station"}, aggs, headers);

        byte[] station = "Tokyo".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // Scaled values: 153 with scale 1 (15.3), -45 with scale 1 (-4.5), 200 with scale 1 (20.0)
        agg.accumulate1BrcScaled(station, 0, station.length, 153, 1);
        agg.accumulate1BrcScaled(station, 0, station.length, -45, 1);
        agg.accumulate1BrcScaled(station, 0, station.length, 200, 1);

        var result = agg.finish();
        assertThat(result.size()).isEqualTo(1);

        var row = result.getRow(0);
        assertThat(((Number) row.get("min_t")).doubleValue()).isCloseTo(-4.5, offset(1e-4));
        assertThat(((Number) row.get("max_t")).doubleValue()).isCloseTo(20.0, offset(1e-4));
        assertThat(((Number) row.get("sum_t")).doubleValue()).isCloseTo(30.8, offset(1e-4));
        assertThat(((Number) row.get("cnt_t")).longValue()).isEqualTo(3L);
        assertThat(((Number) row.get("mean_t")).doubleValue()).isCloseTo(30.8 / 3.0, offset(1e-4));
    }

    @Test
    @DisplayName("Should correctly merge interleaved accumulators from multiple partitions")
    void testMergeInterleavedAccumulators() {
        Aggregate[] aggs = new Aggregate[] {
            Aggregations.min("val").as("min_v"),
            Aggregations.max("val").as("max_v"),
            Aggregations.sum("val").as("sum_v"),
            Aggregations.mean("val").as("mean_v"),
            Aggregations.count("val").as("cnt_v")
        };

        List<Column> headers = List.of(
            new Column("k", String.class),
            new Column("val", Double.class)
        );

        StreamingDirectAggregator agg1 = new StreamingDirectAggregator(new String[]{"k"}, aggs, headers);
        StreamingDirectAggregator agg2 = new StreamingDirectAggregator(new String[]{"k"}, aggs, headers);

        byte[] k1 = "K1".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        agg1.accumulate1Brc(k1, 0, k1.length, 10.0);
        agg1.accumulate1Brc(k1, 0, k1.length, 20.0);

        agg2.accumulate1Brc(k1, 0, k1.length, 5.0);
        agg2.accumulate1Brc(k1, 0, k1.length, 30.0);

        agg1.merge(agg2);

        var result = agg1.finish();
        assertThat(result.size()).isEqualTo(1);

        var row = result.getRow(0);
        assertThat(((Number) row.get("min_v")).doubleValue()).isCloseTo(5.0, offset(1e-4));
        assertThat(((Number) row.get("max_v")).doubleValue()).isCloseTo(30.0, offset(1e-4));
        assertThat(((Number) row.get("sum_v")).doubleValue()).isCloseTo(65.0, offset(1e-4));
        assertThat(((Number) row.get("cnt_v")).longValue()).isEqualTo(4L);
        assertThat(((Number) row.get("mean_v")).doubleValue()).isCloseTo(65.0 / 4.0, offset(1e-4));
    }
}
