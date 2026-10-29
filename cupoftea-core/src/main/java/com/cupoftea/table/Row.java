package com.cupoftea.table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * An immutable representation of a single DataFrame row with typed accessor methods.
 * <p>
 * Implements {@link Map Map&lt;String, Object&gt;} as an unmodifiable map so callers cannot
 * corrupt the internal schema or values directly.
 * <p>
 * Supports both map-backed rows and zero-copy columnar vector views for high-throughput
 * sequential and parallel evaluation.
 */
public class Row implements Map<String, Object>, Serializable {

    private static final long serialVersionUID = 1L;

    private volatile Map<String, Object> values;
    private final List<String> columnOrder;
    private final transient ColumnVector[] vectors;
    private final transient Map<String, Integer> columnIndex;
    private final int rowIndex;

    public Row() {
        this.values = Collections.emptyMap();
        this.columnOrder = Collections.emptyList();
        this.vectors = null;
        this.columnIndex = null;
        this.rowIndex = -1;
    }

    public Row(Map<String, ?> data) {
        if (data == null || data.isEmpty()) {
            this.values = Collections.emptyMap();
            this.columnOrder = Collections.emptyList();
        } else {
            Map<String, Object> copy = new LinkedHashMap<>(data);
            this.values = Collections.unmodifiableMap(copy);
            this.columnOrder = Collections.unmodifiableList(new ArrayList<>(copy.keySet()));
        }
        this.vectors = null;
        this.columnIndex = null;
        this.rowIndex = -1;
    }

    /**
     * Zero-copy columnar constructor backed directly by {@link ColumnVector} instances at {@code rowIndex}.
     * Avoids allocating a {@link LinkedHashMap} per row during vectorized filter, map, and column creation.
     */
    public Row(ColumnVector[] vectors, Map<String, Integer> columnIndex, List<String> columnOrder, int rowIndex) {
        this.vectors = vectors;
        this.columnIndex = columnIndex;
        this.columnOrder = columnOrder != null ? columnOrder : Collections.emptyList();
        this.rowIndex = rowIndex;
        this.values = null;
    }

    private Map<String, Object> materializeValues() {
        Map<String, Object> local = this.values;
        if (local == null) {
            if (vectors == null || columnOrder.isEmpty()) {
                local = Collections.emptyMap();
            } else {
                Map<String, Object> map = new LinkedHashMap<>(columnOrder.size());
                for (int i = 0; i < columnOrder.size(); i++) {
                    ColumnVector vec = vectors[i];
                    map.put(columnOrder.get(i), vec != null ? vec.getObject(rowIndex) : null);
                }
                local = Collections.unmodifiableMap(map);
            }
            this.values = local;
        }
        return local;
    }

