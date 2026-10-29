package com.cupoftea.exporters;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.interfaces.ISqlExporter;
import com.cupoftea.sql.SqlTypeMapping;
import com.cupoftea.sql.configurations.SqlExporterConfiguration;
import com.cupoftea.sql.configurations.SqlExporterConfiguration.SaveMode;
import com.cupoftea.table.Column;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.vector.ColumnVector;

public class SqlExporter implements ISqlExporter {

    private final CupOfTea cupOfTea;

    public SqlExporter(CupOfTea cupOfTea) {
        this.cupOfTea = cupOfTea;
    }

    @Override
    public void exportToTable(Connection conn, String tableName) {
        exportToTable(conn, tableName, new SqlExporterConfiguration());
    }

    @Override
    public void exportToTable(Connection conn, String tableName, SqlExporterConfiguration config) {
        if (conn == null) {
            throw new ExportException("Connection cannot be null");
        }
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new ExportException("Table name cannot be null or empty");
        }
        if (cupOfTea == null || cupOfTea.getDataFrame() == null) {
            throw new ExportException("No DataFrame available to export");
        }
        if (config == null) {
            config = new SqlExporterConfiguration();
        }

        IDataFrame df = cupOfTea.getDataFrame();
        List<Column> columns = df.getHeaders();
        int colCount = columns.size();
        int rowCount = df.size();

        if (colCount == 0) {
            throw new ExportException("Cannot export empty DataFrame with no columns");
        }

        String safeTableName = tableName.trim();
        boolean originalAutoCommit = true;

        try {
            originalAutoCommit = conn.getAutoCommit();
            if (config.isUseTransaction()) {
                conn.setAutoCommit(false);
            }

            // Check if table exists
            boolean tableExists = checkTableExists(conn, safeTableName);

            if (tableExists) {
                if (config.getSaveMode() == SaveMode.FAIL_IF_EXISTS) {
                    throw new ExportException("Table '" + safeTableName + "' already exists and SaveMode is FAIL_IF_EXISTS");
                } else if (config.getSaveMode() == SaveMode.OVERWRITE) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate("DELETE FROM " + safeTableName);
                    }
                }
            } else {
                if (config.isCreateTableIfNotExists()) {
                    createTable(conn, safeTableName, columns);
                } else {
                    throw new ExportException("Table '" + safeTableName + "' does not exist and createTableIfNotExists is false");
                }
            }

            if (rowCount > 0) {
                insertRows(conn, safeTableName, df, columns, config.getBatchSize());
            }

            if (config.isUseTransaction()) {
                conn.commit();
            }
        } catch (SQLException e) {
            if (config.isUseTransaction()) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    // Suppress rollback exception to preserve primary cause
                }
            }
            throw new ExportException("Failed to export DataFrame to SQL table: " + e.getMessage(), e);
        } finally {
            if (config.isUseTransaction()) {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    @Override
    public void exportToTable(DataSource dataSource, String tableName) {
        exportToTable(dataSource, tableName, new SqlExporterConfiguration());
    }

    @Override
    public void exportToTable(DataSource dataSource, String tableName, SqlExporterConfiguration config) {
        if (dataSource == null) {
            throw new ExportException("DataSource cannot be null");
        }
        try (Connection conn = dataSource.getConnection()) {
            exportToTable(conn, tableName, config);
        } catch (SQLException e) {
            throw new ExportException("Failed to get connection from DataSource: " + e.getMessage(), e);
        }
    }

    private boolean checkTableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = null;
        String schema = null;
        String actualTable = tableName;

        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.", 2);
            schema = parts[0];
            actualTable = parts[1];
        }

        try (ResultSet rs = meta.getTables(catalog, schema, actualTable, null)) {
            if (rs.next()) {
                return true;
            }
        }
        // Try uppercase/lowercase in case driver is case-sensitive
        try (ResultSet rs = meta.getTables(catalog, schema, actualTable.toUpperCase(), null)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = meta.getTables(catalog, schema, actualTable.toLowerCase(), null)) {
            return rs.next();
        }
    }

    private void createTable(Connection conn, String tableName, List<Column> columns) throws SQLException {
        StringBuilder sql = new StringBuilder("CREATE TABLE ");
        sql.append(tableName).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(SqlTypeMapping.toSqlColumnDefinition(columns.get(i)));
        }
        sql.append(")");

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql.toString());
        }
    }

    private void insertRows(Connection conn, String tableName, IDataFrame df, List<Column> columns, int batchSize) throws SQLException {
        int colCount = columns.size();
        int rowCount = df.size();

        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(tableName).append(" (");
        for (int i = 0; i < colCount; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(SqlTypeMapping.escapeIdentifier(columns.get(i).columnName()));
        }
        sql.append(") VALUES (");
        for (int i = 0; i < colCount; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        sql.append(")");

        List<ColumnVector> vectors = df.getColumnVectors();

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int countInBatch = 0;
            for (int r = 0; r < rowCount; r++) {
                for (int c = 0; c < colCount; c++) {
                    int paramIndex = c + 1;
                    Column col = columns.get(c);
                    Object val = (vectors != null && c < vectors.size())
                            ? vectors.get(c).getObject(r)
                            : df.getRow(r).get(col.columnName());

                    setStatementParameter(ps, paramIndex, val, col.columnType());
                }
                ps.addBatch();
                countInBatch++;

                if (batchSize > 0 && countInBatch >= batchSize) {
                    ps.executeBatch();
                    countInBatch = 0;
                }
            }
            if (countInBatch > 0) {
                ps.executeBatch();
            }
        }
    }

    private void setStatementParameter(PreparedStatement ps, int paramIndex, Object value, Class<?> colType) throws SQLException {
        if (value == null) {
            ps.setObject(paramIndex, null);
            return;
        }

        if (value instanceof Integer) {
            ps.setInt(paramIndex, (Integer) value);
        } else if (value instanceof Long) {
            ps.setLong(paramIndex, (Long) value);
        } else if (value instanceof Double) {
            ps.setDouble(paramIndex, (Double) value);
        } else if (value instanceof Float) {
            ps.setFloat(paramIndex, (Float) value);
        } else if (value instanceof Boolean) {
            ps.setBoolean(paramIndex, (Boolean) value);
        } else if (value instanceof BigDecimal) {
            ps.setBigDecimal(paramIndex, (BigDecimal) value);
        } else if (value instanceof LocalDate) {
            ps.setDate(paramIndex, Date.valueOf((LocalDate) value));
        } else if (value instanceof LocalDateTime) {
            ps.setTimestamp(paramIndex, Timestamp.valueOf((LocalDateTime) value));
        } else if (value instanceof Instant) {
            ps.setTimestamp(paramIndex, Timestamp.from((Instant) value));
        } else if (value instanceof java.util.Date) {
            ps.setTimestamp(paramIndex, new Timestamp(((java.util.Date) value).getTime()));
        } else {
            ps.setString(paramIndex, value.toString());
        }
    }
}
