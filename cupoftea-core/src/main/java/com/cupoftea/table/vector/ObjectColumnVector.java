package com.cupoftea.table.vector;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Date;

import com.cupoftea.table.Group;
import com.cupoftea.table.NA;

/**
 * Columnar vector for general reference objects, dates, timestamps, big decimals, and groups.
 */
public class ObjectColumnVector extends ColumnVector {

    private static final long serialVersionUID = 1L;

    private Object[] data;

    public ObjectColumnVector(String name, Class<?> type, int initialCapacity, boolean nullable) {
        super(name, type != null ? type : Object.class, nullable, initialCapacity);
        this.data = new Object[Math.max(DEFAULT_INITIAL_CAPACITY, initialCapacity)];
    }

    public ObjectColumnVector(String name, Class<?> type, Object[] data, BitSet nulls, int size) {
        this(name, type, true, data, nulls, size);
    }

    public ObjectColumnVector(String name, Class<?> type, boolean nullable, Object[] data, BitSet nulls, int size) {
        super(name, type != null ? type : Object.class, nullable, data.length);
        this.data = data;
        if (nulls != null) {
            this.nulls.or(nulls);
        }
        this.size = size;
    }

    public Object[] getRawData() {
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
    public Object getObject(int row) {
        checkRowBounds(row);
        if (nulls.get(row)) {
            return null;
        }
        return data[row];
    }

    @Override
    public void setObject(int row, Object value) {
        checkRowBounds(row);
        if (NA.isNA(value)) {
            data[row] = null;
            nulls.set(row);
        } else {
            data[row] = value;
            nulls.clear(row);
        }
    }

    @Override
    protected void setObjectValueOnly(int row, Object value) {
        if (value instanceof Group group) {
            data[row] = new Group(group);
        } else if (!NA.isNA(value)) {
            data[row] = value;
        }
    }

    @Override
    public void appendObject(Object value) {
        ensureCapacity(size + 1);
        if (NA.isNA(value)) {
            data[size] = null;
            nulls.set(size);
        } else {
            data[size] = value;
            nulls.clear(size);
        }
        size++;
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
        Object[] copyData = new Object[size];
        for (int i = 0; i < size; i++) {
            Object val = data[i];
            if (val instanceof Group group) {
                copyData[i] = new Group(group);
            } else {
                copyData[i] = val;
            }
        }
        return new ObjectColumnVector(name, type, nullable, copyData, nulls, size);
    }

    @Override
    public ColumnVector slice(int from, int to) {
        checkSliceBounds(from, to);
        int len = to - from;
        Object[] sliceData = new Object[len];
        for (int i = 0; i < len; i++) {
            Object val = data[from + i];
            if (val instanceof Group group) {
                sliceData[i] = new Group(group);
            } else {
                sliceData[i] = val;
            }
        }
        BitSet sliceNulls = new BitSet(len);
        if (hasNulls()) {
            for (int i = 0; i < len; i++) {
                if (nulls.get(from + i)) {
                    sliceNulls.set(i);
                }
            }
        }
        return new ObjectColumnVector(name, type, nullable, sliceData, sliceNulls, len);
    }

    @Override
    public void fillna(Object value) {
        if (NA.isNA(value)) {
            return;
        }
        for (int i = nulls.nextSetBit(0); i >= 0 && i < size; i = nulls.nextSetBit(i + 1)) {
            data[i] = value;
        }
        nulls.clear();
    }

    @Override
    public void permute(int[] order) {
        Object[] newData = new Object[size];
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
        Object[] newData = new Object[retainedCount];
        BitSet newNulls = new BitSet(retainedCount);
        boolean anyNulls = hasNulls();
        int dst = 0;
        for (int i = retainMask.nextSetBit(0); i >= 0 && i < size; i = retainMask.nextSetBit(i + 1)) {
            Object val = data[i];
            newData[dst] = (val instanceof Group group) ? new Group(group) : val;
            if (anyNulls && nulls.get(i)) {
                newNulls.set(dst);
            }
            dst++;
        }
        return new ObjectColumnVector(name, type, nullable, newData, newNulls, retainedCount);
    }

    @Override
    public void filterInPlace(BitSet retainMask, int retainedCount) {
        Object[] newData = new Object[retainedCount];
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
        if (src instanceof ObjectColumnVector objSrc) {
            Object[] srcData = objSrc.data;
            Object[] dstData = this.data;
            for (int i = 0; i < count; i++) {
                Object val = srcData[srcIndices[i]];
                dstData[dstOffset + i] = (val instanceof Group group) ? new Group(group) : val;
            }
        } else {
            super.copyIndexedValuesOnly(src, srcIndices, count, dstOffset);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public int compareRows(int rowA, int rowB) {
        boolean nullA = nulls.get(rowA);
        boolean nullB = nulls.get(rowB);
        if (nullA && nullB) return 0;
        if (nullA) return 1;
        if (nullB) return -1;

        Object valA = data[rowA];
        Object valB = data[rowB];

        if (valA instanceof Instant instA && valB instanceof Instant instB) {
            return instA.compareTo(instB);
        }
        if (valA instanceof LocalDate dateA && valB instanceof LocalDate dateB) {
            return dateA.compareTo(dateB);
        }
        if (valA instanceof LocalDateTime dtA && valB instanceof LocalDateTime dtB) {
            return dtA.compareTo(dtB);
        }
        if (valA instanceof BigDecimal decA && valB instanceof BigDecimal decB) {
            return decA.compareTo(decB);
        }
        if (valA instanceof Date dateA && valB instanceof Date dateB) {
            return Long.compare(dateA.getTime(), dateB.getTime());
        }
        if (valA instanceof Number numA && valB instanceof Number numB) {
            return Double.compare(numA.doubleValue(), numB.doubleValue());
        }
        if (valA instanceof Comparable compA && valB instanceof Comparable) {
            return compA.compareTo(valB);
        }
        return 0;
    }

    @Override
    public void clear() {
        this.size = 0;
        this.nulls.clear();
        Arrays.fill(data, null);
    }
}
