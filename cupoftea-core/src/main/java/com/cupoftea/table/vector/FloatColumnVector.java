package com.cupoftea.table.vector;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ExecutorService;

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

    public FloatColumnVector(String name, float[] data, BitSet nulls, int size) {
        this(name, true, data, nulls, size);
    }

    public FloatColumnVector(String name, boolean nullable, float[] data, BitSet nulls, int size) {
        super(name, Float.class, nullable, data.length);
        this.data = data;
        if (nulls != null) {
            this.nulls.or(nulls);
        }
        this.size = size;
    }

    public float[] getRawData() {
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
    protected void setObjectValueOnly(int row, Object value) {
        if (value instanceof Number n) {
            data[row] = n.floatValue();
        } else if (value != null && !NA.isNA(value)) {
            data[row] = Float.parseFloat(value.toString().trim());
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
        if (hasNulls()) {
            for (int i = 0; i < len; i++) {
                if (nulls.get(from + i)) {
                    sliceNulls.set(i);
                }
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
        float[] newData = new float[retainedCount];
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
        return new FloatColumnVector(name, nullable, newData, newNulls, retainedCount);
    }

    @Override
    public void filterInPlace(BitSet retainMask, int retainedCount) {
        float[] newData = new float[retainedCount];
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
        if (src instanceof FloatColumnVector floatSrc) {
            float[] srcData = floatSrc.data;
            float[] dstData = this.data;
            for (int i = 0; i < count; i++) {
                dstData[dstOffset + i] = srcData[srcIndices[i]];
            }
        } else {
            super.copyIndexedValuesOnly(src, srcIndices, count, dstOffset);
        }
    }

    // --- Primitive Vectorized Map-Reduce Aggregations ---

    private double sumSingleBlock(int start, int end) {
        double s = 0.0;
        if (!hasNulls()) {
            for (int i = start; i < end; i++) {
                s += data[i];
            }
        } else {
            for (int i = start; i < end; i++) {
                if (!nulls.get(i)) {
                    s += data[i];
                }
            }
        }
        return s;
    }

    public double sumDouble(int from, int to) {
        checkSliceBounds(from, to);
        int len = to - from;
        if (len <= 0) {
            return 0.0;
        }
        int blockSize = ParallelVectorEngine.DETERMINISTIC_FP_BLOCK_SIZE;
        int numBlocks = (len + blockSize - 1) / blockSize;
        double total = 0.0;
        for (int b = 0; b < numBlocks; b++) {
            int bStart = from + b * blockSize;
            int bEnd = Math.min(to, bStart + blockSize);
            total += sumSingleBlock(bStart, bEnd);
        }
        return total;
    }

    public double sumDouble(ExecutorService executor, int parallelThreshold) {
        if (size < parallelThreshold || size <= 1) {
            return sumDouble(0, size);
        }
        int blockSize = ParallelVectorEngine.DETERMINISTIC_FP_BLOCK_SIZE;
        int numBlocks = (size + blockSize - 1) / blockSize;
        if (numBlocks <= 1) {
            return sumDouble(0, size);
        }
        double[] blockSums = new double[numBlocks];
        ParallelVectorEngine.executeChunks(numBlocks, executor, (chunkIdx, bStart, bEnd) -> {
            for (int b = bStart; b < bEnd; b++) {
                int rowStart = b * blockSize;
                int rowEnd = Math.min(size, rowStart + blockSize);
                blockSums[b] = sumSingleBlock(rowStart, rowEnd);
            }
            return null;
        });
        double total = 0.0;
        for (int b = 0; b < numBlocks; b++) {
            total += blockSums[b];
        }
        return total;
    }

    @Override
    public Object sum(int from, int to) {
        checkSliceBounds(from, to);
        if (from == to) {
            return null;
        }
        int validCount = countNonNull(from, to);
        if (validCount == 0) {
            return null;
        }
        return (float) sumDouble(from, to);
    }

    @Override
    public Object sum(ExecutorService executor, int parallelThreshold) {
        if (size < parallelThreshold || size <= 1) {
            return sum(0, size);
        }
        int validCount = countNonNull(executor, parallelThreshold);
        if (validCount == 0) {
            return null;
        }
        return (float) sumDouble(executor, parallelThreshold);
    }

    @Override
    public Object sumIndexed(int[] indices, int count) {
        if (count <= 0) {
            return null;
        }
        double sum = 0.0;
        long validCount = 0L;
        boolean anyNulls = hasNulls();
        for (int i = 0; i < count; i++) {
            int row = indices[i];
            if (!anyNulls || !nulls.get(row)) {
                sum += data[row];
                validCount++;
            }
        }
        return validCount > 0 ? (float) sum : null;
    }

    @Override
    public Object min(int from, int to) {
        checkSliceBounds(from, to);
        if (from == to) {
            return null;
        }
        if (!hasNulls()) {
            float min = data[from];
            for (int i = from + 1; i < to; i++) {
                if (data[i] < min) {
                    min = data[i];
                }
            }
            return min;
        }
        float min = Float.POSITIVE_INFINITY;
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
        Float best = null;
        for (Object p : partials) {
            if (p != null) {
                float v = (Float) p;
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
        float min = Float.POSITIVE_INFINITY;
        boolean found = false;
        boolean anyNulls = hasNulls();
        for (int i = 0; i < count; i++) {
            int row = indices[i];
            if (!anyNulls || !nulls.get(row)) {
                float v = data[row];
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
            float max = data[from];
            for (int i = from + 1; i < to; i++) {
                if (data[i] > max) {
                    max = data[i];
                }
            }
            return max;
        }
        float max = Float.NEGATIVE_INFINITY;
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
        Float best = null;
        for (Object p : partials) {
            if (p != null) {
                float v = (Float) p;
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
        float max = Float.NEGATIVE_INFINITY;
        boolean found = false;
        boolean anyNulls = hasNulls();
        for (int i = 0; i < count; i++) {
            int row = indices[i];
            if (!anyNulls || !nulls.get(row)) {
                float v = data[row];
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
        int validCount = countNonNull(from, to);
        if (validCount == 0) {
            return null;
        }
        return sumDouble(from, to) / (double) validCount;
    }

    @Override
    public Object mean(ExecutorService executor, int parallelThreshold) {
        if (size < parallelThreshold || size <= 1) {
            return mean(0, size);
        }
        int validCount = countNonNull(executor, parallelThreshold);
        if (validCount == 0) {
            return null;
        }
        return sumDouble(executor, parallelThreshold) / (double) validCount;
    }

    @Override
    public Object meanIndexed(int[] indices, int count) {
        if (count <= 0) {
            return null;
        }
        double sum = 0.0;
        long validCount = 0L;
        boolean anyNulls = hasNulls();
        for (int i = 0; i < count; i++) {
            int row = indices[i];
            if (!anyNulls || !nulls.get(row)) {
                sum += data[row];
                validCount++;
            }
        }
        return validCount > 0 ? sum / (double) validCount : null;
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
        float minVal = Float.POSITIVE_INFINITY;
        boolean found = false;
        if (nulls.isEmpty()) {
            for (int i = 0; i < size; i++) {
                if (!found || Float.compare(data[i], minVal) < 0) {
                    minVal = data[i];
                    found = true;
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (!nulls.get(i)) {
                    if (!found || Float.compare(data[i], minVal) < 0) {
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
        float maxVal = Float.NEGATIVE_INFINITY;
        boolean found = false;
        if (nulls.isEmpty()) {
            for (int i = 0; i < size; i++) {
                if (!found || Float.compare(data[i], maxVal) > 0) {
                    maxVal = data[i];
                    found = true;
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (!nulls.get(i)) {
                    if (!found || Float.compare(data[i], maxVal) > 0) {
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
