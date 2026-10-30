package com.cupoftea.lazy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.util.HashMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.Aggregation;
import com.cupoftea.table.Aggregations;

public class LazyCupOfTeaTest {

    private CupOfTea createSampleDataFrame() {
        CupOfTea df = new CupOfTea();
        df = df.addColumn("station", String.class)
               .addColumn("temperature", Double.class)
               .addColumn("measurement_id", Integer.class);

        addRow(df, "Hamburg", 12.0, 1);
        addRow(df, "Bulawayo", 8.9, 2);
        addRow(df, "Hamburg", 14.5, 3);
        addRow(df, "Palembang", 38.8, 4);
        addRow(df, "Bulawayo", 10.1, 5);
        addRow(df, "Hamburg", 9.2, 6);
        addRow(df, "Palembang", 29.5, 7);
        addRow(df, "Hamburg", 15.0, 8);
        return df;
    }

    private void addRow(CupOfTea df, String station, Double temp, Integer id) {
        HashMap<String, Object> row = new HashMap<>();
        row.put("station", station);
        row.put("temperature", temp);
        row.put("measurement_id", id);
        df.addRow(row);
    }

    @Test
    @DisplayName("Should evaluate streaming groupBy and running aggregations lazily with collect()")
    void test_streaming_groupBy_agg_collect() {
        CupOfTea df = createSampleDataFrame();

        CupOfTea result = df.lazy()
                .batchSize(2) // Force streaming in chunks of 2 rows
                .groupBy("station")
                .agg(
                    Aggregations.min("temperature").as("min_temp"),
                    Aggregations.mean("temperature").as("mean_temp"),
                    Aggregations.max("temperature").as("max_temp"),
                    Aggregations.count("temperature").as("count_obs")
                )
                .collect()
                .orderBy("station");

        assertThat(result.size()).isEqualTo(3);

        // Hamburg: 12.0, 14.5, 9.2, 15.0 -> min: 9.2, max: 15.0, mean: 12.675, count: 4
        CupOfTea hamburg = result.filter(r -> "Hamburg".equals(r.get("station")));
        assertThat(((Number) hamburg.getRow(0).get("min_temp")).doubleValue()).isCloseTo(9.2, offset(1e-4));
        assertThat(((Number) hamburg.getRow(0).get("max_temp")).doubleValue()).isCloseTo(15.0, offset(1e-4));
        assertThat(((Number) hamburg.getRow(0).get("mean_temp")).doubleValue()).isCloseTo(12.675, offset(1e-4));
        assertThat(((Number) hamburg.getRow(0).get("count_obs")).longValue()).isEqualTo(4L);

        // Bulawayo: 8.9, 10.1 -> min: 8.9, max: 10.1, mean: 9.5, count: 2
        CupOfTea bulawayo = result.filter(r -> "Bulawayo".equals(r.get("station")));
        assertThat(((Number) bulawayo.getRow(0).get("min_temp")).doubleValue()).isCloseTo(8.9, offset(1e-4));
        assertThat(((Number) bulawayo.getRow(0).get("max_temp")).doubleValue()).isCloseTo(10.1, offset(1e-4));
        assertThat(((Number) bulawayo.getRow(0).get("mean_temp")).doubleValue()).isCloseTo(9.5, offset(1e-4));

        // Palembang: 38.8, 29.5 -> min: 29.5, max: 38.8, mean: 34.15, count: 2
        CupOfTea palembang = result.filter(r -> "Palembang".equals(r.get("station")));
        assertThat(((Number) palembang.getRow(0).get("min_temp")).doubleValue()).isCloseTo(29.5, offset(1e-4));
        assertThat(((Number) palembang.getRow(0).get("max_temp")).doubleValue()).isCloseTo(38.8, offset(1e-4));
    }

