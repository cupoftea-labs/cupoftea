package com.cupoftea.table.vector;

import java.util.Arrays;
import java.util.BitSet;

import com.cupoftea.table.NA;

/**
 * High-performance primitive columnar vector for 64-bit integers.
 */
public class LongColumnVector extends ColumnVector {

    private static final long serialVersionUID = 1L;

    private long[] data;

    public LongColumnVector(String name, int initialCapacity, boolean nullable) {
        super(name, Long.class, nullable, initialCapacity);
        this.data = new long[Math.max(DEFAULT_INITIAL_CAPACITY, initialCapacity)];
    }

    private LongColumnVector(String name, boolean nullable, long[] data, BitSet nulls, int size) {
        super(name, Long.class, nullable, data.length);
        this.data = data;
        this.nulls.or(nulls);
        this.size = size;
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > data.length) {
            int newCap = Math.max(data.length * 2, minCapacity);
            data = Arrays.copyOf(data, newCap);
        }
    }

    @Override
    public long getLong(int row) {
        checkRowBounds(row);
        return data[row];
    }

    @Override
    public void setLong(int row, long val) {
        checkRowBounds(row);
        data[row] = val;
        nulls.clear(row);
    }

    @Override
    public void appendLong(long val) {
        ensureCapacity(size + 1);
        data[size] = val;
        nulls.clear(size);
        size++;
    }

    @Override
    public Object getObject(int row) {
        checkRowBounds(row);
        if (nulls.get(row)) {
            return null;
        }
        return Long.valueOf(data[row]);
    }

    @Override
    public void setObject(int row, Object value) {
        checkRowBounds(row);
        if (NA.isNA(value)) {
            data[row] = 0L;
            nulls.set(row);
        } else if (value instanceof Number n) {
            data[row] = n.longValue();
            nulls.clear(row);
        } else {
            data[row] = Long.parseLong(value.toString().trim());
            nulls.clear(row);
        }
    }

    @Override
    public void appendObject(Object value) {
        if (NA.isNA(value)) {
            appendNull();
        } else if (value instanceof Number n) {
            appendLong(n.longValue());
        } else {
            appendLong(Long.parseLong(value.toString().trim()));
        }
    }

    @Override
    public void appendNull() {
        ensureCapacity(size + 1);
        data[size] = 0L;
        nulls.set(size);
        size++;
    }

    @Override
    public void remove(int row) {
        checkRowBounds(row);
        int numMoved = size - row - 1;
        if (numMoved > 0) {
            System.arraycopy(data, row + 1, data, row, numMoved);
        }
        data[size - 1] = 0L;

        for (int i = row; i < size - 1; i++) {
            if (nulls.get(i + 1)) {
                nulls.set(i);
            } else {
                nulls.clear(i);
            }
        }
        nulls.clear(size - 1);
        size--;
    }

    @Override
    public ColumnVector copy() {
        long[] copyData = Arrays.copyOf(data, size);
        return new LongColumnVector(name, nullable, copyData, nulls, size);
    }

    @Override
    public ColumnVector slice(int from, int to) {
        checkSliceBounds(from, to);
        int len = to - from;
        long[] sliceData = new long[len];
        System.arraycopy(data, from, sliceData, 0, len);
        BitSet sliceNulls = new BitSet(len);
        for (int i = 0; i < len; i++) {
            if (nulls.get(from + i)) {
                sliceNulls.set(i);
            }
        }
        return new LongColumnVector(name, nullable, sliceData, sliceNulls, len);
    }

    @Override
    public void fillna(Object value) {
        if (NA.isNA(value)) {
            return;
        }
        long fillVal = (value instanceof Number n) ? n.longValue() : Long.parseLong(value.toString().trim());
        for (int i = nulls.nextSetBit(0); i >= 0 && i < size; i = nulls.nextSetBit(i + 1)) {
            data[i] = fillVal;
        }
        nulls.clear();
    }

    @Override
    public void permute(int[] order) {
        long[] newData = new long[size];
        BitSet newNulls = new BitSet(size);
        for (int i = 0; i < size; i++) {
            int src = order[i];
            newData[i] = data[src];
            if (nulls.get(src)) {
                newNulls.set(i);
            }
        }
        this.data = newData;
        this.nulls.clear();
        this.nulls.or(newNulls);
    }

    @Override
    public void filterInPlace(BitSet retainMask, int retainedCount) {
        long[] newData = new long[retainedCount];
        BitSet newNulls = new BitSet(retainedCount);
        int dst = 0;
        for (int i = 0; i < size; i++) {
            if (retainMask.get(i)) {
                newData[dst] = data[i];
                if (nulls.get(i)) {
                    newNulls.set(dst);
                }
                dst++;
            }
        }
        this.data = newData;
        this.nulls.clear();
        this.nulls.or(newNulls);
        this.size = retainedCount;
    }

    @Override
    public int compareRows(int rowA, int rowB) {
        boolean nullA = nulls.get(rowA);
        boolean nullB = nulls.get(rowB);
        if (nullA && nullB) return 0;
        if (nullA) return 1;
        if (nullB) return -1;
        return Long.compare(data[rowA], data[rowB]);
    }

    @Override
    public void clear() {
        this.size = 0;
        this.nulls.clear();
        Arrays.fill(data, 0L);
    }

    public long[] toLongArray() {
        return Arrays.copyOf(data, size);
    }

    @Override
    public double[] toPrimitiveDoubleArray() {
        int count = validCount();
        if (count == 0) {
            return new double[0];
        }
        double[] result = new double[count];
        int idx = 0;
        if (nulls.isEmpty()) {
            for (int i = 0; i < size; i++) {
                result[i] = (double) data[i];
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (!nulls.get(i)) {
                    result[idx++] = (double) data[i];
                }
            }
        }
        return result;
    }

    @Override
    public double sum() {
        int count = validCount();
        if (count == 0) {
            return Double.NaN;
        }
        double s = 0.0;
        if (nulls.isEmpty()) {
            for (int i = 0; i < size; i++) {
                s += (double) data[i];
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (!nulls.get(i)) {
                    s += (double) data[i];
                }
            }
        }
        return s;
    }

    @Override
    public double min() {
        int count = validCount();
        if (count == 0) {
            return Double.NaN;
        }
        long minVal = Long.MAX_VALUE;
        boolean found = false;
        if (nulls.isEmpty()) {
            for (int i = 0; i < size; i++) {
                if (!found || data[i] < minVal) {
                    minVal = data[i];
                    found = true;
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (!nulls.get(i)) {
                    if (!found || data[i] < minVal) {
                        minVal = data[i];
                        found = true;
                    }
                }
            }
        }
        return found ? (double) minVal : Double.NaN;
    }

    @Override
    public double max() {
        int count = validCount();
        if (count == 0) {
            return Double.NaN;
        }
        long maxVal = Long.MIN_VALUE;
        boolean found = false;
        if (nulls.isEmpty()) {
            for (int i = 0; i < size; i++) {
                if (!found || data[i] > maxVal) {
                    maxVal = data[i];
                    found = true;
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (!nulls.get(i)) {
                    if (!found || data[i] > maxVal) {
                        maxVal = data[i];
                        found = true;
                    }
                }
            }
        }
        return found ? (double) maxVal : Double.NaN;
    }

    @Override
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
        if (nulls.isEmpty()) {
            for (int i = 0; i < size; i++) {
                double d = ((double) data[i]) - m;
                sumSq += d * d;
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (!nulls.get(i)) {
                    double d = ((double) data[i]) - m;
                    sumSq += d * d;
                }
            }
        }
        return sumSq / (isSample ? (n - 1) : n);
    }
}
