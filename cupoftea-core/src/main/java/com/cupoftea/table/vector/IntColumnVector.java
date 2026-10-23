package com.cupoftea.table.vector;

import java.util.Arrays;
import java.util.BitSet;

import com.cupoftea.table.NA;

/**
 * High-performance primitive columnar vector for 32-bit integers.
 * Stores raw ints in an unboxed array and tracks null/NA presence via BitSet.
 */
public class IntColumnVector extends ColumnVector {

    private static final long serialVersionUID = 1L;

    private int[] data;

    public IntColumnVector(String name, int initialCapacity, boolean nullable) {
        super(name, Integer.class, nullable, initialCapacity);
        this.data = new int[Math.max(DEFAULT_INITIAL_CAPACITY, initialCapacity)];
    }

    private IntColumnVector(String name, boolean nullable, int[] data, BitSet nulls, int size) {
        super(name, Integer.class, nullable, data.length);
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
    public int getInt(int row) {
        checkRowBounds(row);
        return data[row];
    }

    @Override
    public void setInt(int row, int val) {
        checkRowBounds(row);
        data[row] = val;
        nulls.clear(row);
    }

    @Override
    public void appendInt(int val) {
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
        return Integer.valueOf(data[row]);
    }

    @Override
    public void setObject(int row, Object value) {
        checkRowBounds(row);
        if (NA.isNA(value)) {
            data[row] = 0;
            nulls.set(row);
        } else if (value instanceof Number n) {
            data[row] = n.intValue();
            nulls.clear(row);
        } else {
            data[row] = Integer.parseInt(value.toString().trim());
            nulls.clear(row);
        }
    }

    @Override
    public void appendObject(Object value) {
        if (NA.isNA(value)) {
            appendNull();
        } else if (value instanceof Number n) {
            appendInt(n.intValue());
        } else {
            appendInt(Integer.parseInt(value.toString().trim()));
        }
    }

    @Override
    public void appendNull() {
        ensureCapacity(size + 1);
        data[size] = 0;
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
        data[size - 1] = 0;

        // Shift bitset
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
        int[] copyData = Arrays.copyOf(data, size);
        return new IntColumnVector(name, nullable, copyData, nulls, size);
    }

    @Override
    public ColumnVector slice(int from, int to) {
        checkSliceBounds(from, to);
        int len = to - from;
        int[] sliceData = new int[len];
        System.arraycopy(data, from, sliceData, 0, len);
        BitSet sliceNulls = new BitSet(len);
        for (int i = 0; i < len; i++) {
            if (nulls.get(from + i)) {
                sliceNulls.set(i);
            }
        }
        return new IntColumnVector(name, nullable, sliceData, sliceNulls, len);
    }

    @Override
    public void fillna(Object value) {
        if (NA.isNA(value)) {
            return;
        }
        int fillVal = (value instanceof Number n) ? n.intValue() : Integer.parseInt(value.toString().trim());
        for (int i = nulls.nextSetBit(0); i >= 0 && i < size; i = nulls.nextSetBit(i + 1)) {
            data[i] = fillVal;
        }
        nulls.clear();
    }

    @Override
    public void permute(int[] order) {
        int[] newData = new int[size];
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
        int[] newData = new int[retainedCount];
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
        return Integer.compare(data[rowA], data[rowB]);
    }

    @Override
    public void clear() {
        this.size = 0;
        this.nulls.clear();
        Arrays.fill(data, 0);
    }

    public int[] toIntArray() {
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
                result[i] = data[i];
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (!nulls.get(i)) {
                    result[idx++] = data[i];
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
                s += data[i];
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (!nulls.get(i)) {
                    s += data[i];
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
        int minVal = Integer.MAX_VALUE;
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
        return found ? minVal : Double.NaN;
    }

    @Override
    public double max() {
        int count = validCount();
        if (count == 0) {
            return Double.NaN;
        }
        int maxVal = Integer.MIN_VALUE;
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
        return found ? maxVal : Double.NaN;
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
                double d = data[i] - m;
                sumSq += d * d;
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (!nulls.get(i)) {
                    double d = data[i] - m;
                    sumSq += d * d;
                }
            }
        }
        return sumSq / (isSample ? (n - 1) : n);
    }
}
