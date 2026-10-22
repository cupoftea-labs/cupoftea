package com.cupoftea.table;

import java.io.Serializable;
import java.util.Objects;

public record Column (
    String columnName,
    Class columnType,
    boolean nullable
) implements Serializable {

    public Column(String columnName, Class columnType) {
        this(columnName, columnType, true);
    }

    public boolean isNullable() {
        return nullable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Column that = (Column) o;
        return columnName.equals(that.columnName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(columnName);
    }
}
