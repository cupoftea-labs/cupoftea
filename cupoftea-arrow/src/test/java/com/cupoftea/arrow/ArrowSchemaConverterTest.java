package com.cupoftea.arrow;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.table.Column;

public class ArrowSchemaConverterTest {

    @Test
    @DisplayName("Should convert CupOfTea Schema to Arrow Schema with full type fidelity")
    void testToArrowSchema() {
        com.cupoftea.table.Schema cotSchema = com.cupoftea.table.Schema.of(
                "id", Integer.class,
                "count", Long.class,
                "price", Double.class,
                "ratio", Float.class,
                "active", Boolean.class,
                "name", String.class,
                "birthday", LocalDate.class,
                "event_time", LocalDateTime.class,
                "created_at", Instant.class,
                "amount", BigDecimal.class
        );

        Schema arrowSchema = ArrowSchemaConverter.toArrowSchema(cotSchema);
        assertThat(arrowSchema.getFields()).hasSize(10);

        Field idField = arrowSchema.findField("id");
        assertThat(idField.getType()).isEqualTo(new ArrowType.Int(32, true));
        assertThat(idField.isNullable()).isTrue();

        Field countField = arrowSchema.findField("count");
        assertThat(countField.getType()).isEqualTo(new ArrowType.Int(64, true));

        Field priceField = arrowSchema.findField("price");
        assertThat(priceField.getType()).isEqualTo(new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE));

        Field ratioField = arrowSchema.findField("ratio");
        assertThat(ratioField.getType()).isEqualTo(new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE));

        Field activeField = arrowSchema.findField("active");
        assertThat(activeField.getType()).isEqualTo(new ArrowType.Bool());

        Field nameField = arrowSchema.findField("name");
        assertThat(nameField.getType()).isEqualTo(new ArrowType.Utf8());

        Field birthdayField = arrowSchema.findField("birthday");
        assertThat(birthdayField.getType()).isEqualTo(new ArrowType.Date(DateUnit.DAY));

        Field eventField = arrowSchema.findField("event_time");
        assertThat(eventField.getType()).isInstanceOf(ArrowType.Timestamp.class);
        assertThat(((ArrowType.Timestamp) eventField.getType()).getTimezone()).isNull();

        Field createdField = arrowSchema.findField("created_at");
        assertThat(createdField.getType()).isInstanceOf(ArrowType.Timestamp.class);
        assertThat(((ArrowType.Timestamp) createdField.getType()).getTimezone()).isEqualTo("UTC");

        Field amountField = arrowSchema.findField("amount");
        assertThat(amountField.getType()).isInstanceOf(ArrowType.Decimal.class);
    }

    @Test
    @DisplayName("Should convert Arrow Schema to CupOfTea Schema")
    void testToCupOfTeaSchema() {
        Schema arrowSchema = new Schema(java.util.List.of(
                Field.nullable("col_int", new ArrowType.Int(32, true)),
                Field.nullable("col_bigint", new ArrowType.Int(64, true)),
                Field.nullable("col_float", new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE)),
                Field.nullable("col_double", new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)),
                Field.nullable("col_bool", new ArrowType.Bool()),
                Field.nullable("col_str", new ArrowType.Utf8()),
                Field.nullable("col_date", new ArrowType.Date(DateUnit.DAY)),
                Field.nullable("col_ts", new ArrowType.Timestamp(TimeUnit.MICROSECOND, "UTC")),
                Field.nullable("col_dec", new ArrowType.Decimal(38, 9, 128))
        ));

        com.cupoftea.table.Schema cotSchema = ArrowSchemaConverter.toCupOfTeaSchema(arrowSchema);
        assertThat(cotSchema.getColumns()).hasSize(9);

        assertThat(cotSchema.getColumn("col_int").columnType()).isEqualTo(Integer.class);
        assertThat(cotSchema.getColumn("col_bigint").columnType()).isEqualTo(Long.class);
        assertThat(cotSchema.getColumn("col_float").columnType()).isEqualTo(Float.class);
        assertThat(cotSchema.getColumn("col_double").columnType()).isEqualTo(Double.class);
        assertThat(cotSchema.getColumn("col_bool").columnType()).isEqualTo(Boolean.class);
        assertThat(cotSchema.getColumn("col_str").columnType()).isEqualTo(String.class);
        assertThat(cotSchema.getColumn("col_date").columnType()).isEqualTo(LocalDate.class);
        assertThat(cotSchema.getColumn("col_ts").columnType()).isEqualTo(Instant.class);
        assertThat(cotSchema.getColumn("col_dec").columnType()).isEqualTo(BigDecimal.class);
    }
}
