package com.CupOfTea.table;

import java.util.Objects;

public record Column(
    String columnName
) {
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
