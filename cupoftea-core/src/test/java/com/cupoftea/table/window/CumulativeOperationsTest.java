package com.cupoftea.table.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.exceptions.SchemaMismatchException;
import com.cupoftea.table.Schema;
import static com.cupoftea.table.Schema.DECIMAL;
import static com.cupoftea.table.Schema.DOUBLE;
import static com.cupoftea.table.Schema.INT;
import static com.cupoftea.table.Schema.STRING;

class CumulativeOperationsTest {

    @Test
    @DisplayName("cumsum with integer and double columns")
    void testCumSum() {
        CupOfTea df = new CupOfTea(Schema.of("a", INT, "b", DOUBLE));
        int[] aVals = {1, 2, 3, 4};
        double[] bVals = {10.5, 20.5, 30.5, 40.5};
        for (int i = 0; i < 4; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("a", aVals[i]);
            row.put("b", bVals[i]);
            df.addRow(row);
        }

        df.cumsum();

        assertThat(((Number) df.getRow(0).get("a")).intValue()).isEqualTo(1);
        assertThat(((Number) df.getRow(1).get("a")).intValue()).isEqualTo(3);
        assertThat(((Number) df.getRow(2).get("a")).intValue()).isEqualTo(6);
        assertThat(((Number) df.getRow(3).get("a")).intValue()).isEqualTo(10);

        assertThat(((Number) df.getRow(0).get("b")).doubleValue()).isEqualTo(10.5);
        assertThat(((Number) df.getRow(1).get("b")).doubleValue()).isEqualTo(31.0);
        assertThat(((Number) df.getRow(2).get("b")).doubleValue()).isEqualTo(61.5);
        assertThat(((Number) df.getRow(3).get("b")).doubleValue()).isEqualTo(102.0);
    }

    @Test
    @DisplayName("cumsum preserving nulls according to null policy")
    void testCumSumPreservingNulls() {
        CupOfTea df = new CupOfTea(Schema.of("x", INT));
        Integer[] vals = {10, null, 20, 5};
        for (Integer v : vals) {
            Map<String, Object> row = new HashMap<>();
            row.put("x", v);
            df.addRow(row);
        }

        df.cumsum("x");

        // Row 0: 10
        // Row 1: null (null is preserved, accumulator remains 10)
        // Row 2: 10 + 20 = 30
        // Row 3: 30 + 5 = 35
        assertThat(df.getRow(0).get("x")).isEqualTo(10);
        assertThat(df.getRow(1).get("x")).isNull();
        assertThat(df.getRow(2).get("x")).isEqualTo(30);
        assertThat(df.getRow(3).get("x")).isEqualTo(35);
    }

    @Test
    @DisplayName("cumprod with nulls")
    void testCumProd() {
        CupOfTea df = new CupOfTea(Schema.of("p", INT));
        Integer[] vals = {2, 3, null, 4};
        for (Integer v : vals) {
            Map<String, Object> row = new HashMap<>();
            row.put("p", v);
            df.addRow(row);
        }

        df.cumprod("p");

        assertThat(((Number) df.getRow(0).get("p")).intValue()).isEqualTo(2);
        assertThat(((Number) df.getRow(1).get("p")).intValue()).isEqualTo(6);
        assertThat(df.getRow(2).get("p")).isNull();
        assertThat(((Number) df.getRow(3).get("p")).intValue()).isEqualTo(24);
    }

    @Test
    @DisplayName("cummin and cummax with nulls")
    void testCumMinAndCumMax() {
        CupOfTea df = new CupOfTea(Schema.of("val", INT));
        Integer[] vals = {5, null, 3, 7, 2};
        for (Integer v : vals) {
            Map<String, Object> row = new HashMap<>();
            row.put("val", v);
            df.addRow(row);
        }

        CupOfTea minDf = df.copy().cummin("val");
        assertThat(((Number) minDf.getRow(0).get("val")).intValue()).isEqualTo(5);
        assertThat(minDf.getRow(1).get("val")).isNull();
        assertThat(((Number) minDf.getRow(2).get("val")).intValue()).isEqualTo(3);
        assertThat(((Number) minDf.getRow(3).get("val")).intValue()).isEqualTo(3);
        assertThat(((Number) minDf.getRow(4).get("val")).intValue()).isEqualTo(2);

        CupOfTea maxDf = df.copy().cummax("val");
        assertThat(((Number) maxDf.getRow(0).get("val")).intValue()).isEqualTo(5);
        assertThat(maxDf.getRow(1).get("val")).isNull();
        assertThat(((Number) maxDf.getRow(2).get("val")).intValue()).isEqualTo(5);
        assertThat(((Number) maxDf.getRow(3).get("val")).intValue()).isEqualTo(7);
        assertThat(((Number) maxDf.getRow(4).get("val")).intValue()).isEqualTo(7);
    }

    @Test
    @DisplayName("cumsum with BigDecimal exact currency arithmetic")
    void testCumSumBigDecimal() {
        CupOfTea df = new CupOfTea(Schema.of("salary", DECIMAL));
        for (String s : new String[]{"1000.50", "2000.25", null, "500.00"}) {
            Map<String, Object> row = new HashMap<>();
            row.put("salary", s != null ? new BigDecimal(s) : null);
            df.addRow(row);
        }

        df.cumsum("salary");

        assertThat(df.getRow(0).get("salary")).isEqualTo(new BigDecimal("1000.50"));
        assertThat(df.getRow(1).get("salary")).isEqualTo(new BigDecimal("3000.75"));
        assertThat(df.getRow(2).get("salary")).isNull();
        assertThat(df.getRow(3).get("salary")).isEqualTo(new BigDecimal("3500.75"));
    }

    @Test
    @DisplayName("Cumulative ops respect COPY_ON_WRITE immutability")
    void testCumulativeCopyOnWrite() {
        CupOfTea df = new CupOfTea(Schema.of("v", INT));
        for (int v : new int[]{1, 2, 3}) {
            Map<String, Object> row = new HashMap<>();
            row.put("v", v);
            df.addRow(row);
        }
        df.asCopyOnWrite();

        CupOfTea res = df.cumsum("v");
        assertThat(res).isNotSameAs(df);
        assertThat(((Number) df.getRow(1).get("v")).intValue()).isEqualTo(2);
        assertThat(((Number) res.getRow(1).get("v")).intValue()).isEqualTo(3);
    }

    @Test
    @DisplayName("cumsum on non-existent column throws SchemaMismatchException")
    void testNonExistentColumn() {
        CupOfTea df = new CupOfTea(Schema.of("a", INT));
        assertThatThrownBy(() -> df.cumsum("non_existent"))
                .isInstanceOf(SchemaMismatchException.class);
    }
}
