package com.CupOfTea.table;

import static com.CupOfTea.table.Schema.DECIMAL;
import static com.CupOfTea.table.Schema.STRING;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.CupOfTea.CupOfTea;

class BigDecimalAggregationTest {

    @Test
    @DisplayName("Should perform exact currency sum with BigDecimal avoiding binary float rounding error")
    void testExactBigDecimalSum() {
        CupOfTea df = new CupOfTea();
        df.addColumn("account", String.class);
        df.addColumn("amount", BigDecimal.class);

        HashMap<String, Object> r1 = new HashMap<>();
        r1.put("account", "checking");
        r1.put("amount", new BigDecimal("58749.87"));
        df.addRow(r1);

        HashMap<String, Object> r2 = new HashMap<>();
        r2.put("account", "checking");
        r2.put("amount", new BigDecimal("65000.33"));
        df.addRow(r2);

        CupOfTea grouped = df.groupBy("account").sum("amount");

        assertThat(grouped.getColumnHeader("amountSum").columnType()).isEqualTo(BigDecimal.class);
        // With double, 58749.87 + 65000.33 = 123750.20000000001
        // With BigDecimal, it is exactly 123750.20!
        assertThat(grouped.getRow(0).get("amountSum")).isEqualTo(new BigDecimal("123750.20"));
    }

    @Test
    @DisplayName("Should perform exact BigDecimal average, min, max, and count skipping null and NA")
    void testBigDecimalAggregationsWithMissingValues() {
        CupOfTea df = new CupOfTea();
        df.addColumn("dept", String.class);
        df.addColumn("salary", BigDecimal.class);

        HashMap<String, Object> r1 = new HashMap<>();
        r1.put("dept", "eng");
        r1.put("salary", new BigDecimal("1000.00"));
        df.addRow(r1);

        HashMap<String, Object> r2 = new HashMap<>();
        r2.put("dept", "eng");
        r2.put("salary", null); // missing null
        df.addRow(r2);

        HashMap<String, Object> r3 = new HashMap<>();
        r3.put("dept", "eng");
        r3.put("salary", NA.NA); // missing NA
        df.addRow(r3);

        HashMap<String, Object> r4 = new HashMap<>();
        r4.put("dept", "eng");
        r4.put("salary", new BigDecimal("3000.00"));
        df.addRow(r4);

        GroupBy grouped = df.groupBy("dept");

        CupOfTea sumDf = grouped.sum("salary");
        assertThat(sumDf.getRow(0).get("salarySum")).isEqualTo(new BigDecimal("4000.00"));

        CupOfTea avgDf = df.groupBy("dept").average("salary");
        // (1000 + 3000) / 2 = 2000
        assertThat((BigDecimal) avgDf.getRow(0).get("salaryAverage")).isEqualByComparingTo("2000");

        CupOfTea maxDf = df.groupBy("dept").maximum("salary");
        assertThat(maxDf.getRow(0).get("salaryMaximum")).isEqualTo(new BigDecimal("3000.00"));

        CupOfTea minDf = df.groupBy("dept").minimum("salary");
        assertThat(minDf.getRow(0).get("salaryMinimum")).isEqualTo(new BigDecimal("1000.00"));

        CupOfTea countDf = df.groupBy("dept").count("salary");
        // only 2 non-null / non-NA rows
        assertThat(countDf.getRow(0).get("salaryCount")).isEqualTo(2);
    }
}
