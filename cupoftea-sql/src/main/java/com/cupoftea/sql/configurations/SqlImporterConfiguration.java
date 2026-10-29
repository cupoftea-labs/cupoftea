package com.cupoftea.sql.configurations;

import java.util.Objects;

import com.cupoftea.table.Schema;

public class SqlImporterConfiguration {

    private Schema schema;
    private int fetchSize = 0;
    private int maxRows = 0;
    private int queryTimeout = 0;

    public SqlImporterConfiguration() {
    }

    public static SqlImporterConfiguration builder() {
        return new SqlImporterConfiguration();
    }

    public Schema getSchema() {
        return schema;
    }

    public SqlImporterConfiguration setSchema(Schema schema) {
        this.schema = schema;
        return this;
    }

    public SqlImporterConfiguration schema(Schema schema) {
        this.schema = schema;
        return this;
    }

    public int getFetchSize() {
        return fetchSize;
    }

    public SqlImporterConfiguration setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
        return this;
    }

    public SqlImporterConfiguration fetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
        return this;
    }

    public int getMaxRows() {
        return maxRows;
    }

    public SqlImporterConfiguration setMaxRows(int maxRows) {
        this.maxRows = maxRows;
        return this;
    }

    public SqlImporterConfiguration maxRows(int maxRows) {
        this.maxRows = maxRows;
        return this;
    }

    public int getQueryTimeout() {
        return queryTimeout;
    }

    public SqlImporterConfiguration setQueryTimeout(int queryTimeout) {
        this.queryTimeout = queryTimeout;
        return this;
    }

    public SqlImporterConfiguration queryTimeout(int queryTimeout) {
        this.queryTimeout = queryTimeout;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SqlImporterConfiguration that = (SqlImporterConfiguration) o;
        return fetchSize == that.fetchSize && maxRows == that.maxRows && queryTimeout == that.queryTimeout && Objects.equals(schema, that.schema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, fetchSize, maxRows, queryTimeout);
    }
}
