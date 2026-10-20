package com.cupoftea.table;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.utils.interfaces.Order;
import com.cupoftea.utils.interfaces.Orderer;

class NATest {

    @Test
    @DisplayName("Should detect missing values via NA.isNA and NA.isMissing")
    void testIsNA() {
        assertThat(NA.isNA(null)).isTrue();
        assertThat(NA.isNA(NA.NA)).isTrue();
        assertThat(NA.isNA(NA.INSTANCE)).isTrue();
        assertThat(NA.isNA(Double.NaN)).isTrue();
        assertThat(NA.isNA(Float.NaN)).isTrue();

        assertThat(NA.isMissing(null)).isTrue();
        assertThat(NA.isMissing(NA.NA)).isTrue();

        // Empty string is NOT NA
        assertThat(NA.isNA("")).isFalse();
        assertThat(NA.isValue("")).isTrue();

        // Regular values are NOT NA
        assertThat(NA.isNA("hello")).isFalse();
        assertThat(NA.isNA(0)).isFalse();
        assertThat(NA.isNA(false)).isFalse();
        assertThat(NA.isValue(100)).isTrue();
    }

    @Test
    @DisplayName("Should sort NA / null values last in DataFrame.orderBy")
    void testOrderByWithNAValues() {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class);
        df.addColumn("score", Double.class);

        HashMap<String, Object> r1 = new HashMap<>();
        r1.put("id", 1);
        r1.put("score", null);
        df.addRow(r1);

        HashMap<String, Object> r2 = new HashMap<>();
        r2.put("id", 2);
        r2.put("score", 95.5);
        df.addRow(r2);

        HashMap<String, Object> r3 = new HashMap<>();
        r3.put("id", 3);
        r3.put("score", NA.NA);
        df.addRow(r3);

        HashMap<String, Object> r4 = new HashMap<>();
        r4.put("id", 4);
        r4.put("score", 82.0);
        df.addRow(r4);

        df.orderBy(new Orderer("score", Order.ASCENDING));

        assertThat(df.getRow(0).get("score")).isEqualTo(82.0);
        assertThat(df.getRow(1).get("score")).isEqualTo(95.5);
        assertThat(NA.isNA(df.getRow(2).get("score"))).isTrue();
        assertThat(NA.isNA(df.getRow(3).get("score"))).isTrue();
    }

    @Test
    @DisplayName("Should distinguish empty string from missing in DataFrame")
    void testEmptyStringDistinctFromMissing() {
        CupOfTea df = new CupOfTea();
        df.addColumn("name", String.class);

        HashMap<String, Object> r1 = new HashMap<>();
        r1.put("name", "");
        df.addRow(r1);

        HashMap<String, Object> r2 = new HashMap<>();
        r2.put("name", null);
        df.addRow(r2);

        assertThat(df.getRow(0).get("name")).isEqualTo("");
        assertThat(NA.isNA(df.getRow(0).get("name"))).isFalse();

        assertThat(df.getRow(1).get("name")).isNull();
        assertThat(NA.isNA(df.getRow(1).get("name"))).isTrue();
    }
}
