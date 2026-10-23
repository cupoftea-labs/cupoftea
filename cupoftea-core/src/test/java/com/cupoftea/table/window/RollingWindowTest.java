package com.cupoftea.table.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.exceptions.SchemaMismatchException;
import com.cupoftea.table.Schema;
import static com.cupoftea.table.Schema.DOUBLE;
import static com.cupoftea.table.Schema.INT;
import static com.cupoftea.table.Schema.DECIMAL;
import static com.cupoftea.table.Schema.STRING;

class RollingWindowTest {

    private CupOfTea createSampleData() {
        CupOfTea df = new CupOfTea(Schema.of("price", DOUBLE));
        for (double p : new double[]{10.0, 20.0, 30.0, 40.0, 50.0}) {
            Map<String, Object> row = new HashMap<>();
            row.put("price", p);
            df.addRow(row);
        }
        return df;
    }

    @Test
    @DisplayName("Rolling sum with backward window and default minPeriods")
    void testRollingSumBackwardDefault() {
        CupOfTea df = createSampleData();
        CupOfTea result = df.rolling(3).sum("price");

        // Window size 3:
        // row 0: [10] -> null (< 3)
        // row 1: [10, 20] -> null (< 3)
        // row 2: [10, 20, 30] -> 60.0
        // row 3: [20, 30, 40] -> 90.0
        // row 4: [30, 40, 50] -> 120.0
        assertThat(result.getRow(0).get("price")).isNull();
        assertThat(result.getRow(1).get("price")).isNull();
        assertThat(((Number) result.getRow(2).get("price")).doubleValue()).isEqualTo(60.0);
        assertThat(((Number) result.getRow(3).get("price")).doubleValue()).isEqualTo(90.0);
        assertThat(((Number) result.getRow(4).get("price")).doubleValue()).isEqualTo(120.0);
    }

    @Test
    @DisplayName("Rolling sum with minPeriods = 1")
    void testRollingSumMinPeriods1() {
        CupOfTea df = createSampleData();
        CupOfTea result = df.rolling(3).minPeriods(1).sum("price");

        assertThat(((Number) result.getRow(0).get("price")).doubleValue()).isEqualTo(10.0);
        assertThat(((Number) result.getRow(1).get("price")).doubleValue()).isEqualTo(30.0);
        assertThat(((Number) result.getRow(2).get("price")).doubleValue()).isEqualTo(60.0);
        assertThat(((Number) result.getRow(3).get("price")).doubleValue()).isEqualTo(90.0);
        assertThat(((Number) result.getRow(4).get("price")).doubleValue()).isEqualTo(120.0);
    }

