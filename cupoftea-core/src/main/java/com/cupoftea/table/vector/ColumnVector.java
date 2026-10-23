package com.cupoftea.table.vector;

import java.io.Serializable;
import java.util.BitSet;
import java.util.Objects;

import com.cupoftea.table.Column;

/**
 * Base abstraction for columnar storage vectors in CupOfTea.
 * Stores data contiguously by column rather than row-by-row HashMaps.
 * Tracks null/missing values using a {@link BitSet} bitmap where a set bit indicates a null/NA cell.
 */
public abstract class ColumnVector implements Serializable {

    private static final long serialVersionUID = 1L;

    protected static final int DEFAULT_INITIAL_CAPACITY = 16;

    protected String name;
    protected Class<?> type;
    protected boolean nullable;
    protected final BitSet nulls;
    protected int size;

    protected ColumnVector(String name, Class<?> type, boolean nullable, int initialCapacity) {
        this.name = Objects.requireNonNull(name, "Column name cannot be null");
        this.type = type;
        this.nullable = nullable;
        this.nulls = new BitSet(Math.max(DEFAULT_INITIAL_CAPACITY, initialCapacity));
        this.size = 0;
    }

    public static ColumnVector create(String name, Class<?> type, int initialCapacity, boolean nullable) {
        if (type == int.class || type == Integer.class) {
            return new IntColumnVector(name, initialCapacity, nullable);
        } else if (type == long.class || type == Long.class) {
            return new LongColumnVector(name, initialCapacity, nullable);
        } else if (type == double.class || type == Double.class) {
            return new DoubleColumnVector(name, initialCapacity, nullable);
        } else if (type == float.class || type == Float.class) {
            return new FloatColumnVector(name, initialCapacity, nullable);
        } else if (type == boolean.class || type == Boolean.class) {
            return new BooleanColumnVector(name, initialCapacity, nullable);
        } else if (type == String.class) {
            return new StringColumnVector(name, initialCapacity, nullable);
        } else {
            return new ObjectColumnVector(name, type != null ? type : Object.class, initialCapacity, nullable);
        }
    }

    public static ColumnVector create(Column column, int initialCapacity) {
        Objects.requireNonNull(column, "Column cannot be null");
        return create(column.columnName(), column.columnType(), initialCapacity, column.nullable());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "Column name cannot be null");
    }

    public Class<?> getType() {
        return type;
    }

    public void setType(Class<?> type) {
        this.type = type;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public int size() {
        return size;
    }

    public boolean isNull(int row) {
        checkRowBounds(row);
        return nulls.get(row);
    }

    public void setNull(int row) {
        checkRowBounds(row);
        nulls.set(row);
    }

    public void clearNull(int row) {
        checkRowBounds(row);
        nulls.clear(row);
    }

    public BitSet getNulls() {
        return nulls;
    }

    public boolean hasNulls() {
        return !nulls.isEmpty();
    }

    public int nullCount() {
        return nulls.cardinality();
    }

    public abstract Object getObject(int row);

    public abstract void setObject(int row, Object value);

    public abstract void appendObject(Object value);

    public abstract void appendNull();

    public abstract void remove(int row);

    public abstract ColumnVector copy();

    public abstract ColumnVector slice(int from, int to);

    public abstract void fillna(Object value);

    public abstract void permute(int[] order);

    public abstract void filterInPlace(BitSet retainMask, int retainedCount);

    public abstract int compareRows(int rowA, int rowB);

    public abstract void clear();

    protected void checkRowBounds(int row) {
        if (row < 0 || row >= size) {
            throw new IndexOutOfBoundsException("Row index " + row + " out of bounds for column '" + name + "' of size " + size);
        }
    }

    protected void checkSliceBounds(int from, int to) {
        if (from < 0 || to > size || from > to) {
            throw new IndexOutOfBoundsException("Slice range [" + from + ", " + to + ") out of bounds for column '" + name + "' of size " + size);
        }
    }

    // Default primitive accessors (can be overridden by specialized subclasses)

    public int getInt(int row) {
        Object val = getObject(row);
        if (val == null) {
            return 0;
        }
        if (val instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(val.toString().trim());
    }

    public void setInt(int row, int val) {
        setObject(row, val);
    }

    public void appendInt(int val) {
        appendObject(val);
    }

    public long getLong(int row) {
        Object val = getObject(row);
        if (val == null) {
            return 0L;
        }
        if (val instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(val.toString().trim());
    }

    public void setLong(int row, long val) {
        setObject(row, val);
    }

    public void appendLong(long val) {
        appendObject(val);
    }

    public double getDouble(int row) {
        Object val = getObject(row);
        if (val == null) {
            return 0.0;
        }
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(val.toString().trim());
    }

    public void setDouble(int row, double val) {
        setObject(row, val);
    }

    public void appendDouble(double val) {
        appendObject(val);
    }

    public float getFloat(int row) {
        Object val = getObject(row);
        if (val == null) {
            return 0.0f;
        }
        if (val instanceof Number n) {
            return n.floatValue();
        }
        return Float.parseFloat(val.toString().trim());
    }

    public void setFloat(int row, float val) {
        setObject(row, val);
    }

    public void appendFloat(float val) {
        appendObject(val);
    }

    public boolean getBoolean(int row) {
        Object val = getObject(row);
        if (val == null) {
            return false;
        }
        if (val instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(val.toString().trim());
    }

    public void setBoolean(int row, boolean val) {
        setObject(row, val);
    }

    public void appendBoolean(boolean val) {
        appendObject(val);
    }

    public String getString(int row) {
        Object val = getObject(row);
        return val != null ? val.toString() : null;
    }

    public void setString(int row, String val) {
        setObject(row, val);
    }

    public void appendString(String val) {
        appendObject(val);
    }
}
