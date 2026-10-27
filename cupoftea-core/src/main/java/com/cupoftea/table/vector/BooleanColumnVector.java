package com.cupoftea.table.vector;

import java.util.Arrays;
import java.util.BitSet;

import com.cupoftea.table.NA;

/**
 * High-performance columnar vector for booleans.
 */
public class BooleanColumnVector extends ColumnVector {

    private static final long serialVersionUID = 1L;

    private boolean[] data;

    public BooleanColumnVector(String name, int initialCapacity, boolean nullable) {
        super(name, Boolean.class, nullable, initialCapacity);
        this.data = new boolean[Math.max(DEFAULT_INITIAL_CAPACITY, initialCapacity)];
    }

    public BooleanColumnVector(String name, boolean[] data, BitSet nulls, int size) {
        this(name, true, data, nulls, size);
    }

    public BooleanColumnVector(String name, boolean nullable, boolean[] data, BitSet nulls, int size) {
        super(name, Boolean.class, nullable, data.length);
        this.data = data;
        if (nulls != null) {
            this.nulls.or(nulls);
        }
        this.size = size;
    }

    public boolean[] getRawData() {
        return data;
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > data.length) {
            int newCap = Math.max(data.length * 2, minCapacity);
            data = Arrays.copyOf(data, newCap);
        }
    }

    @Override
    public void setSizeUnchecked(int newSize) {
        ensureCapacity(newSize);
        this.size = newSize;
    }

    @Override
    public boolean getBoolean(int row) {
        checkRowBounds(row);
        return data[row];
    }

    @Override
    public void setBoolean(int row, boolean val) {
        checkRowBounds(row);
        data[row] = val;
        nulls.clear(row);
    }

    @Override
    public void appendBoolean(boolean val) {
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
        return Boolean.valueOf(data[row]);
    }

    @Override
    public void setObject(int row, Object value) {
        checkRowBounds(row);
        if (NA.isNA(value)) {
            data[row] = false;
            nulls.set(row);
        } else if (value instanceof Boolean b) {
            setBoolean(row, b);
        } else {
            setBoolean(row, Boolean.parseBoolean(value.toString().trim()));
        }
    }

    @Override
    protected void setObjectValueOnly(int row, Object value) {
        if (value instanceof Boolean b) {
            data[row] = b;
        } else if (value != null && !NA.isNA(value)) {
            data[row] = Boolean.parseBoolean(value.toString().trim());
        }
    }

    @Override
    public void appendObject(Object value) {
        if (NA.isNA(value)) {
            appendNull();
        } else if (value instanceof Boolean b) {
            appendBoolean(b);
        } else {
            appendBoolean(Boolean.parseBoolean(value.toString().trim()));
        }
    }

    @Override
    public void appendNull() {
        ensureCapacity(size + 1);
        data[size] = false;
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
        data[size - 1] = false;

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
        boolean[] copyData = Arrays.copyOf(data, size);
        return new BooleanColumnVector(name, nullable, copyData, nulls, size);
    }

    @Override
    public ColumnVector slice(int from, int to) {
        checkSliceBounds(from, to);
        int len = to - from;
        boolean[] sliceData = new boolean[len];
        System.arraycopy(data, from, sliceData, 0, len);
        BitSet sliceNulls = new BitSet(len);
        if (hasNulls()) {
            for (int i = 0; i < len; i++) {
                if (nulls.get(from + i)) {
                    sliceNulls.set(i);
                }
            }
        }
        return new BooleanColumnVector(name, nullable, sliceData, sliceNulls, len);
    }

    @Override
    public void fillna(Object value) {
        if (NA.isNA(value)) {
            return;
        }
        boolean fillVal = (value instanceof Boolean b) ? b : Boolean.parseBoolean(value.toString().trim());
        for (int i = nulls.nextSetBit(0); i >= 0 && i < size; i = nulls.nextSetBit(i + 1)) {
            data[i] = fillVal;
        }
        nulls.clear();
    }

    @Override
    public void permute(int[] order) {
        boolean[] newData = new boolean[size];
        BitSet newNulls = new BitSet(size);
        boolean anyNulls = hasNulls();
        for (int i = 0; i < size; i++) {
            int src = order[i];
            newData[i] = data[src];
            if (anyNulls && nulls.get(src)) {
                newNulls.set(i);
            }
        }
        this.data = newData;
        this.nulls.clear();
        this.nulls.or(newNulls);
    }

    @Override
    public ColumnVector filter(BitSet retainMask, int retainedCount) {
        boolean[] newData = new boolean[retainedCount];
        BitSet newNulls = new BitSet(retainedCount);
        boolean anyNulls = hasNulls();
        int dst = 0;
        for (int i = retainMask.nextSetBit(0); i >= 0 && i < size; i = retainMask.nextSetBit(i + 1)) {
            newData[dst] = data[i];
            if (anyNulls && nulls.get(i)) {
                newNulls.set(dst);
            }
            dst++;
        }
        return new BooleanColumnVector(name, nullable, newData, newNulls, retainedCount);
    }

    @Override
    public void filterInPlace(BitSet retainMask, int retainedCount) {
        boolean[] newData = new boolean[retainedCount];
        BitSet newNulls = new BitSet(retainedCount);
        boolean anyNulls = hasNulls();
        int dst = 0;
        for (int i = retainMask.nextSetBit(0); i >= 0 && i < size; i = retainMask.nextSetBit(i + 1)) {
            newData[dst] = data[i];
            if (anyNulls && nulls.get(i)) {
                newNulls.set(dst);
            }
            dst++;
        }
        this.data = newData;
        this.nulls.clear();
        this.nulls.or(newNulls);
        this.size = retainedCount;
    }

    @Override
    public void copyIndexedValuesOnly(ColumnVector src, int[] srcIndices, int count, int dstOffset) {
        if (src instanceof BooleanColumnVector boolSrc) {
            boolean[] srcData = boolSrc.data;
            boolean[] dstData = this.data;
            for (int i = 0; i < count; i++) {
                dstData[dstOffset + i] = srcData[srcIndices[i]];
            }
        } else {
            super.copyIndexedValuesOnly(src, srcIndices, count, dstOffset);
        }
    }

    @Override
    public int compareRows(int rowA, int rowB) {
        boolean nullA = nulls.get(rowA);
        boolean nullB = nulls.get(rowB);
        if (nullA && nullB) return 0;
        if (nullA) return 1;
        if (nullB) return -1;
        return Boolean.compare(data[rowA], data[rowB]);
    }

    @Override
    public void clear() {
        this.size = 0;
        this.nulls.clear();
        Arrays.fill(data, false);
    }

    public boolean[] toBooleanArray() {
        return Arrays.copyOf(data, size);
    }
}
