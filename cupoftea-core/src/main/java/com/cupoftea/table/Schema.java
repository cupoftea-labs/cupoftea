package com.cupoftea.table;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the schema (ordered column names and types) of a DataFrame.
 * Supports static creation such as:
 * <pre>{@code
 * Schema schema = Schema.of("year", INT, "price", DOUBLE);
 * }</pre>
 */
public final class Schema implements Iterable<Column>, Serializable {

    private static final long serialVersionUID = 1L;

    public static final DataType INT = DataType.INT;
    public static final DataType DOUBLE = DataType.DOUBLE;
    public static final DataType FLOAT = DataType.FLOAT;
    public static final DataType LONG = DataType.LONG;
    public static final DataType BOOLEAN = DataType.BOOLEAN;
    public static final DataType STRING = DataType.STRING;
    public static final DataType DATE = DataType.DATE;
    public static final DataType DATETIME = DataType.DATETIME;
    public static final DataType INSTANT = DataType.INSTANT;
    public static final DataType DECIMAL = DataType.DECIMAL;
    public static final DataType BIG_DECIMAL = DataType.DECIMAL;
    public static final DataType OBJECT = DataType.OBJECT;

    private final List<Column> columns;
    private final Map<String, Column> columnByName;

    public Schema(List<Column> columns) {
        if (columns == null) {
            this.columns = Collections.emptyList();
            this.columnByName = Collections.emptyMap();
        } else {
            List<Column> copy = new ArrayList<>(columns.size());
            Map<String, Column> byName = new LinkedHashMap<>();
            for (Column col : columns) {
                if (col == null || col.columnName() == null) {
                    throw new IllegalArgumentException("Column and column name cannot be null in Schema");
                }
                copy.add(col);
                byName.put(col.columnName(), col);
            }
            this.columns = Collections.unmodifiableList(copy);
            this.columnByName = Collections.unmodifiableMap(byName);
        }
    }

    /**
     * Creates a Schema from alternating column names and types.
     * Types can be instances of {@link DataType} or {@link Class}.
     * <pre>{@code
     * Schema schema = Schema.of("year", INT, "price", DOUBLE);
     * }</pre>
     */
    public static Schema of(Object... nameTypePairs) {
        if (nameTypePairs == null || nameTypePairs.length == 0) {
            return empty();
        }
        if (nameTypePairs.length % 2 != 0) {
            throw new IllegalArgumentException(
                "Schema.of requires an even number of arguments (alternating column name and type), got "
                    + nameTypePairs.length
            );
        }

        List<Column> cols = new ArrayList<>(nameTypePairs.length / 2);
        for (int i = 0; i < nameTypePairs.length; i += 2) {
            Object nameObj = nameTypePairs[i];
            Object typeObj = nameTypePairs[i + 1];

            if (!(nameObj instanceof String name)) {
                throw new IllegalArgumentException("Column name must be a String at argument index " + i + ": " + nameObj);
            }

            Class<?> resolvedType = resolveType(typeObj);
            cols.add(new Column(name, resolvedType));
        }

        return new Schema(cols);
    }

    public static Schema of(Column... columns) {
        if (columns == null || columns.length == 0) {
            return empty();
        }
        return new Schema(List.of(columns));
    }

    public static Schema of(List<Column> columns) {
        return new Schema(columns);
    }

    public static Schema of(Map<String, ?> schemaMap) {
        if (schemaMap == null || schemaMap.isEmpty()) {
            return empty();
        }
        List<Column> cols = new ArrayList<>(schemaMap.size());
        for (Map.Entry<String, ?> entry : schemaMap.entrySet()) {
            cols.add(new Column(entry.getKey(), resolveType(entry.getValue())));
        }
        return new Schema(cols);
    }

    public static Schema empty() {
        return new Schema(Collections.emptyList());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Schema fromHeader(Header header) {
        if (header == null || header.getHeader() == null) {
            return empty();
        }
        return new Schema(header.getHeader());
    }

    public Header toHeader() {
        Header header = new Header();
        for (Column col : this.columns) {
            header.addColumn(new Column(col.columnName(), col.columnType(), col.nullable()));
        }
        return header;
    }

    public boolean isNullable(String name) {
        Column col = this.columnByName.get(name);
        return col != null && col.nullable();
    }

    public boolean isNullable(int index) {
        Column col = this.columns.get(index);
        return col != null && col.nullable();
    }

    public Map<String, Class<?>> toMap() {
        Map<String, Class<?>> map = new LinkedHashMap<>();
        for (Column col : this.columns) {
            map.put(col.columnName(), col.columnType());
        }
        return map;
    }

    public int size() {
        return this.columns.size();
    }

    public boolean isEmpty() {
        return this.columns.isEmpty();
    }

    public List<Column> getColumns() {
        return this.columns;
    }

    public List<String> getColumnNames() {
        List<String> names = new ArrayList<>(this.columns.size());
        for (Column col : this.columns) {
            names.add(col.columnName());
        }
        return names;
    }

    public List<Class<?>> getColumnTypes() {
        List<Class<?>> types = new ArrayList<>(this.columns.size());
        for (Column col : this.columns) {
            types.add(col.columnType());
        }
        return types;
    }

    public Column getColumn(String name) {
        return this.columnByName.get(name);
    }

    public Column getColumn(int index) {
        return this.columns.get(index);
    }

    public Class<?> getType(String name) {
        Column col = this.columnByName.get(name);
        return col != null ? col.columnType() : null;
    }

    public DataType getDataType(String name) {
        Class<?> type = getType(name);
        return type != null ? DataType.fromJavaType(type) : null;
    }

    public boolean hasColumn(String name) {
        return this.columnByName.containsKey(name);
    }

    public int indexOf(String name) {
        for (int i = 0; i < this.columns.size(); i++) {
            if (this.columns.get(i).columnName().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public Iterator<Column> iterator() {
        return this.columns.iterator();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Schema schema = (Schema) o;
        if (this.columns.size() != schema.columns.size()) return false;
        for (int i = 0; i < this.columns.size(); i++) {
            Column c1 = this.columns.get(i);
            Column c2 = schema.columns.get(i);
            if (!Objects.equals(c1.columnName(), c2.columnName())
                    || !Objects.equals(c1.columnType(), c2.columnType())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(columns);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Schema[");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            Column c = columns.get(i);
            sb.append(c.columnName()).append(": ");
            sb.append(c.columnType() != null ? c.columnType().getSimpleName() : "Object");
        }
        sb.append("]");
        return sb.toString();
    }

    private static Class<?> resolveType(Object typeObj) {
        if (typeObj == null) {
            return null;
        }
        if (typeObj instanceof DataType dt) {
            return dt.getJavaType();
        }
        if (typeObj instanceof Class<?> clz) {
            return clz;
        }
        throw new IllegalArgumentException("Unsupported type definition: " + typeObj + " (" + typeObj.getClass() + ")");
    }

    public static final class Builder {
        private final List<Column> columns = new ArrayList<>();

        public Builder add(String name, DataType type) {
            columns.add(new Column(name, type != null ? type.getJavaType() : null));
            return this;
        }

        public Builder add(String name, DataType type, boolean nullable) {
            columns.add(new Column(name, type != null ? type.getJavaType() : null, nullable));
            return this;
        }

        public Builder add(String name, Class<?> type) {
            columns.add(new Column(name, type));
            return this;
        }

        public Builder add(String name, Class<?> type, boolean nullable) {
            columns.add(new Column(name, type, nullable));
            return this;
        }

        public Builder add(Column column) {
            columns.add(column);
            return this;
        }

        public Schema build() {
            return new Schema(columns);
        }
    }
}
