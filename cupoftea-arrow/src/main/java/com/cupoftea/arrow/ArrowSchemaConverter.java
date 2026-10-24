package com.cupoftea.arrow;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;

import com.cupoftea.table.Column;
import com.cupoftea.table.DataType;
import com.cupoftea.table.interfaces.IDataFrame;

/**
 * Bidirectional converter between CupOfTea {@link com.cupoftea.table.Schema} and Apache Arrow {@link Schema}.
 */
public final class ArrowSchemaConverter {

    private ArrowSchemaConverter() {
    }

    /**
     * Converts a CupOfTea {@link com.cupoftea.table.Schema} to an Arrow {@link Schema}.
     */
    public static Schema toArrowSchema(com.cupoftea.table.Schema cupOfTeaSchema) {
        Objects.requireNonNull(cupOfTeaSchema, "cupOfTeaSchema cannot be null");
        List<Field> fields = new ArrayList<>();
        for (Column col : cupOfTeaSchema.getColumns()) {
            fields.add(toArrowField(col));
        }
        return new Schema(fields);
    }

    /**
     * Converts the schema of an {@link IDataFrame} to an Arrow {@link Schema}.
     */
    public static Schema toArrowSchema(IDataFrame dataFrame) {
        Objects.requireNonNull(dataFrame, "dataFrame cannot be null");
        List<Field> fields = new ArrayList<>();
        for (Column col : dataFrame.getHeaders()) {
            if (col.columnType() == BigDecimal.class) {
                int scale = 4;
                com.cupoftea.table.vector.ColumnVector cv = dataFrame.getColumnVector(col.columnName());
                if (cv != null) {
                    for (int i = 0; i < cv.size(); i++) {
                        if (!cv.isNull(i)) {
                            Object val = cv.getObject(i);
                            if (val instanceof BigDecimal bd) {
                                scale = Math.max(0, bd.scale());
                                break;
                            }
                        }
                    }
                }
                ArrowType arrowType = new ArrowType.Decimal(38, scale, 128);
                fields.add(new Field(col.columnName(), new FieldType(col.nullable(), arrowType, null), null));
            } else {
                fields.add(toArrowField(col));
            }
        }
        return new Schema(fields);
    }

    /**
     * Converts a CupOfTea {@link Column} to an Arrow {@link Field}.
     */
    public static Field toArrowField(Column column) {
        Objects.requireNonNull(column, "column cannot be null");
        String name = column.columnName();
        boolean nullable = column.nullable();
        Class<?> type = column.columnType();

        ArrowType arrowType = resolveArrowType(type);
        return new Field(name, new FieldType(nullable, arrowType, null), null);
    }

    /**
     * Resolves the corresponding ArrowType for a Java class.
     */
    public static ArrowType resolveArrowType(Class<?> clazz) {
        if (clazz == null) {
            return new ArrowType.Utf8();
        }
        if (clazz == int.class || clazz == Integer.class) {
            return new ArrowType.Int(32, true);
        }
        if (clazz == long.class || clazz == Long.class) {
            return new ArrowType.Int(64, true);
        }
        if (clazz == double.class || clazz == Double.class) {
            return new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE);
        }
        if (clazz == float.class || clazz == Float.class) {
            return new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE);
        }
        if (clazz == boolean.class || clazz == Boolean.class) {
            return new ArrowType.Bool();
        }
        if (clazz == short.class || clazz == Short.class) {
            return new ArrowType.Int(16, true);
        }
        if (clazz == byte.class || clazz == Byte.class) {
            return new ArrowType.Int(8, true);
        }
        if (clazz == String.class) {
            return new ArrowType.Utf8();
        }
        if (clazz == LocalDate.class) {
            return new ArrowType.Date(DateUnit.DAY);
        }
        if (clazz == Date.class) {
            return new ArrowType.Date(DateUnit.MILLISECOND);
        }
        if (clazz == LocalDateTime.class) {
            return new ArrowType.Timestamp(TimeUnit.MICROSECOND, null);
        }
        if (clazz == Instant.class) {
            return new ArrowType.Timestamp(TimeUnit.MICROSECOND, "UTC");
        }
        if (clazz == BigDecimal.class) {
            return new ArrowType.Decimal(38, 9, 128);
        }
        return new ArrowType.Utf8();
    }

    /**
     * Converts an Arrow {@link Schema} to a CupOfTea {@link com.cupoftea.table.Schema}.
     */
    public static com.cupoftea.table.Schema toCupOfTeaSchema(Schema arrowSchema) {
        Objects.requireNonNull(arrowSchema, "arrowSchema cannot be null");
        List<Column> columns = new ArrayList<>();
        for (Field field : arrowSchema.getFields()) {
            columns.add(toCupOfTeaColumn(field));
        }
        return new com.cupoftea.table.Schema(columns);
    }

    /**
     * Converts an Arrow {@link Field} to a CupOfTea {@link Column}.
     */
    public static Column toCupOfTeaColumn(Field field) {
        Objects.requireNonNull(field, "field cannot be null");
        String name = field.getName();
        boolean nullable = field.isNullable();
        Class<?> javaType = resolveJavaType(field.getType());
        return new Column(name, javaType, nullable);
    }

    /**
     * Resolves the corresponding Java class for an ArrowType.
     */
    public static Class<?> resolveJavaType(ArrowType arrowType) {
        if (arrowType == null) {
            return Object.class;
        }
        if (arrowType instanceof ArrowType.Int intType) {
            int bitWidth = intType.getBitWidth();
            if (bitWidth <= 8) {
                return Byte.class;
            } else if (bitWidth <= 16) {
                return Short.class;
            } else if (bitWidth <= 32) {
                return Integer.class;
            } else {
                return Long.class;
            }
        }
        if (arrowType instanceof ArrowType.FloatingPoint fp) {
            if (fp.getPrecision() == FloatingPointPrecision.SINGLE) {
                return Float.class;
            } else if (fp.getPrecision() == FloatingPointPrecision.DOUBLE) {
                return Double.class;
            } else {
                return Float.class;
            }
        }
        if (arrowType instanceof ArrowType.Bool) {
            return Boolean.class;
        }
        if (arrowType instanceof ArrowType.Utf8 || arrowType instanceof ArrowType.LargeUtf8) {
            return String.class;
        }
        if (arrowType instanceof ArrowType.Date dateType) {
            return dateType.getUnit() == DateUnit.DAY ? LocalDate.class : Date.class;
        }
        if (arrowType instanceof ArrowType.Timestamp tsType) {
            return tsType.getTimezone() != null ? Instant.class : LocalDateTime.class;
        }
        if (arrowType instanceof ArrowType.Decimal) {
            return BigDecimal.class;
        }
        return Object.class;
    }
}
