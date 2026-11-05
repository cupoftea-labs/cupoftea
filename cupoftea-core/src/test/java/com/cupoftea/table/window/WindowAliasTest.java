package com.cupoftea.table.window;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.Schema;
import static com.cupoftea.table.Schema.DOUBLE;

class WindowAliasTest {

    @Test
    @DisplayName("rolling_mean and rolling_sum convenience aliases")
    void testRollingAliases() {
        CupOfTea df = new CupOfTea(Schema.of("val", DOUBLE));
        for (double v : new double[]{10.0, 20.0, 30.0, 40.0, 50.0}) {
            Map<String, Object> row = new HashMap<>();
            row.put("val", v);
            df.addRow(row);
        }

        CupOfTea resMean = df.copy().rolling_mean("val", 3, "roll_mean");
        assertThat(((Number) resMean.getRow(2).get("roll_mean")).doubleValue()).isEqualTo(20.0);

        CupOfTea resSum = df.copy().rolling_sum("val", 3, "roll_sum");
        assertThat(((Number) resSum.getRow(2).get("roll_sum")).doubleValue()).isEqualTo(60.0);
    }
}
