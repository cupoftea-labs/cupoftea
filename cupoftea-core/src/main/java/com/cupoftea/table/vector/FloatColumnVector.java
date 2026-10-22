package com.cupoftea.table.vector;

import java.util.Arrays;
import java.util.BitSet;

import com.cupoftea.table.NA;

/**
 * High-performance primitive columnar vector for 32-bit IEEE 754 floating point floats.
 */
public class FloatColumnVector extends ColumnVector {

    private static final long serialVersionUID = 1L;

    private float[] data;

    public FloatColumnVector(String name, int initialCapacity, boolean nullable) {
        super(name, Float.class, nullable, initialCapacity);
        this.data = new float[Math.max(DEFAULT_INITIAL_CAPACITY, initialCapacity)];
    }

    private FloatColumnVector(String name, boolean nullable, float[] data, BitSet nulls, int size) {
        super(name, Float.class, nullable, data.length);
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
    public float getFloat(int row) {
        checkRowBounds(row);
        return data[row];
    }

    @Override
    public void setFloat(int row, float val) {
        checkRowBounds(row);
        if (Float.isNaN(val)) {
            data[row] = Float.NaN;
            nulls.set(row);
        } else {
            data[row] = val;
            nulls.clear(row);
        }
    }

    @Override
    public void appendFloat(float val) {
        ensureCapacity(size + 1);
        if (Float.isNaN(val)) {
            data[size] = Float.NaN;
            nulls.set(size);
        } else {
            data[size] = val;
            nulls.clear(size);
        }
        size++;
    }

    @Override
    public Object getObject(int row) {
        checkRowBounds(row);
        if (nulls.get(row)) {
            return null;
        }
        return Float.valueOf(data[row]);
    }

    @Override
    public void setObject(int row, Object value) {
        checkRowBounds(row);
        if (NA.isNA(value)) {
            data[row] = Float.NaN;
            nulls.set(row);
        } else if (value instanceof Number n) {
            setFloat(row, n.floatValue());
        } else {
            setFloat(row, Float.parseFloat(value.toString().trim()));
        }
    }

    @Override
    public void appendObject(Object value) {
        if (NA.isNA(value)) {
            appendNull();
        } else if (value instanceof Number n) {
            appendFloat(n.floatValue());
        } else {
            appendFloat(Float.parseFloat(value.toString().trim()));
        }
    }

    @Override
    public void appendNull() {
        ensureCapacity(size + 1);
        data[size] = Float.NaN;
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
        data[size - 1] = 0.0f;

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
        float[] copyData = Arrays.copyOf(data, size);
        return new FloatColumnVector(name, nullable, copyData, nulls, size);
    }

    @Override
    public ColumnVector slice(int from, int to) {
        checkSliceBounds(from, to);
        int len = to - from;
        float[] sliceData = new float[len];
        System.arraycopy(data, from, sliceData, 0, len);
        BitSet sliceNulls = new BitSet(len);
        for (int i = 0; i < len; i++) {
            if (nulls.get(from + i)) {
                sliceNulls.set(i);
            }
        }
        return new FloatColumnVector(name, nullable, sliceData, sliceNulls, len);
    }

    @Override
    public void fillna(Object value) {
        if (NA.isNA(value)) {
            return;
        }
        float fillVal = (value instanceof Number n) ? n.floatValue() : Float.parseFloat(value.toString().trim());
        for (int i = nulls.nextSetBit(0); i >= 0 && i < size; i = nulls.nextSetBit(i + 1)) {
            data[i] = fillVal;
        }
        nulls.clear();
    }

    @Override
    public void permute(int[] order) {
        float[] newData = new float[size];
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
        float[] newData = new float[retainedCount];
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
        return Float.compare(data[rowA], data[rowB]);
    }

    @Override
    public void clear() {
        this.size = 0;
        this.nulls.clear();
        Arrays.fill(data, 0.0f);
    }

    public float[] toFloatArray() {
        return Arrays.copyOf(data, size);
    }
}
