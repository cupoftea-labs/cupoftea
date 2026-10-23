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

    public final transient StringAccessor str;
    public final transient TemporalAccessor dt;

    protected ColumnVector(String name, Class<?> type, boolean nullable, int initialCapacity) {
        this.name = Objects.requireNonNull(name, "Column name cannot be null");
        this.type = type;
        this.nullable = nullable;
        this.nulls = new BitSet(Math.max(DEFAULT_INITIAL_CAPACITY, initialCapacity));
        this.size = 0;
        this.str = new StringAccessor(this);
        this.dt = new TemporalAccessor(this);
    }

    public StringAccessor str() {
        return this.str != null ? this.str : new StringAccessor(this);
    }

    public TemporalAccessor dt() {
        return this.dt != null ? this.dt : new TemporalAccessor(this);
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

    // ==========================================
    // Statistical Operations & Vectorization
    // ==========================================

    /**
     * Returns the count of non-null and non-NA elements currently stored.
     */
    public int validCount() {
        return size - (nulls.isEmpty() ? 0 : nulls.get(0, size).cardinality());
    }

    /**
     * Extracts non-null, non-NA values into a contiguous primitive {@code double[]} array.
     */
    public double[] toPrimitiveDoubleArray() {
        int count = validCount();
        if (count == 0) {
            return new double[0];
        }
        double[] result = new double[count];
        int idx = 0;
        for (int i = 0; i < size; i++) {
            if (!nulls.get(i)) {
                result[idx++] = getDouble(i);
            }
        }
        return result;
    }

    /**
     * Computes the sum of non-null values, or {@link Double#NaN} if column has no valid values.
     */
    public double sum() {
        int count = validCount();
        if (count == 0) {
            return Double.NaN;
        }
        double total = 0.0;
        for (int i = 0; i < size; i++) {
            if (!nulls.get(i)) {
                total += getDouble(i);
            }
        }
        return total;
    }

    /**
     * Computes the arithmetic mean, or {@link Double#NaN} if column has no valid values.
     */
    public double mean() {
        int count = validCount();
        if (count == 0) {
            return Double.NaN;
        }
        return sum() / count;
    }

    /**
     * Computes the minimum value, or {@link Double#NaN} if column has no valid values.
     */
    public double min() {
        int count = validCount();
        if (count == 0) {
            return Double.NaN;
        }
        double minVal = Double.POSITIVE_INFINITY;
        boolean found = false;
        for (int i = 0; i < size; i++) {
            if (!nulls.get(i)) {
                double val = getDouble(i);
                if (!found || Double.compare(val, minVal) < 0) {
                    minVal = val;
                    found = true;
                }
            }
        }
        return found ? minVal : Double.NaN;
    }

    /**
     * Computes the maximum value, or {@link Double#NaN} if column has no valid values.
     */
    public double max() {
        int count = validCount();
        if (count == 0) {
            return Double.NaN;
        }
        double maxVal = Double.NEGATIVE_INFINITY;
        boolean found = false;
        for (int i = 0; i < size; i++) {
            if (!nulls.get(i)) {
                double val = getDouble(i);
                if (!found || Double.compare(val, maxVal) > 0) {
                    maxVal = val;
                    found = true;
                }
            }
        }
        return found ? maxVal : Double.NaN;
    }

    /**
     * Calculates the exact median (50th percentile) skipping null and NA values.
     */
    public double median() {
        return quantile(0.5);
    }

    /**
     * Computes sample variance (ddof=1) skipping null and NA values.
     */
    public double variance() {
        return variance(true);
    }

    /**
     * Computes sample (isSample=true, ddof=1) or population (isSample=false, ddof=0) variance.
     */
    public double variance(boolean isSample) {
        int n = validCount();
        if (isSample && n < 2) {
            return Double.NaN;
        }
        if (!isSample && n < 1) {
            return Double.NaN;
        }
        double m = mean();
        double sumSq = 0.0;
        for (int i = 0; i < size; i++) {
            if (!nulls.get(i)) {
                double d = getDouble(i) - m;
                sumSq += d * d;
            }
        }
        return sumSq / (isSample ? (n - 1) : n);
    }

    /**
     * Alias for {@link #variance()}.
     */
    public double var() {
        return variance(true);
    }

    /**
     * Alias for {@link #variance(boolean)}.
     */
    public double var(boolean isSample) {
        return variance(isSample);
    }

    /**
     * Computes sample standard deviation (ddof=1) skipping null and NA values.
     */
    public double standardDeviation() {
        return standardDeviation(true);
    }

    /**
     * Computes sample (isSample=true, ddof=1) or population (isSample=false, ddof=0) standard deviation.
     */
    public double standardDeviation(boolean isSample) {
        double v = variance(isSample);
        return Double.isNaN(v) ? Double.NaN : Math.sqrt(v);
    }

    /**
     * Alias for {@link #standardDeviation()}.
     */
    public double std() {
        return standardDeviation(true);
    }

    /**
     * Alias for {@link #standardDeviation(boolean)}.
     */
    public double std(boolean isSample) {
        return standardDeviation(isSample);
    }

    /**
     * Calculates arbitrary quantiles / percentiles using linear interpolation.
     *
     * @param q quantile fraction between 0.0 and 1.0
     * @return the interpolated quantile value
     */
    public double quantile(double q) {
        int n = validCount();
        if (n == 0) {
            return Double.NaN;
        }
        double[] arr = toPrimitiveDoubleArray();
        return Introselect.quantile(arr, 0, arr.length, q);
    }

    /**
     * Computes sample Fisher-Pearson skewness skipping null and NA values.
     */
    public double skewness() {
        return skewness(true);
    }

    /**
     * Computes sample or population skewness.
     */
    public double skewness(boolean isSample) {
        int n = validCount();
        if (isSample && n < 3) {
            return Double.NaN;
        }
        if (!isSample && n < 1) {
            return Double.NaN;
        }
        double m = mean();
        double sum2 = 0.0;
        double sum3 = 0.0;
        for (int i = 0; i < size; i++) {
            if (!nulls.get(i)) {
                double d = getDouble(i) - m;
                sum2 += d * d;
                sum3 += d * d * d;
            }
        }
        double m2 = sum2 / n;
        if (m2 == 0.0) {
            return Double.NaN;
        }
        double m3 = sum3 / n;
        double g1 = m3 / Math.pow(m2, 1.5);
        if (!isSample) {
            return g1;
        }
        return (Math.sqrt((double) n * (n - 1)) / (n - 2.0)) * g1;
    }

    /**
     * Alias for {@link #skewness()}.
     */
    public double skew() {
        return skewness(true);
    }

    /**
     * Computes sample excess kurtosis skipping null and NA values.
     */
    public double kurtosis() {
        return kurtosis(true);
    }

    /**
     * Computes sample or population excess kurtosis.
     */
    public double kurtosis(boolean isSample) {
        int n = validCount();
        if (isSample && n < 4) {
            return Double.NaN;
        }
        if (!isSample && n < 1) {
            return Double.NaN;
        }
        double m = mean();
        double sum2 = 0.0;
        double sum4 = 0.0;
        for (int i = 0; i < size; i++) {
            if (!nulls.get(i)) {
                double d = getDouble(i) - m;
                double d2 = d * d;
                sum2 += d2;
                sum4 += d2 * d2;
            }
        }
        double m2 = sum2 / n;
        if (m2 == 0.0) {
            return Double.NaN;
        }
        double m4 = sum4 / n;
        if (!isSample) {
            return (m4 / (m2 * m2)) - 3.0;
        }
        double term1 = ((double) (n - 1)) / ((n - 2.0) * (n - 3.0));
        double term2 = (n + 1.0) * (m4 / (m2 * m2)) - 3.0 * (n - 1.0);
        return term1 * term2;
    }

    /**
     * Alias for {@link #kurtosis()}.
     */
    public double kurt() {
        return kurtosis(true);
    }
}
