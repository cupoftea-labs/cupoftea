package com.cupoftea.importers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.interfaces.ISqlImporter;
import com.cupoftea.sql.SqlTypeMapping;
import com.cupoftea.sql.configurations.SqlImporterConfiguration;
import com.cupoftea.table.Column;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.Schema;
import com.cupoftea.table.vector.ColumnVector;

public class SqlImporter implements ISqlImporter {

    private final CupOfTea target;

    public SqlImporter(CupOfTea target) {
        this.target = target;
    }

    @Override
    public void importFromResultSet(ResultSet rs) {
        importFromResultSet(rs, new SqlImporterConfiguration());
    }

    @Override
    public void importFromResultSet(ResultSet rs, SqlImporterConfiguration config) {
        if (rs == null) {
            throw new ImportException("ResultSet cannot be null");
        }
        if (config == null) {
            config = new SqlImporterConfiguration();
        }

        try {
            ResultSetMetaData metaData = rs.getMetaData();
            Schema schema = config.getSchema() != null
                    ? config.getSchema()
                    : SqlTypeMapping.fromResultSetMetaData(metaData);

            int columnCount = schema.size();
            List<ColumnVector> vectors = new ArrayList<>(columnCount);
            List<Column> columns = schema.getColumns();
            for (Column col : columns) {
                vectors.add(ColumnVector.create(col, 16));
            }

            int rowCount = 0;
            int maxRows = config.getMaxRows();

            while (rs.next()) {
                if (maxRows > 0 && rowCount >= maxRows) {
                    break;
                }
                for (int i = 0; i < columnCount; i++) {
                    Column col = columns.get(i);
                    int colIndex = i + 1;
                    Object value = extractValue(rs, colIndex, col.columnType());
                    if (value == null) {
                        vectors.get(i).appendNull();
                    } else {
                        vectors.get(i).appendObject(value);
                    }
                }
                rowCount++;
            }

            DataFrame df = new DataFrame(vectors);
            if (target != null) {
                target.setDataFrame(df);
            }
        } catch (SQLException e) {
            throw new ImportException("Failed to import data from ResultSet: " + e.getMessage(), e);
        }
    }

    @Override
    public void importFromQuery(Connection conn, String query) {
        importFromQuery(conn, query, new SqlImporterConfiguration());
    }

    @Override
    public void importFromQuery(Connection conn, String query, SqlImporterConfiguration config) {
        if (conn == null) {
            throw new ImportException("Connection cannot be null");
        }
        if (query == null || query.trim().isEmpty()) {
            throw new ImportException("Query cannot be null or empty");
        }
        if (config == null) {
            config = new SqlImporterConfiguration();
        }

        try (Statement stmt = conn.createStatement()) {
            if (config.getFetchSize() > 0) {
                stmt.setFetchSize(config.getFetchSize());
            }
            if (config.getMaxRows() > 0) {
                stmt.setMaxRows(config.getMaxRows());
            }
            if (config.getQueryTimeout() > 0) {
                stmt.setQueryTimeout(config.getQueryTimeout());
            }

            try (ResultSet rs = stmt.executeQuery(query)) {
                importFromResultSet(rs, config);
            }
        } catch (SQLException e) {
            throw new ImportException("Failed to execute SQL query: " + e.getMessage(), e);
        }
    }

    @Override
    public void importFromQuery(DataSource dataSource, String query) {
        importFromQuery(dataSource, query, new SqlImporterConfiguration());
    }

    @Override
    public void importFromQuery(DataSource dataSource, String query, SqlImporterConfiguration config) {
        if (dataSource == null) {
            throw new ImportException("DataSource cannot be null");
        }
        try (Connection conn = dataSource.getConnection()) {
            importFromQuery(conn, query, config);
        } catch (SQLException e) {
            throw new ImportException("Failed to get connection from DataSource: " + e.getMessage(), e);
        }
    }

    @Override
    public void importFromTable(Connection conn, String tableName) {
        importFromTable(conn, tableName, new SqlImporterConfiguration());
    }

    @Override
    public void importFromTable(Connection conn, String tableName, SqlImporterConfiguration config) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new ImportException("Table name cannot be null or empty");
        }
        String query = "SELECT * FROM " + tableName.trim();
        importFromQuery(conn, query, config);
    }

    @Override
    public void importFromTable(DataSource dataSource, String tableName) {
        importFromTable(dataSource, tableName, new SqlImporterConfiguration());
    }

    @Override
    public void importFromTable(DataSource dataSource, String tableName, SqlImporterConfiguration config) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new ImportException("Table name cannot be null or empty");
        }
        String query = "SELECT * FROM " + tableName.trim();
        importFromQuery(dataSource, query, config);
    }

    private static Object extractValue(ResultSet rs, int columnIndex, Class<?> targetType) throws SQLException {
        Object raw = rs.getObject(columnIndex);
        if (raw == null || rs.wasNull()) {
            return null;
        }

        if (targetType == Integer.class || targetType == int.class) {
            int val = rs.getInt(columnIndex);
            return rs.wasNull() ? null : val;
        }
        if (targetType == Long.class || targetType == long.class) {
            long val = rs.getLong(columnIndex);
            return rs.wasNull() ? null : val;
        }
        if (targetType == Double.class || targetType == double.class) {
            double val = rs.getDouble(columnIndex);
            return rs.wasNull() ? null : val;
        }
        if (targetType == Float.class || targetType == float.class) {
            float val = rs.getFloat(columnIndex);
            return rs.wasNull() ? null : val;
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            boolean val = rs.getBoolean(columnIndex);
            return rs.wasNull() ? null : val;
        }
        if (targetType == String.class) {
            return rs.getString(columnIndex);
        }
        if (targetType == BigDecimal.class) {
            return rs.getBigDecimal(columnIndex);
        }
        if (targetType == LocalDate.class) {
            Date d = rs.getDate(columnIndex);
            return (d != null && !rs.wasNull()) ? d.toLocalDate() : null;
        }
        if (targetType == LocalDateTime.class) {
            Timestamp ts = rs.getTimestamp(columnIndex);
            return (ts != null && !rs.wasNull()) ? ts.toLocalDateTime() : null;
        }
        if (targetType == Instant.class) {
            Timestamp ts = rs.getTimestamp(columnIndex);
            return (ts != null && !rs.wasNull()) ? ts.toInstant() : null;
        }

        return raw;
    }
}
