package com.cupoftea.table;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents the index (row labels or designated index column) of a DataFrame.
 */
public class Index implements Serializable {

    private static final long serialVersionUID = 1L;

    private String columnName;

    public Index() {
        this.columnName = null;
    }

    public Index(String columnName) {
        this.columnName = columnName;
    }

    public boolean hasIndex() {
        return this.columnName != null;
    }

    public String getColumnName() {
        return this.columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public void clear() {
        this.columnName = null;
    }

    public Index copy() {
        return new Index(this.columnName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Index index = (Index) o;
        return Objects.equals(columnName, index.columnName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(columnName);
    }

    @Override
    public String toString() {
        return columnName != null ? "Index[" + columnName + "]" : "Index[default]";
    }
}
