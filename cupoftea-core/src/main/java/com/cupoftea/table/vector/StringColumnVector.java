package com.cupoftea.table.vector;

import java.util.Arrays;
import java.util.BitSet;

import com.cupoftea.table.NA;

/**
 * Columnar vector for Strings.
 */
public class StringColumnVector extends ColumnVector {

    private static final long serialVersionUID = 1L;

    private String[] data;

    public StringColumnVector(String name, int initialCapacity, boolean nullable) {
        super(name, String.class, nullable, initialCapacity);
        this.data = new String[Math.max(DEFAULT_INITIAL_CAPACITY, initialCapacity)];
    }

    private StringColumnVector(String name, boolean nullable, String[] data, BitSet nulls, int size) {
        super(name, String.class, nullable, data.length);
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
    public String getString(int row) {
        checkRowBounds(row);
        if (nulls.get(row)) {
            return null;
        }
        return data[row];
    }

    @Override
    public void setString(int row, String val) {
        checkRowBounds(row);
        data[row] = val;
        if (val == null) {
            nulls.set(row);
        } else {
            nulls.clear(row);
        }
    }

    @Override
    public void appendString(String val) {
        ensureCapacity(size + 1);
        data[size] = val;
        if (val == null) {
            nulls.set(size);
        } else {
            nulls.clear(size);
        }
        size++;
    }

    @Override
    public Object getObject(int row) {
        return getString(row);
    }

    @Override
    public void setObject(int row, Object value) {
        checkRowBounds(row);
        if (NA.isNA(value)) {
            data[row] = null;
            nulls.set(row);
        } else {
            data[row] = value.toString();
            nulls.clear(row);
        }
    }

    @Override
    public void appendObject(Object value) {
        if (NA.isNA(value)) {
            appendNull();
        } else {
            appendString(value.toString());
        }
    }

    @Override
    public void appendNull() {
        ensureCapacity(size + 1);
        data[size] = null;
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
        data[size - 1] = null;

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
        String[] copyData = Arrays.copyOf(data, size);
        return new StringColumnVector(name, nullable, copyData, nulls, size);
    }

    @Override
    public ColumnVector slice(int from, int to) {
        checkSliceBounds(from, to);
        int len = to - from;
        String[] sliceData = new String[len];
        System.arraycopy(data, from, sliceData, 0, len);
        BitSet sliceNulls = new BitSet(len);
        for (int i = 0; i < len; i++) {
            if (nulls.get(from + i)) {
                sliceNulls.set(i);
            }
        }
        return new StringColumnVector(name, nullable, sliceData, sliceNulls, len);
    }

    @Override
    public void fillna(Object value) {
        if (NA.isNA(value)) {
            return;
        }
        String fillVal = value.toString();
        for (int i = nulls.nextSetBit(0); i >= 0 && i < size; i = nulls.nextSetBit(i + 1)) {
            data[i] = fillVal;
        }
        nulls.clear();
    }

    @Override
    public void permute(int[] order) {
        String[] newData = new String[size];
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
        String[] newData = new String[retainedCount];
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
        return data[rowA].compareTo(data[rowB]);
    }

    @Override
    public void clear() {
        this.size = 0;
        this.nulls.clear();
        Arrays.fill(data, null);
    }

    public String[] toStringArray() {
        return Arrays.copyOf(data, size);
    }
}