    @Test
    @DisplayName("Should evaluate streaming groupBy and running aggregations in parallel")
    void test_parallel_streaming_groupBy_agg() {
        CupOfTea df = createSampleDataFrame();

        CupOfTea result = df.lazy()
                .batchSize(2)
                .parallel()
                .groupBy("station")
                .agg(
                    Aggregations.min("temperature"),
                    Aggregations.mean("temperature"),
                    Aggregations.max("temperature")
                )
                .collect()
                .orderBy("station");

        assertThat(result.size()).isEqualTo(3);
        CupOfTea hamburg = result.filter(r -> "Hamburg".equals(r.get("station")));
        assertThat(((Number) hamburg.getRow(0).get("temperatureMin")).doubleValue()).isCloseTo(9.2, offset(1e-4));
        assertThat(((Number) hamburg.getRow(0).get("temperatureMax")).doubleValue()).isCloseTo(15.0, offset(1e-4));
    }

    @Test
    @DisplayName("Should support filtering before streaming aggregation")
    void test_filter_before_streaming_aggregation() {
        CupOfTea df = createSampleDataFrame();

        // Filter temperatures >= 10.0
        CupOfTea result = df.lazy()
                .batchSize(2)
                .filter(row -> ((Number) row.get("temperature")).doubleValue() >= 10.0)
                .groupBy("station")
                .agg(Aggregations.count("temperature").as("cnt"))
                .collect()
                .orderBy("station");

        // Hamburg: 12.0, 14.5, 15.0 (9.2 filtered out) -> 3
        CupOfTea hamburg = result.filter(r -> "Hamburg".equals(r.get("station")));
        assertThat(((Number) hamburg.getRow(0).get("cnt")).longValue()).isEqualTo(3L);

        // Bulawayo: 10.1 (8.9 filtered out) -> 1
        CupOfTea bulawayo = result.filter(r -> "Bulawayo".equals(r.get("station")));
        assertThat(((Number) bulawayo.getRow(0).get("cnt")).longValue()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should evaluate whole-DataFrame global aggregation lazily")
    void test_global_aggregation_lazy() {
        CupOfTea df = createSampleDataFrame();

        CupOfTea result = df.lazy()
                .batchSize(2)
                .agg(
                    Aggregations.min("temperature").as("global_min"),
                    Aggregations.max("temperature").as("global_max"),
                    Aggregations.mean("temperature").as("global_mean")
                )
                .collect();

        assertThat(result.size()).isEqualTo(1);
        assertThat(((Number) result.getRow(0).get("global_min")).doubleValue()).isCloseTo(8.9, offset(1e-4));
        assertThat(((Number) result.getRow(0).get("global_max")).doubleValue()).isCloseTo(38.8, offset(1e-4));
    }

    @Test
    @DisplayName("Should count matching rows lazily without full materialization")
    void test_lazy_count() {
        CupOfTea df = createSampleDataFrame();

        long total = df.lazy().batchSize(3).count();
        assertThat(total).isEqualTo(8L);

        long filtered = df.lazy().batchSize(2)
                .filter(r -> ((Number) r.get("temperature")).doubleValue() > 10.0)
                .count();
        assertThat(filtered).isEqualTo(6L);
    }

    @Test
    @DisplayName("Should collect and limit stream batches")
    void test_collect_with_limit() {
        CupOfTea df = createSampleDataFrame();

        CupOfTea result = df.lazy()
                .batchSize(2)
                .select("station", "temperature")
                .limit(3)
                .collect();

        assertThat(result.size()).isEqualTo(3);
        assertThat(result.getHeaders()).hasSize(2);
    }

    @Test
    @DisplayName("Should output explain execution plan")
    void test_explain_plan() {
        CupOfTea df = createSampleDataFrame();
        String plan = df.lazy()
                .filter(r -> true)
                .select("station", "temperature")
                .groupBy("station")
                .agg(Aggregation.MEAN.of("temperature"))
                .explain();

        assertThat(plan).contains("LazyExecutionPlan");
        assertThat(plan).contains("GroupBy: [station]");
        assertThat(plan).contains("Select: [station, temperature]");
    }
}
