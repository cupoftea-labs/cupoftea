package com.cupoftea.sql;

import java.math.BigDecimal;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

import com.cupoftea.table.Column;
import com.cupoftea.table.Schema;

public final class SqlTypeMapping {

    private SqlTypeMapping() {
    }

    public static Schema fromResultSetMetaData(ResultSetMetaData metaData) throws SQLException {
        int columnCount = metaData.getColumnCount();
        Column[] columns = new Column[columnCount];
        for (int i = 1; i <= columnCount; i++) {
            String colName = metaData.getColumnLabel(i);
            if (colName == null || colName.isEmpty()) {
                colName = metaData.getColumnName(i);
            }
            int sqlType = metaData.getColumnType(i);
            Class<?> javaType = mapSqlTypeToJavaClass(sqlType);
            boolean nullable = metaData.isNullable(i) != ResultSetMetaData.columnNoNulls;
            columns[i - 1] = new Column(colName, javaType, nullable);
        }
        return Schema.of(columns);
    }

    public static Class<?> mapSqlTypeToJavaClass(int sqlType) {
        switch (sqlType) {
            case Types.BIT:
            case Types.BOOLEAN:
                return Boolean.class;
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
                return Integer.class;
            case Types.BIGINT:
                return Long.class;
            case Types.FLOAT:
            case Types.REAL:
                return Float.class;
            case Types.DOUBLE:
            case Types.NUMERIC:
                return Double.class;
            case Types.DECIMAL:
                return BigDecimal.class;
            case Types.DATE:
                return LocalDate.class;
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return LocalDateTime.class;
            case Types.TIME:
            case Types.TIME_WITH_TIMEZONE:
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
            case Types.NCHAR:
            case Types.NVARCHAR:
            case Types.LONGNVARCHAR:
            case Types.CLOB:
            case Types.NCLOB:
                return String.class;
            default:
                return Object.class;
        }
    }

    public static String toSqlColumnDefinition(Column column) {
        String colName = escapeIdentifier(column.columnName());
        String sqlType = toSqlType(column.columnType());
        String nullable = column.nullable() ? "" : " NOT NULL";
        return colName + " " + sqlType + nullable;
    }

    public static String toSqlType(Class<?> javaType) {
        if (javaType == null) {
            return "VARCHAR(255)";
        }
        if (javaType == Integer.class || javaType == int.class) {
            return "INTEGER";
        }
        if (javaType == Long.class || javaType == long.class) {
            return "BIGINT";
        }
        if (javaType == Double.class || javaType == double.class) {
            return "DOUBLE PRECISION";
        }
        if (javaType == Float.class || javaType == float.class) {
            return "REAL";
        }
        if (javaType == Boolean.class || javaType == boolean.class) {
            return "BOOLEAN";
        }
        if (javaType == BigDecimal.class) {
            return "DECIMAL(38, 10)";
        }
        if (javaType == LocalDate.class) {
            return "DATE";
        }
        if (javaType == LocalDateTime.class) {
            return "TIMESTAMP";
        }
        if (javaType == Instant.class || javaType == Date.class) {
            return "TIMESTAMP";
        }
        if (javaType == String.class) {
            return "VARCHAR(255)";
        }
        return "VARCHAR(255)";
    }

    public static String escapeIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        return identifier.replace("\"", "\"\"");
    }
}
