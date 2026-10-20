package com.CupOfTea.table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Objects;

/**
 * Standard logical data types supported by CupOfTea schema and type inference.
 */
public enum DataType {
    INT(Integer.class, int.class),
    DOUBLE(Double.class, double.class),
    FLOAT(Float.class, float.class),
    LONG(Long.class, long.class),
    BOOLEAN(Boolean.class, boolean.class),
    STRING(String.class),
    DATE(LocalDate.class),
    DATETIME(LocalDateTime.class),
    INSTANT(Instant.class),
    DECIMAL(BigDecimal.class),
    OBJECT(Object.class);

    private final Class<?> javaType;
    private final Class<?> primitiveType;

    DataType(Class<?> javaType) {
        this(javaType, null);
    }

    DataType(Class<?> javaType, Class<?> primitiveType) {
        this.javaType = javaType;
        this.primitiveType = primitiveType;
    }

    public Class<?> getJavaType() {
        return javaType;
    }

    public Class<?> getPrimitiveType() {
        return primitiveType;
    }

    /**
     * Resolves the matching DataType for a given Java class.
     *
     * @param clazz the Java class
     * @return the corresponding DataType, or {@link #OBJECT} if unrecognized
     */
    public static DataType fromJavaType(Class<?> clazz) {
        if (clazz == null) {
            return OBJECT;
        }
        if (clazz == Integer.class || clazz == int.class) {
            return INT;
        }
        if (clazz == Double.class || clazz == double.class) {
            return DOUBLE;
        }
        if (clazz == Float.class || clazz == float.class) {
            return FLOAT;
        }
        if (clazz == Long.class || clazz == long.class) {
            return LONG;
        }
        if (clazz == Boolean.class || clazz == boolean.class) {
            return BOOLEAN;
        }
        if (clazz == String.class) {
            return STRING;
        }
        if (clazz == LocalDate.class) {
            return DATE;
        }
        if (clazz == LocalDateTime.class) {
            return DATETIME;
        }
        if (clazz == Instant.class) {
            return INSTANT;
        }
        if (clazz == BigDecimal.class) {
            return DECIMAL;
        }
        if (Date.class.isAssignableFrom(clazz)) {
            return DATETIME;
        }
        return OBJECT;
    }
}
