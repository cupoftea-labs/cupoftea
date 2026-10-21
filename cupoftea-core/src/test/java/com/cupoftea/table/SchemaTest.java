package com.cupoftea.table;

import static com.cupoftea.table.Schema.BOOLEAN;
import static com.cupoftea.table.Schema.DATE;
import static com.cupoftea.table.Schema.DATETIME;
import static com.cupoftea.table.Schema.DECIMAL;
import static com.cupoftea.table.Schema.DOUBLE;
import static com.cupoftea.table.Schema.FLOAT;
import static com.cupoftea.table.Schema.INSTANT;
import static com.cupoftea.table.Schema.INT;
import static com.cupoftea.table.Schema.LONG;
import static com.cupoftea.table.Schema.STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;

class SchemaTest {

    @Test
    @DisplayName("Should create schema using Schema.of with DataType constants")
    void testSchemaOfWithDataTypeConstants() {
        Schema schema = Schema.of("year", INT, "price", DOUBLE);

        assertThat(schema.size()).isEqualTo(2);
        assertThat(schema.getColumnNames()).containsExactly("year", "price");
        assertThat(schema.getType("year")).isEqualTo(Integer.class);
        assertThat(schema.getType("price")).isEqualTo(Double.class);
        assertThat(schema.getDataType("year")).isEqualTo(DataType.INT);
        assertThat(schema.getDataType("price")).isEqualTo(DataType.DOUBLE);
    }

    @Test
    @DisplayName("Should create schema using Schema.of with Class literals")
    void testSchemaOfWithClassLiterals() {
        Schema schema = Schema.of(
            "id", Long.class,
            "name", String.class,
            "active", Boolean.class,
            "rate", Float.class,
            "created", LocalDate.class,
            "timestamp", Instant.class,
            "salary", BigDecimal.class
        );

        assertThat(schema.size()).isEqualTo(7);
        assertThat(schema.getType("id")).isEqualTo(Long.class);
        assertThat(schema.getType("name")).isEqualTo(String.class);
        assertThat(schema.getType("active")).isEqualTo(Boolean.class);
        assertThat(schema.getType("rate")).isEqualTo(Float.class);
        assertThat(schema.getType("created")).isEqualTo(LocalDate.class);
        assertThat(schema.getType("timestamp")).isEqualTo(Instant.class);
        assertThat(schema.getType("salary")).isEqualTo(BigDecimal.class);
    }

    @Test
    @DisplayName("Should create schema using builder")
    void testSchemaBuilder() {
        Schema schema = Schema.builder()
            .add("year", INT)
            .add("price", DOUBLE)
            .add(new Column("notes", String.class))
            .build();

        assertThat(schema.size()).isEqualTo(3);
        assertThat(schema.hasColumn("year")).isTrue();
        assertThat(schema.hasColumn("price")).isTrue();
        assertThat(schema.hasColumn("notes")).isTrue();
        assertThat(schema.indexOf("price")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when Schema.of has odd number of arguments")
    void testSchemaOfOddArgumentsThrows() {
        assertThatThrownBy(() -> Schema.of("year", INT, "price"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("even number of arguments");
    }

    @Test
    @DisplayName("Should convert between Schema and Header")
    void testSchemaHeaderConversion() {
        Schema schema = Schema.of("col1", INT, "col2", STRING);
        Header header = schema.toHeader();

        assertThat(header.columnCount()).isEqualTo(2);
        assertThat(header.get("col1").columnType()).isEqualTo(Integer.class);
        assertThat(header.get("col2").columnType()).isEqualTo(String.class);

        Schema reconstructed = Schema.fromHeader(header);
        assertThat(reconstructed).isEqualTo(schema);
        assertThat(reconstructed.hashCode()).isEqualTo(schema.hashCode());
    }

    @Test
    @DisplayName("Should construct CupOfTea and DataFrame with explicit Schema")
    void testConstructCupOfTeaWithSchema() {
        Schema schema = Schema.of("year", INT, "price", DOUBLE);
        CupOfTea df = new CupOfTea(schema);

        assertThat(df.getSchema()).isEqualTo(schema);
        assertThat(df.getHeaders()).hasSize(2);
        assertThat(df.getColumnHeader("year").columnType()).isEqualTo(Integer.class);
        assertThat(df.getColumnHeader("price").columnType()).isEqualTo(Double.class);
    }

    @Test
    @DisplayName("Should set schema and coerce existing rows in DataFrame")
    void testSetSchemaCoercesExistingRows() {
        CupOfTea df = new CupOfTea();
        df.addColumn("year", String.class);
        df.addColumn("price", String.class);

        HashMap<String, Object> row = new HashMap<>();
        row.put("year", "2024");
        row.put("price", "99.95");
        df.addRow(row);

        Schema targetSchema = Schema.of("year", INT, "price", DOUBLE);
        df.setSchema(targetSchema);

        assertThat(df.getColumnHeader("year").columnType()).isEqualTo(Integer.class);
        assertThat(df.getColumnHeader("price").columnType()).isEqualTo(Double.class);
        assertThat(df.getRow(0).get("year")).isEqualTo(2024);
        assertThat(df.getRow(0).get("price")).isEqualTo(99.95);
    }
}
