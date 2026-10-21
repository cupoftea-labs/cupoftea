package com.CupOfTea.table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable representation of a single DataFrame row with typed accessor methods.
 * <p>
 * Implements {@link Map Map&lt;String, Object&gt;} as an unmodifiable map so callers cannot
 * corrupt the internal schema or values directly.
 */
public class Row implements Map<String, Object>, Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, Object> values;

    public Row() {
        this.values = Collections.emptyMap();
    }

    public Row(Map<String, ?> data) {
        if (data == null || data.isEmpty()) {
            this.values = Collections.emptyMap();
        } else {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(data));
        }
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
     * Untyped column accessor.
     *
     * @param column column name
     * @return raw value or {@code null}
     */
    public Object get(String column) {
        return this.values.get(column);
    }

    @Override
    public Object get(Object key) {
        return this.values.get(key);
    }

    /**
     * Returns the column value as a String. If the value is not a String,
     * {@link Object#toString()} is called unless null.
     */
    public String getString(String column) {
        Object val = this.values.get(column);
        if (val == null) {
            return null;
        }
        if (val instanceof String s) {
            return s;
        }
        return val.toString();
    }

    /**
     * Returns the column value as an Integer.
     */
    public Integer getInt(String column) {
        return getInteger(column);
    }

    /**
     * Returns the column value as an Integer.
     */
    public Integer getInteger(String column) {
        Object val = this.values.get(column);
        if (val == null) {
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
        if (s.isEmpty()) {
            return null;
        }
        return Integer.valueOf(s);
    }

    /**
     * Returns the column value as a Long.
     */
    public Long getLong(String column) {
        Object val = this.values.get(column);
        if (val == null) {
            return null;
        }
        if (val instanceof Long l) {
            return l;
        }
        if (val instanceof Number num) {
            return num.longValue();
        }
        String s = val.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        return Long.valueOf(s);
    }

    /**
     * Returns the column value as a Double.
     */
    public Double getDouble(String column) {
        Object val = this.values.get(column);
        if (val == null) {
            return null;
        }
        if (val instanceof Double d) {
            return d;
        }
        if (val instanceof Number num) {
            return num.doubleValue();
        }
        String s = val.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        return Double.valueOf(s);
    }

    /**
     * Returns the column value as a Float.
     */
    public Float getFloat(String column) {
        Object val = this.values.get(column);
        if (val == null) {
            return null;
        }
        if (val instanceof Float f) {
            return f;
        }
        if (val instanceof Number num) {
            return num.floatValue();
        }
        String s = val.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        return Float.valueOf(s);
    }

    /**
     * Returns the column value as a Boolean.
     */
    public Boolean getBoolean(String column) {
        Object val = this.values.get(column);
        if (val == null) {
            return null;
        }
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof Number num) {
            return num.intValue() != 0;
        }
        String s = val.toString().trim();
        if ("true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s) || "y".equalsIgnoreCase(s)) {
            return true;
        }
        if ("false".equalsIgnoreCase(s) || "0".equals(s) || "no".equalsIgnoreCase(s) || "n".equalsIgnoreCase(s)) {
            return false;
        }
        return Boolean.valueOf(s);
    }

    /**
     * Returns the column value as a Date.
     */
    public Date getDate(String column) {
        Object val = this.values.get(column);
        if (val == null) {
            return null;
        }
        if (val instanceof Date d) {
            return new Date(d.getTime());
        }
        return null;
    }

    /**
     * Returns the column value as a LocalDate.
     */
    public LocalDate getLocalDate(String column) {
        Object val = this.values.get(column);
        if (val == null) {
            return null;
        }
        if (val instanceof LocalDate ld) {
            return ld;
        }
        return LocalDate.parse(val.toString().trim());
    }

    /**
     * Returns the column value as a LocalDateTime.
     */
    public LocalDateTime getLocalDateTime(String column) {
        Object val = this.values.get(column);
        if (val == null) {
            return null;
        }
        if (val instanceof LocalDateTime ldt) {
            return ldt;
        }
        return LocalDateTime.parse(val.toString().trim());
    }

    /**
     * Returns the column value as an Instant.
     */
    public Instant getInstant(String column) {
        Object val = this.values.get(column);
        if (val == null) {
            return null;
        }
        if (val instanceof Instant inst) {
            return inst;
        }
        return Instant.parse(val.toString().trim());
    }

    /**
     * Returns the column value as a BigDecimal.
     */
    public BigDecimal getBigDecimal(String column) {
        Object val = this.values.get(column);
        if (val == null) {
            return null;
        }
        if (val instanceof BigDecimal bd) {
            return bd;
        }
        if (val instanceof Number num) {
            return new BigDecimal(num.toString());
        }
        String s = val.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        return new BigDecimal(s);
    }

    /**
     * Returns the column value cast or converted to the requested type.
     */
    public <T> T getAs(String column, Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");
        Object val = this.values.get(column);
        if (val == null) {
            return null;
        }
        if (type.isInstance(val)) {
            return type.cast(val);
        }
        throw new ClassCastException("Value for column '" + column + "' (" + val + ") is of type "
            + val.getClass().getName() + ", not " + type.getName());
    }

    /**
     * Checks if the column exists in this row.
     */
    public boolean containsKey(String column) {
        return this.values.containsKey(column);
    }

    /**
     * Returns the set of column names present in this row.
     */
    public Set<String> getColumns() {
        return this.values.keySet();
    }

    /**
     * Returns an unmodifiable shallow copy of the row as a {@link Map}.
     */
    public Map<String, Object> toMap() {
        return new LinkedHashMap<>(this.values);
    }

    // Unmodifiable Map implementation

    @Override
    public int size() {
        return this.values.size();
    }

    @Override
    public boolean isEmpty() {
        return this.values.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return this.values.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return this.values.containsValue(value);
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
        return this.values.keySet();
    }

    @Override
    public Collection<Object> values() {
        return this.values.values();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return this.values.entrySet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof Row other) {
            return this.values.equals(other.values);
        }
        if (o instanceof Map<?, ?> otherMap) {
            return this.values.equals(otherMap);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.values.hashCode();
    }

    @Override
    public String toString() {
        return this.values.toString();
    }
}
