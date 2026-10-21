package com.cupoftea.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class RowTest {

    @Test
    @DisplayName("Should create Row using Row.of and access typed getters")
    void test_rowOf_and_typedGetters() {
        Date now = new Date();
        LocalDate localDate = LocalDate.of(2026, 10, 20);
        LocalDateTime localDateTime = LocalDateTime.of(2026, 10, 20, 15, 30);
        Instant instant = Instant.now();

        Row row = Row.of(
            "intVal", 42,
            "longVal", 10000000000L,
            "doubleVal", 3.14159,
            "floatVal", 2.718f,
            "boolVal", true,
            "strVal", "hello",
            "bigDecVal", new BigDecimal("12345.67"),
            "dateVal", now,
            "localDateVal", localDate,
            "localDateTimeVal", localDateTime,
            "instantVal", instant
        );

        assertThat(row.getInt("intVal")).isEqualTo(42);
        assertThat(row.getInteger("intVal")).isEqualTo(42);
        assertThat(row.getLong("longVal")).isEqualTo(10000000000L);
        assertThat(row.getDouble("doubleVal")).isEqualTo(3.14159);
        assertThat(row.getFloat("floatVal")).isEqualTo(2.718f);
        assertThat(row.getBoolean("boolVal")).isTrue();
        assertThat(row.getString("strVal")).isEqualTo("hello");
        assertThat(row.getBigDecimal("bigDecVal")).isEqualTo(new BigDecimal("12345.67"));
        assertThat(row.getDate("dateVal")).isEqualTo(now);
        assertThat(row.getLocalDate("localDateVal")).isEqualTo(localDate);
        assertThat(row.getLocalDateTime("localDateTimeVal")).isEqualTo(localDateTime);
        assertThat(row.getInstant("instantVal")).isEqualTo(instant);
        assertThat(row.getAs("strVal", String.class)).isEqualTo("hello");
        assertThat(row.containsKey("intVal")).isTrue();
        assertThat(row.containsKey("nonExistent")).isFalse();
        assertThat(row.getColumns()).contains("intVal", "strVal", "doubleVal");
    }

    @Test
    @DisplayName("Should coerce string representations to typed values")
    void test_typedGetters_stringCoercion() {
        Row row = Row.of(
            "intStr", "123",
            "longStr", "9876543210",
            "doubleStr", "45.67",
            "floatStr", "1.23",
            "boolStr", "true",
            "localDateStr", "2026-10-20",
            "instantStr", "2026-10-20T12:00:00Z"
        );

        assertThat(row.getInt("intStr")).isEqualTo(123);
        assertThat(row.getLong("longStr")).isEqualTo(9876543210L);
        assertThat(row.getDouble("doubleStr")).isEqualTo(45.67);
        assertThat(row.getFloat("floatStr")).isEqualTo(1.23f);
        assertThat(row.getBoolean("boolStr")).isTrue();
        assertThat(row.getLocalDate("localDateStr")).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(row.getInstant("instantStr")).isEqualTo(Instant.parse("2026-10-20T12:00:00Z"));
    }

    @Test
    @DisplayName("Should return null for missing or null columns")
    void test_typedGetters_nullValues() {
        Row row = Row.of("nullCol", null);

        assertThat(row.getInt("nullCol")).isNull();
        assertThat(row.getInt("missingCol")).isNull();
        assertThat(row.getLong("nullCol")).isNull();
        assertThat(row.getDouble("nullCol")).isNull();
        assertThat(row.getFloat("nullCol")).isNull();
        assertThat(row.getBoolean("nullCol")).isNull();
        assertThat(row.getString("nullCol")).isNull();
        assertThat(row.getDate("nullCol")).isNull();
        assertThat(row.getLocalDate("nullCol")).isNull();
        assertThat(row.getLocalDateTime("nullCol")).isNull();
        assertThat(row.getInstant("nullCol")).isNull();
        assertThat(row.getBigDecimal("nullCol")).isNull();
    }

    @Test
    @DisplayName("Should throw UnsupportedOperationException on any mutating operation")
    void test_immutability_throwsOnMutations() {
        Row row = Row.of("a", 1, "b", "two");

        assertThatThrownBy(() -> row.put("c", 3))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("unmodifiable");

        assertThatThrownBy(() -> row.put("a", 999))
            .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> row.remove("a"))
            .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(row::clear)
            .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> row.putAll(Map.of("x", 10)))
            .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> row.putIfAbsent("x", 10))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should encapsulate internal data and resist external map mutations")
    void test_encapsulation_isolatedFromSourceAndToMap() {
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("key", "initial");

        Row row = new Row(sourceMap);
        sourceMap.put("key", "modifiedExternal");

        // Row holds a defensive copy
        assertThat(row.getString("key")).isEqualTo("initial");

        Map<String, Object> exported = row.toMap();
        exported.put("key", "modifiedExported");

        // toMap returns a copy
        assertThat(row.getString("key")).isEqualTo("initial");
    }

    @Test
    @DisplayName("Should implement equals, hashCode, and toString properly")
    void test_equals_hashCode_toString() {
        Row row1 = Row.of("a", 1, "b", "two");
        Row row2 = Row.of("b", "two", "a", 1);
        Row row3 = Row.of("a", 2);

        assertThat(row1).isEqualTo(row2);
        assertThat(row1.hashCode()).isEqualTo(row2.hashCode());
        assertThat(row1).isNotEqualTo(row3);
        assertThat(row1.toString()).contains("a=1").contains("b=two");
    }

    @Test
    @DisplayName("Should return null for NA sentinel without throwing NumberFormatException")
    void test_typedGetters_naValues() {
        Row row = Row.of(
            "naVal", NA.NA,
            "nullVal", null,
            "nanDouble", Double.NaN,
            "nanFloat", Float.NaN,
            "emptyStr", ""
        );

        assertThat(row.getInt("naVal")).isNull();
        assertThat(row.getInteger("naVal")).isNull();
        assertThat(row.getLong("naVal")).isNull();
        assertThat(row.getDouble("naVal")).isNull();
        assertThat(row.getFloat("naVal")).isNull();
        assertThat(row.getBoolean("naVal")).isNull();
        assertThat(row.getString("naVal")).isNull();
        assertThat(row.getDate("naVal")).isNull();
        assertThat(row.getLocalDate("naVal")).isNull();
        assertThat(row.getLocalDateTime("naVal")).isNull();
        assertThat(row.getInstant("naVal")).isNull();
        assertThat(row.getBigDecimal("naVal")).isNull();
        assertThat(row.getAs("naVal", String.class)).isNull();

        // Empty string is preserved
        assertThat(row.getString("emptyStr")).isEqualTo("");
        assertThat(row.isNA("emptyStr")).isFalse();

        // isNA / isNull / isMissing
        assertThat(row.isNA("naVal")).isTrue();
        assertThat(row.isMissing("naVal")).isTrue();
        assertThat(row.isNull("naVal")).isFalse(); // not null, but NA

        assertThat(row.isNA("nullVal")).isTrue();
        assertThat(row.isNull("nullVal")).isTrue();

        assertThat(row.isNA("nanDouble")).isTrue();
        assertThat(row.isNA("nanFloat")).isTrue();
    }

    @Test
    @DisplayName("Should support positional accessors and negative indexing")
    void test_row_positionalGetters() {
        Row row = Row.of(
            "id", 101,
            "name", "Alice",
            "score", 95.5,
            "active", true
        );

        assertThat(row.getColumnNames()).containsExactly("id", "name", "score", "active");

        // Column names by index
        assertThat(row.getColumnName(0)).isEqualTo("id");
        assertThat(row.getColumnName(1)).isEqualTo("name");
        assertThat(row.getColumnName(2)).isEqualTo("score");
        assertThat(row.getColumnName(3)).isEqualTo("active");
        assertThat(row.getColumnName(-1)).isEqualTo("active");
        assertThat(row.getColumnName(-2)).isEqualTo("score");

        // Positional getters
        assertThat(row.get(0)).isEqualTo(101);
        assertThat(row.getInt(0)).isEqualTo(101);
        assertThat(row.getInteger(0)).isEqualTo(101);
        assertThat(row.getString(1)).isEqualTo("Alice");
        assertThat(row.getDouble(2)).isEqualTo(95.5);
        assertThat(row.getBoolean(3)).isTrue();

        // Negative indexing in getters
        assertThat(row.getBoolean(-1)).isTrue();
        assertThat(row.getDouble(-2)).isEqualTo(95.5);
        assertThat(row.getString(-3)).isEqualTo("Alice");
        assertThat(row.getInt(-4)).isEqualTo(101);

        // Positional NA checks
        assertThat(row.isNA(0)).isFalse();
        assertThat(row.isNull(0)).isFalse();
    }

    @Test
    @DisplayName("Should throw IndexOutOfBoundsException on invalid positional column index")
    void test_row_positionalIndexOutOfBounds() {
        Row row = Row.of("a", 1, "b", 2);

        assertThatThrownBy(() -> row.getColumnName(2))
            .isInstanceOf(IndexOutOfBoundsException.class)
            .hasMessageContaining("out of bounds");

        assertThatThrownBy(() -> row.getColumnName(-3))
            .isInstanceOf(IndexOutOfBoundsException.class)
            .hasMessageContaining("out of bounds");

        assertThatThrownBy(() -> row.getInt(5))
            .isInstanceOf(IndexOutOfBoundsException.class);
    }
}