    public static Row of(Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return new Row();
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Key-value arguments must come in pairs");
        }
        Map<String, Object> map = new LinkedHashMap<>(keyValues.length / 2);
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return new Row(map);
    }

    /**
     * Resolves the column name at the given 0-based index.
     * Supports negative index (-1 refers to the last column).
     */
    public String getColumnName(int index) {
        int total = this.columnOrder.size();
        int resolved = index < 0 ? total + index : index;
        if (resolved < 0 || resolved >= total) {
            throw new IndexOutOfBoundsException("Column index " + index + " out of bounds for row with " + total + " columns");
        }
        return this.columnOrder.get(resolved);
    }

    private ColumnVector resolveVector(String column) {
        if (vectors == null || columnIndex == null || column == null) {
            return null;
        }
        Integer idx = columnIndex.get(column);
        return idx != null ? vectors[idx] : null;
    }

    /**
     * Untyped column accessor by name.
     *
     * @param column column name
     * @return raw value or {@code null}
     */
    public Object get(String column) {
        if (vectors != null) {
            ColumnVector vec = resolveVector(column);
            return vec != null ? vec.getObject(rowIndex) : null;
        }
        return this.values.get(column);
    }

    @Override
    public Object get(Object key) {
        if (vectors != null) {
            if (key instanceof String s) {
                return get(s);
            }
            return null;
        }
        return this.values.get(key);
    }

    /**
     * Untyped column accessor by integer position.
     * Supports negative indexing (-1 is the last column).
     */
    public Object get(int index) {
        if (vectors != null) {
            int total = this.columnOrder.size();
            int resolved = index < 0 ? total + index : index;
            if (resolved < 0 || resolved >= total) {
                throw new IndexOutOfBoundsException("Column index " + index + " out of bounds for row with " + total + " columns");
            }
            ColumnVector vec = vectors[resolved];
            return vec != null ? vec.getObject(rowIndex) : null;
        }
        return get(getColumnName(index));
    }

    /**
     * Checks if the value for the given column is missing / NA (null, NA singleton, or NaN).
     */
    public boolean isNA(String column) {
        if (vectors != null) {
            ColumnVector vec = resolveVector(column);
            if (vec == null || vec.isNull(rowIndex)) {
                return true;
            }
            return NA.isNA(vec.getObject(rowIndex));
        }
        return NA.isNA(this.values.get(column));
    }

    /**
     * Checks if the value for the given column is null.
     */
    public boolean isNull(String column) {
        if (vectors != null) {
            ColumnVector vec = resolveVector(column);
            return vec == null || vec.isNull(rowIndex);
        }
        return this.values.get(column) == null;
    }

    /**
     * Alias for {@link #isNA(String)}.
     */
    public boolean isMissing(String column) {
        return isNA(column);
    }

    /**
     * Checks if the value at the given column position is missing / NA.
     */
    public boolean isNA(int index) {
        return isNA(getColumnName(index));
    }

    /**
     * Checks if the value at the given column position is null.
     */
    public boolean isNull(int index) {
        return isNull(getColumnName(index));
    }

    /**
     * Alias for {@link #isNA(int)}.
     */
    public boolean isMissing(int index) {
        return isNA(index);
    }

    /**
     * Returns the column value as a String. If the value is not a String,
     * {@link Object#toString()} is called unless null or NA.
     */
    public String getString(String column) {
        if (vectors != null) {
            ColumnVector vec = resolveVector(column);
            if (vec == null || vec.isNull(rowIndex)) {
                return null;
            }
            if (vec instanceof StringColumnVector sv) {
                return sv.getString(rowIndex);
            }
        }
        Object val = get(column);
        if (NA.isNA(val)) {
            return null;
        }
        if (val instanceof String s) {
            return s;
        }
        return val.toString();
    }

    /**
     * Returns the column value at the specified index as a String.
     */
    public String getString(int index) {
        return getString(getColumnName(index));
    }

    /**
     * Returns the column value as an Integer.
     */
    public Integer getInt(String column) {
        return getInteger(column);
    }

    /**
     * Returns the column value at the specified index as an Integer.
     */
    public Integer getInt(int index) {
        return getInt(getColumnName(index));
    }

    /**
     * Returns the column value as an Integer.
     */
    public Integer getInteger(String column) {
        if (vectors != null) {
            ColumnVector vec = resolveVector(column);
            if (vec == null || vec.isNull(rowIndex)) {
                return null;
            }
            if (vec instanceof IntColumnVector iv) {
                return iv.getInt(rowIndex);
            }
        }
        Object val = get(column);
        if (NA.isNA(val)) {
            return null;
        }
        if (val instanceof Integer i) {
            return i;
        }
        if (val instanceof Number num) {
            return num.intValue();
        }
        if (val instanceof Boolean b) {
            return b ? 1 : 0;
        }
        String s = val.toString().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s) || "na".equalsIgnoreCase(s)) {
            return null;
        }
        return Integer.valueOf(s);
    }

    /**
     * Returns the column value at the specified index as an Integer.
     */
    public Integer getInteger(int index) {
        return getInteger(getColumnName(index));
    }

    /**
     * Returns the column value as a Long.
     */
    public Long getLong(String column) {
        if (vectors != null) {
            ColumnVector vec = resolveVector(column);
            if (vec == null || vec.isNull(rowIndex)) {
                return null;
            }
            if (vec instanceof LongColumnVector lv) {
                return lv.getLong(rowIndex);
            }
            if (vec instanceof IntColumnVector iv) {
                return (long) iv.getInt(rowIndex);
            }
        }
        Object val = get(column);
        if (NA.isNA(val)) {
            return null;
        }
        if (val instanceof Long l) {
            return l;
        }
        if (val instanceof Number num) {
            return num.longValue();
        }
        String s = val.toString().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s) || "na".equalsIgnoreCase(s)) {
            return null;
        }
        return Long.valueOf(s);
    }

    /**
     * Returns the column value at the specified index as a Long.
     */
    public Long getLong(int index) {
        return getLong(getColumnName(index));
    }

    /**
     * Returns the column value as a Double.
     */
    public Double getDouble(String column) {
        if (vectors != null) {
            ColumnVector vec = resolveVector(column);
            if (vec == null || vec.isNull(rowIndex)) {
                return null;
            }
            if (vec instanceof DoubleColumnVector dv) {
                return dv.getDouble(rowIndex);
            }
            if (vec instanceof IntColumnVector iv) {
                return (double) iv.getInt(rowIndex);
            }
            if (vec instanceof LongColumnVector lv) {
                return (double) lv.getLong(rowIndex);
            }
            if (vec instanceof FloatColumnVector fv) {
                return (double) fv.getFloat(rowIndex);
            }
        }
        Object val = get(column);
        if (val == null || val instanceof NA) {
            return null;
        }
        if (val instanceof Double d) {
            return d;
        }
        if (val instanceof Number num) {
            return num.doubleValue();
        }
        String s = val.toString().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s) || "na".equalsIgnoreCase(s)) {
            return null;
        }
        if ("nan".equalsIgnoreCase(s)) {
            return Double.NaN;
        }
        return Double.valueOf(s);
    }

    /**
     * Returns the column value at the specified index as a Double.
     */
    public Double getDouble(int index) {
        return getDouble(getColumnName(index));
    }

    /**
     * Returns the column value as a Float.
     */
    public Float getFloat(String column) {
        if (vectors != null) {
            ColumnVector vec = resolveVector(column);
            if (vec == null || vec.isNull(rowIndex)) {
                return null;
            }
            if (vec instanceof FloatColumnVector fv) {
                return fv.getFloat(rowIndex);
            }
        }
        Object val = get(column);
        if (val == null || val instanceof NA) {
            return null;
        }
        if (val instanceof Float f) {
            return f;
        }
        if (val instanceof Number num) {
            return num.floatValue();
        }
        String s = val.toString().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s) || "na".equalsIgnoreCase(s)) {
            return null;
        }
        if ("nan".equalsIgnoreCase(s)) {
            return Float.NaN;
        }
        return Float.valueOf(s);
    }

    /**
     * Returns the column value at the specified index as a Float.
     */
    public Float getFloat(int index) {
        return getFloat(getColumnName(index));
    }

    /**
     * Returns the column value as a Boolean.
     */
    public Boolean getBoolean(String column) {
        if (vectors != null) {
            ColumnVector vec = resolveVector(column);
            if (vec == null || vec.isNull(rowIndex)) {
                return null;
            }
            if (vec instanceof BooleanColumnVector bv) {
                return bv.getBoolean(rowIndex);
            }
        }
        Object val = get(column);
        if (NA.isNA(val)) {
            return null;
        }
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof Number num) {
            return num.intValue() != 0;
        }
        String s = val.toString().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s) || "na".equalsIgnoreCase(s)) {
            return null;
        }
        if ("true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s) || "y".equalsIgnoreCase(s)) {
            return true;
        }
        if ("false".equalsIgnoreCase(s) || "0".equals(s) || "no".equalsIgnoreCase(s) || "n".equalsIgnoreCase(s)) {
            return false;
        }
        return Boolean.valueOf(s);
    }

    /**
     * Returns the column value at the specified index as a Boolean.
     */
    public Boolean getBoolean(int index) {
        return getBoolean(getColumnName(index));
    }

    /**
     * Returns the column value as a Date.
     */
    public Date getDate(String column) {
        Object val = get(column);
        if (NA.isNA(val)) {
            return null;
        }
        if (val instanceof Date d) {
            return new Date(d.getTime());
        }
        return null;
    }

    /**
     * Returns the column value at the specified index as a Date.
     */
    public Date getDate(int index) {
        return getDate(getColumnName(index));
    }

    /**
     * Returns the column value as a LocalDate.
     */
    public LocalDate getLocalDate(String column) {
        Object val = get(column);
        if (NA.isNA(val)) {
            return null;
        }
        if (val instanceof LocalDate ld) {
            return ld;
        }
        return LocalDate.parse(val.toString().trim());
    }

    /**
     * Returns the column value at the specified index as a LocalDate.
     */
    public LocalDate getLocalDate(int index) {
        return getLocalDate(getColumnName(index));
    }

    /**
     * Returns the column value as a LocalDateTime.
     */
    public LocalDateTime getLocalDateTime(String column) {
        Object val = get(column);
        if (NA.isNA(val)) {
            return null;
        }
        if (val instanceof LocalDateTime ldt) {
            return ldt;
        }
        return LocalDateTime.parse(val.toString().trim());
    }

    /**
     * Returns the column value at the specified index as a LocalDateTime.
     */
    public LocalDateTime getLocalDateTime(int index) {
        return getLocalDateTime(getColumnName(index));
    }

    /**
     * Returns the column value as an Instant.
     */
    public Instant getInstant(String column) {
        Object val = get(column);
        if (NA.isNA(val)) {
            return null;
        }
        if (val instanceof Instant inst) {
            return inst;
        }
        return Instant.parse(val.toString().trim());
    }

    /**
     * Returns the column value at the specified index as an Instant.
     */
    public Instant getInstant(int index) {
        return getInstant(getColumnName(index));
    }

    /**
     * Returns the column value as a BigDecimal.
     */
    public BigDecimal getBigDecimal(String column) {
        Object val = get(column);
        if (NA.isNA(val)) {
            return null;
        }
        if (val instanceof BigDecimal bd) {
            return bd;
        }
        if (val instanceof Number num) {
            return new BigDecimal(num.toString());
        }
        String s = val.toString().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s) || "na".equalsIgnoreCase(s)) {
            return null;
        }
        return new BigDecimal(s);
    }

    /**
     * Returns the column value at the specified index as a BigDecimal.
     */
    public BigDecimal getBigDecimal(int index) {
        return getBigDecimal(getColumnName(index));
    }

    /**
     * Returns the column value cast or converted to the requested type.
     */
    public <T> T getAs(String column, Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");
        Object val = get(column);
        if (NA.isNA(val)) {
            return null;
        }
        if (type.isInstance(val)) {
            return type.cast(val);
        }
        throw new ClassCastException("Value for column '" + column + "' (" + val + ") is of type "
            + val.getClass().getName() + ", not " + type.getName());
    }

    /**
     * Returns the column value at the specified index cast to the requested type.
     */
    public <T> T getAs(int index, Class<T> type) {
        return getAs(getColumnName(index), type);
    }

    /**
     * Checks if the column exists in this row.
     */
    public boolean containsKey(String column) {
        if (vectors != null) {
            return columnIndex != null && columnIndex.containsKey(column);
        }
        return this.values.containsKey(column);
    }

    /**
     * Returns the set of column names present in this row.
     */
    public Set<String> getColumns() {
        if (vectors != null) {
            return columnIndex != null ? Collections.unmodifiableSet(columnIndex.keySet()) : Collections.emptySet();
        }
        return this.values.keySet();
    }

    /**
     * Returns the ordered list of column names present in this row.
     */
    public List<String> getColumnNames() {
        return this.columnOrder;
    }

    /**
     * Returns an unmodifiable shallow copy of the row as a {@link Map}.
     */
    public Map<String, Object> toMap() {
        return new LinkedHashMap<>(materializeValues());
    }

    // Unmodifiable Map implementation

    @Override
    public int size() {
        return this.columnOrder.size();
    }

    @Override
    public boolean isEmpty() {
        return this.columnOrder.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        if (vectors != null) {
            return columnIndex != null && columnIndex.containsKey(key);
        }
        return this.values.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return materializeValues().containsValue(value);
    }

    @Override
    public Object put(String key, Object value) {
        throw new UnsupportedOperationException("Row is unmodifiable and cannot be mutated");
    }

    @Override
    public Object remove(Object key) {
        throw new UnsupportedOperationException("Row is unmodifiable and cannot be mutated");
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        throw new UnsupportedOperationException("Row is unmodifiable and cannot be mutated");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Row is unmodifiable and cannot be mutated");
    }

    @Override
    public Set<String> keySet() {
        return getColumns();
    }

    @Override
    public Collection<Object> values() {
        return materializeValues().values();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return materializeValues().entrySet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof Row other) {
            return materializeValues().equals(other.materializeValues());
        }
        if (o instanceof Map<?, ?> otherMap) {
            return materializeValues().equals(otherMap);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return materializeValues().hashCode();
    }

    @Override
    public String toString() {
        return materializeValues().toString();
    }
}
