package com.cupoftea.sql.configurations;

import java.util.Objects;

public class SqlExporterConfiguration {

    public enum SaveMode {
        APPEND,
        OVERWRITE,
        FAIL_IF_EXISTS
    }

    private SaveMode saveMode = SaveMode.APPEND;
    private int batchSize = 1000;
    private boolean createTableIfNotExists = true;
    private boolean useTransaction = true;

    public SqlExporterConfiguration() {
    }

    public static SqlExporterConfiguration builder() {
        return new SqlExporterConfiguration();
    }

    public SaveMode getSaveMode() {
        return saveMode;
    }

    public SqlExporterConfiguration setSaveMode(SaveMode saveMode) {
        this.saveMode = saveMode != null ? saveMode : SaveMode.APPEND;
        return this;
    }

    public SqlExporterConfiguration saveMode(SaveMode saveMode) {
        return setSaveMode(saveMode);
    }

    public int getBatchSize() {
        return batchSize;
    }

    public SqlExporterConfiguration setBatchSize(int batchSize) {
        this.batchSize = batchSize > 0 ? batchSize : 1000;
        return this;
    }

    public SqlExporterConfiguration batchSize(int batchSize) {
        return setBatchSize(batchSize);
    }

    public boolean isCreateTableIfNotExists() {
        return createTableIfNotExists;
    }

    public SqlExporterConfiguration setCreateTableIfNotExists(boolean createTableIfNotExists) {
        this.createTableIfNotExists = createTableIfNotExists;
        return this;
    }

    public SqlExporterConfiguration createTableIfNotExists(boolean createTableIfNotExists) {
        return setCreateTableIfNotExists(createTableIfNotExists);
    }

    public boolean isUseTransaction() {
        return useTransaction;
    }

    public SqlExporterConfiguration setUseTransaction(boolean useTransaction) {
        this.useTransaction = useTransaction;
        return this;
    }

    public SqlExporterConfiguration useTransaction(boolean useTransaction) {
        return setUseTransaction(useTransaction);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SqlExporterConfiguration that = (SqlExporterConfiguration) o;
        return batchSize == that.batchSize && createTableIfNotExists == that.createTableIfNotExists && useTransaction == that.useTransaction && saveMode == that.saveMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(saveMode, batchSize, createTableIfNotExists, useTransaction);
    }
}