    @Test
    @DisplayName("Rolling mean with custom output column")
    void testRollingMeanWithOutputColumn() {
        CupOfTea df = createSampleData();
        CupOfTea result = df.rolling(3).minPeriods(1).mean("price", "price_roll_mean");

        // original column is preserved
        assertThat(((Number) result.getRow(0).get("price")).doubleValue()).isEqualTo(10.0);
        // rolling mean
        assertThat(((Number) result.getRow(0).get("price_roll_mean")).doubleValue()).isEqualTo(10.0);
        assertThat(((Number) result.getRow(1).get("price_roll_mean")).doubleValue()).isEqualTo(15.0);
        assertThat(((Number) result.getRow(2).get("price_roll_mean")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) result.getRow(3).get("price_roll_mean")).doubleValue()).isEqualTo(30.0);
        assertThat(((Number) result.getRow(4).get("price_roll_mean")).doubleValue()).isEqualTo(40.0);
    }

    @Test
    @DisplayName("Rolling min and max using monotonic deque (O(1))")
    void testRollingMinMax() {
        CupOfTea df = new CupOfTea(Schema.of("val", DOUBLE));
        for (double v : new double[]{5.0, 1.0, 4.0, 2.0, 8.0}) {
            Map<String, Object> row = new HashMap<>();
            row.put("val", v);
            df.addRow(row);
        }

        CupOfTea minDf = df.copy().rolling(3).minPeriods(1).min("val");
        // Windows for W=3, minPeriods=1:
        // row 0: [5] -> min 5
        // row 1: [5, 1] -> min 1
        // row 2: [5, 1, 4] -> min 1
        // row 3: [1, 4, 2] -> min 1
        // row 4: [4, 2, 8] -> min 2
        assertThat(((Number) minDf.getRow(0).get("val")).doubleValue()).isEqualTo(5.0);
        assertThat(((Number) minDf.getRow(1).get("val")).doubleValue()).isEqualTo(1.0);
        assertThat(((Number) minDf.getRow(2).get("val")).doubleValue()).isEqualTo(1.0);
        assertThat(((Number) minDf.getRow(3).get("val")).doubleValue()).isEqualTo(1.0);
        assertThat(((Number) minDf.getRow(4).get("val")).doubleValue()).isEqualTo(2.0);

        CupOfTea maxDf = df.copy().rolling(3).minPeriods(1).max("val");
        // row 0: [5] -> max 5
        // row 1: [5, 1] -> max 5
        // row 2: [5, 1, 4] -> max 5
        // row 3: [1, 4, 2] -> max 4
        // row 4: [4, 2, 8] -> max 8
        assertThat(((Number) maxDf.getRow(0).get("val")).doubleValue()).isEqualTo(5.0);
        assertThat(((Number) maxDf.getRow(1).get("val")).doubleValue()).isEqualTo(5.0);
        assertThat(((Number) maxDf.getRow(2).get("val")).doubleValue()).isEqualTo(5.0);
        assertThat(((Number) maxDf.getRow(3).get("val")).doubleValue()).isEqualTo(4.0);
        assertThat(((Number) maxDf.getRow(4).get("val")).doubleValue()).isEqualTo(8.0);
    }

    @Test
    @DisplayName("Rolling standard deviation and variance")
    void testRollingStdAndVariance() {
        CupOfTea df = new CupOfTea(Schema.of("val", DOUBLE));
        for (double v : new double[]{2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0}) {
            Map<String, Object> row = new HashMap<>();
            row.put("val", v);
            df.addRow(row);
        }

        CupOfTea stdDf = df.copy().rolling(4).minPeriods(4).std("val");
        assertThat(stdDf.getRow(0).get("val")).isNull();
        assertThat(stdDf.getRow(1).get("val")).isNull();
        assertThat(stdDf.getRow(2).get("val")).isNull();
        // Window [2, 4, 4, 4]: mean = 3.5, sample variance = ((2-3.5)^2 + 3*(4-3.5)^2)/3 = (2.25 + 0.75)/3 = 1.0, std = 1.0
        assertThat(((Number) stdDf.getRow(3).get("val")).doubleValue()).isCloseTo(1.0, within(1e-6));
    }

    @Test
    @DisplayName("Rolling median via dual heap")
    void testRollingMedian() {
        CupOfTea df = new CupOfTea(Schema.of("val", DOUBLE));
        for (double v : new double[]{10.0, 20.0, 100.0, 30.0, 40.0}) {
            Map<String, Object> row = new HashMap<>();
            row.put("val", v);
            df.addRow(row);
        }

        CupOfTea medDf = df.rolling(3).minPeriods(1).median("val");
        // row 0: [10] -> 10
        // row 1: [10, 20] -> 15
        // row 2: [10, 20, 100] -> 20
        // row 3: [20, 100, 30] -> 30
        // row 4: [100, 30, 40] -> 40
        assertThat(((Number) medDf.getRow(0).get("val")).doubleValue()).isEqualTo(10.0);
        assertThat(((Number) medDf.getRow(1).get("val")).doubleValue()).isEqualTo(15.0);
        assertThat(((Number) medDf.getRow(2).get("val")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) medDf.getRow(3).get("val")).doubleValue()).isEqualTo(30.0);
        assertThat(((Number) medDf.getRow(4).get("val")).doubleValue()).isEqualTo(40.0);
    }

    @Test
    @DisplayName("Centered and forward rolling windows")
    void testCenteredAndForwardWindows() {
        CupOfTea df = createSampleData(); // [10, 20, 30, 40, 50]

        // Centered window size 3:
        // row 0: [10, 20] -> mean 15.0
        // row 1: [10, 20, 30] -> mean 20.0
        // row 2: [20, 30, 40] -> mean 30.0
        // row 3: [30, 40, 50] -> mean 40.0
        // row 4: [40, 50] -> mean 45.0
        CupOfTea centered = df.copy().rolling(3).minPeriods(1).center().mean("price");
        assertThat(((Number) centered.getRow(0).get("price")).doubleValue()).isEqualTo(15.0);
        assertThat(((Number) centered.getRow(1).get("price")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) centered.getRow(2).get("price")).doubleValue()).isEqualTo(30.0);
        assertThat(((Number) centered.getRow(3).get("price")).doubleValue()).isEqualTo(40.0);
        assertThat(((Number) centered.getRow(4).get("price")).doubleValue()).isEqualTo(45.0);

        // Forward window size 3:
        // row 0: [10, 20, 30] -> mean 20.0
        // row 1: [20, 30, 40] -> mean 30.0
        // row 2: [30, 40, 50] -> mean 40.0
        // row 3: [40, 50] -> mean 45.0
        // row 4: [50] -> mean 50.0
        CupOfTea forward = df.copy().rolling(3).minPeriods(1).forward().mean("price");
        assertThat(((Number) forward.getRow(0).get("price")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) forward.getRow(1).get("price")).doubleValue()).isEqualTo(30.0);
        assertThat(((Number) forward.getRow(2).get("price")).doubleValue()).isEqualTo(40.0);
        assertThat(((Number) forward.getRow(3).get("price")).doubleValue()).isEqualTo(45.0);
        assertThat(((Number) forward.getRow(4).get("price")).doubleValue()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Sliding window with missing values and null policy")
    void testRollingWithNulls() {
        CupOfTea df = new CupOfTea(Schema.of("val", DOUBLE));
        Double[] vals = new Double[]{10.0, null, 30.0, 40.0, null};
        for (Double v : vals) {
            Map<String, Object> row = new HashMap<>();
            row.put("val", v);
            df.addRow(row);
        }

        // Window size 3, minPeriods = 2:
        // row 0: [10] -> valid 1 < 2 -> null
        // row 1: [10, null] -> valid 1 < 2 -> null
        // row 2: [10, null, 30] -> valid 2 >= 2 -> sum 40.0, mean 20.0
        // row 3: [null, 30, 40] -> valid 2 >= 2 -> sum 70.0, mean 35.0
        // row 4: [30, 40, null] -> valid 2 >= 2 -> sum 70.0, mean 35.0
        CupOfTea sumRes = df.copy().rolling(3).minPeriods(2).sum("val");
        assertThat(sumRes.getRow(0).get("val")).isNull();
        assertThat(sumRes.getRow(1).get("val")).isNull();
        assertThat(((Number) sumRes.getRow(2).get("val")).doubleValue()).isEqualTo(40.0);
        assertThat(((Number) sumRes.getRow(3).get("val")).doubleValue()).isEqualTo(70.0);
        assertThat(((Number) sumRes.getRow(4).get("val")).doubleValue()).isEqualTo(70.0);
    }

    @Test
    @DisplayName("Rolling window with BigDecimal exact precision")
    void testRollingBigDecimal() {
        CupOfTea df = new CupOfTea(Schema.of("amount", DECIMAL));
        for (String s : new String[]{"10.25", "20.50", "30.75"}) {
            Map<String, Object> row = new HashMap<>();
            row.put("amount", new BigDecimal(s));
            df.addRow(row);
        }

        CupOfTea res = df.rolling(2).minPeriods(1).sum("amount");
        assertThat(res.getRow(0).get("amount")).isEqualTo(new BigDecimal("10.25"));
        assertThat(res.getRow(1).get("amount")).isEqualTo(new BigDecimal("30.75"));
        assertThat(res.getRow(2).get("amount")).isEqualTo(new BigDecimal("51.25"));
    }

    @Test
    @DisplayName("Custom aggregation function with apply()")
    void testRollingApply() {
        CupOfTea df = createSampleData(); // [10, 20, 30, 40, 50]

        // Custom range: max - min
        CupOfTea res = df.rolling(3).minPeriods(3).apply("price", "range", (double[] window) -> {
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (double v : window) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
            return max - min;
        });

        assertThat(res.getRow(0).get("range")).isNull();
        assertThat(res.getRow(1).get("range")).isNull();
        assertThat(((Number) res.getRow(2).get("range")).doubleValue()).isEqualTo(20.0); // 30 - 10
        assertThat(((Number) res.getRow(3).get("range")).doubleValue()).isEqualTo(20.0); // 40 - 20
        assertThat(((Number) res.getRow(4).get("range")).doubleValue()).isEqualTo(20.0); // 50 - 30
    }

    @Test
    @DisplayName("Mutation modes: IN_PLACE vs COPY_ON_WRITE")
    void testMutationModes() {
        CupOfTea inPlaceDf = createSampleData().asInPlace();
        CupOfTea inPlaceRes = inPlaceDf.rolling(3).minPeriods(1).sum("price");
        assertThat(inPlaceRes).isSameAs(inPlaceDf);

        CupOfTea cowDf = createSampleData().asCopyOnWrite();
        CupOfTea cowRes = cowDf.rolling(3).minPeriods(1).sum("price");
        assertThat(cowRes).isNotSameAs(cowDf);
        // Original cowDf price should be untouched
        assertThat(((Number) cowDf.getRow(0).get("price")).doubleValue()).isEqualTo(10.0);
        assertThat(((Number) cowDf.getRow(1).get("price")).doubleValue()).isEqualTo(20.0);
    }
}
