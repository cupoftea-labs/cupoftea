package com.cupoftea.table.vector;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.IntUnaryOperator;

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

    public IntColumnVector(String name, int[] data, BitSet nulls, int size) {
        this(name, true, data, nulls, size);
    }

    public IntColumnVector(String name, boolean nullable, int[] data, BitSet nulls, int size) {
        super(name, Integer.class, nullable, data.length);
        this.data = data;
        if (nulls != null) {
            this.nulls.or(nulls);
        }
        this.size = size;
    }

    public int[] getRawData() {
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
    protected void setObjectValueOnly(int row, Object value) {
        if (value instanceof Number n) {
            data[row] = n.intValue();
        } else if (value != null && !NA.isNA(value)) {
            data[row] = Integer.parseInt(value.toString().trim());
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
        if (hasNulls()) {
            for (int i = 0; i < len; i++) {
                if (nulls.get(from + i)) {
                    sliceNulls.set(i);
                }
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
        int[] newData = new int[retainedCount];
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
        return new IntColumnVector(name, nullable, newData, newNulls, retainedCount);
    }

    @Override
    public void filterInPlace(BitSet retainMask, int retainedCount) {
        int[] newData = new int[retainedCount];
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
        if (src instanceof IntColumnVector intSrc) {
            int[] srcData = intSrc.data;
            int[] dstData = this.data;
            for (int i = 0; i < count; i++) {
                dstData[dstOffset + i] = srcData[srcIndices[i]];
            }
        } else {
            super.copyIndexedValuesOnly(src, srcIndices, count, dstOffset);
        }
    }

    public IntColumnVector mapInt(IntUnaryOperator mapper) {
        Objects.requireNonNull(mapper, "Mapper cannot be null");
        int[] newData = new int[size];
        BitSet newNulls = (BitSet) nulls.clone();
        boolean anyNulls = hasNulls();
        if (!anyNulls) {
            for (int i = 0; i < size; i++) {
                newData[i] = mapper.applyAsInt(data[i]);
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (!nulls.get(i)) {
                    newData[i] = mapper.applyAsInt(data[i]);
                }
            }
        }
        return new IntColumnVector(name, nullable, newData, newNulls, size);
    }

    public IntColumnVector mapInt(IntUnaryOperator mapper, ExecutorService executor, int parallelThreshold) {
        Objects.requireNonNull(mapper, "Mapper cannot be null");
        if (size < parallelThreshold || size <= 1) {
            return mapInt(mapper);
        }
        int[] newData = new int[size];
        BitSet newNulls = (BitSet) nulls.clone();
        boolean anyNulls = hasNulls();
        ParallelVectorEngine.executeChunks(size, executor, (chunkIdx, start, end) -> {
            if (!anyNulls) {
                for (int i = start; i < end; i++) {
                    newData[i] = mapper.applyAsInt(data[i]);
                }
            } else {
                for (int i = start; i < end; i++) {
                    if (!nulls.get(i)) {
                        newData[i] = mapper.applyAsInt(data[i]);
                    }
                }
            }
            return null;
        });
        return new IntColumnVector(name, nullable, newData, newNulls, size);
    }

    // --- Primitive Vectorized Map-Reduce Aggregations ---

    public long sumLong() {
        return sumLong(0, size);
    }

    public long sumLong(int from, int to) {
        checkSliceBounds(from, to);
        long sum = 0L;
        if (!hasNulls()) {
            for (int i = from; i < to; i++) {
                sum += data[i];
            }
        } else {
            for (int i = from; i < to; i++) {
                if (!nulls.get(i)) {
                    sum += data[i];
                }
            }
        }
        return sum;
    }

    public long sumLong(ExecutorService executor, int parallelThreshold) {
        if (size < parallelThreshold || size <= 1) {
            return sumLong(0, size);
        }
        List<Long> partials = ParallelVectorEngine.executeChunks(
            size, executor, (chunkIdx, start, end) -> sumLong(start, end)
        );
        long total = 0L;
        for (long p : partials) {
            total += p;
        }
        return total;
    }

    @Override
    public Object sum(int from, int to) {
        checkSliceBounds(from, to);
        if (from == to) {
            return null;
        }
        if (!hasNulls()) {
            long sum = 0L;
            for (int i = from; i < to; i++) {
                sum += data[i];
            }
            return (int) sum;
        }
        long sum = 0L;
        boolean found = false;
        for (int i = from; i < to; i++) {
            if (!nulls.get(i)) {
                sum += data[i];
                found = true;
            }
        }
        return found ? (int) sum : null;
    }

    @Override
    public Object sum(ExecutorService executor, int parallelThreshold) {
        if (size < parallelThreshold || size <= 1) {
            return sum(0, size);
        }
        if (!hasNulls()) {
            return (int) sumLong(executor, parallelThreshold);
        }
        List<long[]> partials = ParallelVectorEngine.executeChunks(size, executor, (chunkIdx, start, end) -> {
            long s = 0L;
            long count = 0L;
            for (int i = start; i < end; i++) {
                if (!nulls.get(i)) {
                    s += data[i];
                    count++;
                }
            }
            return new long[]{s, count};
        });
        long totalSum = 0L;
        long totalCount = 0L;
        for (long[] p : partials) {
            totalSum += p[0];
            totalCount += p[1];
        }
        return totalCount > 0 ? (int) totalSum : null;
    }

    @Override
    public Object sumIndexed(int[] indices, int count) {
        if (count <= 0) {
            return null;
        }
        if (!hasNulls()) {
            long sum = 0L;
            for (int i = 0; i < count; i++) {
                sum += data[indices[i]];
            }
            return (int) sum;
        }
        long sum = 0L;
        boolean found = false;
        for (int i = 0; i < count; i++) {
            int row = indices[i];
            if (!nulls.get(row)) {
                sum += data[row];
                found = true;
            }
        }
        return found ? (int) sum : null;
    }

    @Override
    public Object min(int from, int to) {
        checkSliceBounds(from, to);
        if (from == to) {
            return null;
        }
        if (!hasNulls()) {
            int min = data[from];
            for (int i = from + 1; i < to; i++) {
                if (data[i] < min) {
                    min = data[i];
                }
            }
            return min;
        }
        int min = Integer.MAX_VALUE;
        boolean found = false;
        for (int i = from; i < to; i++) {
            if (!nulls.get(i)) {
                if (!found || data[i] < min) {
                    min = data[i];
                    found = true;
                }
            }
        }
        return found ? min : null;
    }

    @Override
    public Object min(ExecutorService executor, int parallelThreshold) {
        if (size < parallelThreshold || size <= 1) {
            return min(0, size);
        }
        List<Object> partials = ParallelVectorEngine.executeChunks(
            size, executor, (chunkIdx, start, end) -> min(start, end)
        );
        Integer best = null;
        for (Object p : partials) {
            if (p != null) {
                int v = (Integer) p;
                if (best == null || v < best) {
                    best = v;
                }
            }
        }
        return best;
    }

    @Override
    public Object minIndexed(int[] indices, int count) {
        if (count <= 0) {
            return null;
        }
        if (!hasNulls()) {
            int min = data[indices[0]];
            for (int i = 1; i < count; i++) {
                int v = data[indices[i]];
                if (v < min) min = v;
            }
            return min;
        }
        int min = Integer.MAX_VALUE;
        boolean found = false;
        for (int i = 0; i < count; i++) {
            int row = indices[i];
            if (!nulls.get(row)) {
                int v = data[row];
                if (!found || v < min) {
                    min = v;
                    found = true;
                }
            }
        }
        return found ? min : null;
    }

    @Override
    public Object max(int from, int to) {
        checkSliceBounds(from, to);
        if (from == to) {
            return null;
        }
        if (!hasNulls()) {
            int max = data[from];
            for (int i = from + 1; i < to; i++) {
                if (data[i] > max) {
                    max = data[i];
                }
            }
            return max;
        }
        int max = Integer.MIN_VALUE;
        boolean found = false;
        for (int i = from; i < to; i++) {
            if (!nulls.get(i)) {
                if (!found || data[i] > max) {
                    max = data[i];
                    found = true;
                }
            }
        }
        return found ? max : null;
    }

    @Override
    public Object max(ExecutorService executor, int parallelThreshold) {
        if (size < parallelThreshold || size <= 1) {
            return max(0, size);
        }
        List<Object> partials = ParallelVectorEngine.executeChunks(
            size, executor, (chunkIdx, start, end) -> max(start, end)
        );
        Integer best = null;
        for (Object p : partials) {
            if (p != null) {
                int v = (Integer) p;
                if (best == null || v > best) {
                    best = v;
                }
            }
        }
        return best;
    }

    @Override
    public Object maxIndexed(int[] indices, int count) {
        if (count <= 0) {
            return null;
        }
        if (!hasNulls()) {
            int max = data[indices[0]];
            for (int i = 1; i < count; i++) {
                int v = data[indices[i]];
                if (v > max) max = v;
            }
            return max;
        }
        int max = Integer.MIN_VALUE;
        boolean found = false;
        for (int i = 0; i < count; i++) {
            int row = indices[i];
            if (!nulls.get(row)) {
                int v = data[row];
                if (!found || v > max) {
                    max = v;
                    found = true;
                }
            }
        }
        return found ? max : null;
    }

    @Override
    public Object mean(int from, int to) {
        checkSliceBounds(from, to);
        if (from == to) {
            return null;
        }
        if (!hasNulls()) {
            long sum = 0L;
            for (int i = from; i < to; i++) {
                sum += data[i];
            }
            return (double) sum / (double) (to - from);
        }
        long sum = 0L;
        long count = 0L;
        for (int i = from; i < to; i++) {
            if (!nulls.get(i)) {
                sum += data[i];
                count++;
            }
        }
        return count > 0 ? (double) sum / (double) count : null;
    }

    @Override
    public Object mean(ExecutorService executor, int parallelThreshold) {
        if (size < parallelThreshold || size <= 1) {
            return mean(0, size);
        }
        if (!hasNulls()) {
            long sum = sumLong(executor, parallelThreshold);
            return (double) sum / (double) size;
        }
        List<long[]> partials = ParallelVectorEngine.executeChunks(size, executor, (chunkIdx, start, end) -> {
            long s = 0L;
            long count = 0L;
            for (int i = start; i < end; i++) {
                if (!nulls.get(i)) {
                    s += data[i];
                    count++;
                }
            }
            return new long[]{s, count};
        });
        long totalSum = 0L;
        long totalCount = 0L;
        for (long[] p : partials) {
            totalSum += p[0];
            totalCount += p[1];
        }
        return totalCount > 0 ? (double) totalSum / (double) totalCount : null;
    }

    @Override
    public Object meanIndexed(int[] indices, int count) {
        if (count <= 0) {
            return null;
        }
        if (!hasNulls()) {
            long sum = 0L;
            for (int i = 0; i < count; i++) {
                sum += data[indices[i]];
            }
            return (double) sum / (double) count;
        }
        long sum = 0L;
        long validCount = 0L;
        for (int i = 0; i < count; i++) {
            int row = indices[i];
            if (!nulls.get(row)) {
                sum += data[row];
                validCount++;
            }
        }
        return validCount > 0 ? (double) sum / (double) validCount : null;
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
