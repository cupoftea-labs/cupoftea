package com.cupoftea.table.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.table.Schema;
import static com.cupoftea.table.Schema.DOUBLE;
import static com.cupoftea.table.Schema.INT;

class ExpandingWindowTest {

    private CupOfTea createSampleData() {
        CupOfTea df = new CupOfTea(Schema.of("val", DOUBLE));
        for (double v : new double[]{10.0, 20.0, 30.0, 40.0, 50.0}) {
            Map<String, Object> row = new HashMap<>();
            row.put("val", v);
            df.addRow(row);
        }
        return df;
    }

    @Test
    @DisplayName("Expanding mean includes all historical rows")
    void testExpandingMean() {
        CupOfTea df = createSampleData();
        CupOfTea res = df.expanding().mean("val", "exp_mean");

        // row 0: [10] -> 10.0
        // row 1: [10, 20] -> 15.0
        // row 2: [10, 20, 30] -> 20.0
        // row 3: [10, 20, 30, 40] -> 25.0
        // row 4: [10, 20, 30, 40, 50] -> 30.0
        assertThat(((Number) res.getRow(0).get("exp_mean")).doubleValue()).isEqualTo(10.0);
        assertThat(((Number) res.getRow(1).get("exp_mean")).doubleValue()).isEqualTo(15.0);
        assertThat(((Number) res.getRow(2).get("exp_mean")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) res.getRow(3).get("exp_mean")).doubleValue()).isEqualTo(25.0);
        assertThat(((Number) res.getRow(4).get("exp_mean")).doubleValue()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("Expanding sum, min, max, count")
    void testExpandingBasicStats() {
        CupOfTea df = new CupOfTea(Schema.of("v", DOUBLE));
        for (double v : new double[]{20.0, 5.0, 50.0, 1.0}) {
            Map<String, Object> row = new HashMap<>();
            row.put("v", v);
            df.addRow(row);
        }

        CupOfTea minDf = df.copy().expanding().min("v");
        assertThat(((Number) minDf.getRow(0).get("v")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) minDf.getRow(1).get("v")).doubleValue()).isEqualTo(5.0);
        assertThat(((Number) minDf.getRow(2).get("v")).doubleValue()).isEqualTo(5.0);
        assertThat(((Number) minDf.getRow(3).get("v")).doubleValue()).isEqualTo(1.0);

        CupOfTea maxDf = df.copy().expanding().max("v");
        assertThat(((Number) maxDf.getRow(0).get("v")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) maxDf.getRow(1).get("v")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) maxDf.getRow(2).get("v")).doubleValue()).isEqualTo(50.0);
        assertThat(((Number) maxDf.getRow(3).get("v")).doubleValue()).isEqualTo(50.0);

        CupOfTea sumDf = df.copy().expanding().sum("v");
        assertThat(((Number) sumDf.getRow(0).get("v")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) sumDf.getRow(1).get("v")).doubleValue()).isEqualTo(25.0);
        assertThat(((Number) sumDf.getRow(2).get("v")).doubleValue()).isEqualTo(75.0);
        assertThat(((Number) sumDf.getRow(3).get("v")).doubleValue()).isEqualTo(76.0);

        CupOfTea countDf = df.copy().expanding().count("v");
        assertThat(((Number) countDf.getRow(0).get("v")).intValue()).isEqualTo(1);
        assertThat(((Number) countDf.getRow(1).get("v")).intValue()).isEqualTo(2);
        assertThat(((Number) countDf.getRow(2).get("v")).intValue()).isEqualTo(3);
        assertThat(((Number) countDf.getRow(3).get("v")).intValue()).isEqualTo(4);
    }

    @Test
    @DisplayName("Expanding window with minPeriods padding")
    void testExpandingMinPeriods() {
        CupOfTea df = createSampleData();
        CupOfTea res = df.expanding(3).mean("val");

        assertThat(res.getRow(0).get("val")).isNull();
        assertThat(res.getRow(1).get("val")).isNull();
        assertThat(((Number) res.getRow(2).get("val")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) res.getRow(3).get("val")).doubleValue()).isEqualTo(25.0);
        assertThat(((Number) res.getRow(4).get("val")).doubleValue()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("Expanding window with COPY_ON_WRITE mode")
    void testExpandingCopyOnWrite() {
        CupOfTea df = createSampleData().asCopyOnWrite();
        CupOfTea res = df.expanding().sum("val");

        assertThat(res).isNotSameAs(df);
        assertThat(((Number) df.getRow(1).get("val")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) res.getRow(1).get("val")).doubleValue()).isEqualTo(30.0);
    }
}
