package com.cupoftea.table.vector;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.IntPredicate;

import com.cupoftea.table.Aggregations;
import com.cupoftea.table.Column;
import com.cupoftea.table.NA;

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

    public void setSizeUnchecked(int newSize) {
        this.size = newSize;
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

    public int getNullCount() {
        return nullCount();
    }

    public Object get(int row) {
        return getObject(row);
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

    /**
     * Creates a new filtered {@link ColumnVector} without mutating {@code this} or performing a redundant full copy.
     */
    public ColumnVector filter(BitSet retainMask, int retainedCount) {
        ColumnVector copy = this.copy();
        copy.filterInPlace(retainMask, retainedCount);
        return copy;
    }

    /**
     * Returns a zero-copy {@link ColumnVectorSlice} view over {@code [from, to)} without copying underlying arrays.
     */
    public ColumnVectorSlice sliceView(int from, int to) {
        checkSliceBounds(from, to);
        return new ColumnVectorSlice(this, from, to - from);
    }

    /**
     * Partitions this vector into {@code numChunks} contiguous zero-copy {@link ColumnVectorSlice} views.
     */
    public List<ColumnVectorSlice> partition(int numChunks) {
        if (numChunks <= 0) {
            throw new IllegalArgumentException("Number of chunks must be positive, got: " + numChunks);
        }
        if (size == 0) {
            return Collections.emptyList();
        }
        int[][] ranges = ParallelVectorEngine.partitionRanges(size, numChunks);
        List<ColumnVectorSlice> slices = new ArrayList<>(ranges.length);
        for (int[] range : ranges) {
            slices.add(new ColumnVectorSlice(this, range[0], range[1] - range[0]));
        }
        return slices;
    }

    /**
     * Copies values (without modifying the shared null BitSet) at {@code srcIndices[0 .. count)} from {@code src}
     * into {@code this} starting at {@code dstOffset}. Safe for concurrent invocation across disjoint {@code dstOffset} ranges.
     */
    public void copyIndexedValuesOnly(ColumnVector src, int[] srcIndices, int count, int dstOffset) {
        for (int i = 0; i < count; i++) {
            int srcIdx = srcIndices[i];
            if (!src.isNull(srcIdx)) {
                this.setObjectValueOnly(dstOffset + i, src.getObject(srcIdx));
            }
        }
    }

    protected void setObjectValueOnly(int row, Object value) {
        setObject(row, value);
    }

    /**
     * Copies null bit flags at {@code srcIndices[0 .. count)} from {@code src} into {@code this.nulls} starting at {@code dstOffset}.
     */
    public void applyNullsFromIndexed(ColumnVector src, int[] srcIndices, int count, int dstOffset) {
        if (!src.hasNulls()) {
            return;
        }
        BitSet srcNulls = src.nulls;
        for (int i = 0; i < count; i++) {
            if (srcNulls.get(srcIndices[i])) {
                this.nulls.set(dstOffset + i);
            }
        }
    }

    /**
     * Evaluates a row index predicate across this vector (sequentially or in parallel via prefix-sum scan)
     * and returns a compacted {@link ColumnVector}.
     */
    public ColumnVector filter(IntPredicate predicate, boolean parallel, ExecutorService executor, int parallelThreshold) {
        if (!parallel || size < parallelThreshold || size <= 1) {
            BitSet mask = new BitSet(size);
            int matched = 0;
            for (int i = 0; i < size; i++) {
                if (predicate.test(i)) {
                    mask.set(i);
                    matched++;
                }
            }
            return filter(mask, matched);
        }

        ExecutorService pool = ParallelVectorEngine.resolveExecutor(executor);
        int numChunks = ParallelVectorEngine.computeChunkCount(size, pool);
        int[][] ranges = ParallelVectorEngine.partitionRanges(size, numChunks);
        int[] chunkCounts = new int[ranges.length];

        List<int[]> chunkIndices = ParallelVectorEngine.executeRanges(ranges, pool, (chunkIdx, start, end) -> {
            int[] localMatches = new int[end - start];
            int count = 0;
            for (int r = start; r < end; r++) {
                if (predicate.test(r)) {
                    localMatches[count++] = r;
                }
            }
            chunkCounts[chunkIdx] = count;
            return localMatches;
        });

        int[] offsets = new int[ranges.length];
        int totalMatched = ParallelVectorEngine.computePrefixSums(chunkCounts, offsets);
        return filterFromChunks(chunkIndices, chunkCounts, offsets, totalMatched, ranges, pool);
    }

    /**
     * Consolidates per-chunk matching row indices into a single contiguous {@link ColumnVector} using prefix-sum offsets.
     */
    public ColumnVector filterFromChunks(
            List<int[]> chunkIndices,
            int[] chunkCounts,
            int[] offsets,
            int totalMatched,
            int[][] ranges,
            ExecutorService pool) {
        ColumnVector target = ColumnVector.create(this.name, this.type, Math.max(16, totalMatched), this.nullable);
        target.setSizeUnchecked(totalMatched);

        if (totalMatched == 0) {
            return target;
        }

        ParallelVectorEngine.executeRanges(ranges, pool, (chunkIdx, start, end) -> {
            int count = chunkCounts[chunkIdx];
            if (count > 0) {
                target.copyIndexedValuesOnly(this, chunkIndices.get(chunkIdx), count, offsets[chunkIdx]);
            }
            return null;
        });

        if (this.hasNulls()) {
            for (int c = 0; c < ranges.length; c++) {
                int count = chunkCounts[c];
                if (count > 0) {
                    target.applyNullsFromIndexed(this, chunkIndices.get(c), count, offsets[c]);
                }
            }
        }

        return target;
    }

    // --- Vectorized Map / Transformations ---

    /**
     * Transforms each element of this vector sequentially and returns a new {@link ColumnVector} of {@code targetType}.
     */
    public ColumnVector map(Function<Object, ?> mapper, Class<?> targetType) {
        Objects.requireNonNull(mapper, "Mapper function cannot be null");
        Class<?> resolvedType = targetType != null ? targetType : this.type;
        ColumnVector result = ColumnVector.create(this.name, resolvedType, Math.max(16, this.size), this.nullable);
        for (int i = 0; i < this.size; i++) {
            Object inVal = getObject(i);
            Object outVal = mapper.apply(inVal);
            result.appendObject(outVal);
        }
        return result;
    }

    /**
     * Transforms each element of this vector in parallel across chunks and returns a new {@link ColumnVector}.
     */
    public ColumnVector map(Function<Object, ?> mapper, Class<?> targetType, ExecutorService executor, int parallelThreshold) {
        Objects.requireNonNull(mapper, "Mapper function cannot be null");
        if (this.size < parallelThreshold || this.size <= 1) {
            return map(mapper, targetType);
        }

        Class<?> resolvedType = targetType != null ? targetType : this.type;
        Object[] mappedValues = new Object[this.size];
        ExecutorService pool = ParallelVectorEngine.resolveExecutor(executor);

        ParallelVectorEngine.executeChunks(this.size, pool, (chunkIdx, start, end) -> {
            for (int i = start; i < end; i++) {
                mappedValues[i] = mapper.apply(getObject(i));
            }
            return null;
        });

        ColumnVector result = ColumnVector.create(this.name, resolvedType, Math.max(16, this.size), this.nullable);
        for (int i = 0; i < this.size; i++) {
            result.appendObject(mappedValues[i]);
        }
        return result;
    }

    // --- Vectorized Aggregations (Sequential & Parallel Map-Reduce) ---

    public int countNonNull() {
        return size - nulls.cardinality();
    }

    public int countNonNull(int from, int to) {
        checkSliceBounds(from, to);
        int len = to - from;
        if (len == 0 || !hasNulls()) {
            return len;
        }
        int nullCnt = 0;
        for (int i = nulls.nextSetBit(from); i >= 0 && i < to; i = nulls.nextSetBit(i + 1)) {
            nullCnt++;
        }
        return len - nullCnt;
    }

    public int countNonNull(ExecutorService executor, int parallelThreshold) {
        if (size < parallelThreshold || size <= 1) {
            return countNonNull();
        }
        if (!hasNulls()) {
            return size;
        }
        List<Integer> counts = ParallelVectorEngine.executeChunks(
            size, executor, (chunkIdx, start, end) -> countNonNull(start, end)
        );
        int total = 0;
        for (int c : counts) {
            total += c;
        }
        return total;
    }

    public int countNonNullIndexed(int[] indices, int count) {
        if (count <= 0) {
            return 0;
        }
        if (!hasNulls()) {
            return count;
        }
        int nonNull = 0;
        for (int i = 0; i < count; i++) {
            if (!nulls.get(indices[i])) {
                nonNull++;
            }
        }
        return nonNull;
    }

    public Object sum(int from, int to) {
        checkSliceBounds(from, to);
        BigDecimal sum = BigDecimal.ZERO;
        boolean hasVal = false;
        for (int i = from; i < to; i++) {
            if (!isNull(i)) {
                BigDecimal bd = Aggregations.toBigDecimal(getObject(i));
                if (bd != null) {
                    sum = sum.add(bd);
                    hasVal = true;
                }
            }
        }
        if (!hasVal) {
            return null;
        }
        if (type == BigDecimal.class) {
            return sum;
        }
        if (type == Integer.class || type == int.class) {
            return sum.intValue();
        }
        if (type == Long.class || type == long.class) {
            return sum.longValue();
        }
        if (type == Float.class || type == float.class) {
            return sum.floatValue();
        }
        return sum.doubleValue();
    }

    public Object sum(boolean parallel, ExecutorService executor, int parallelThreshold) {
        return parallel ? sum(executor, parallelThreshold) : sum(0, size);
    }

    public Object sum(ExecutorService executor, int parallelThreshold) {
        if (size < parallelThreshold || size <= 1) {
            return sum(0, size);
        }
        List<Object> partialSums = ParallelVectorEngine.executeChunks(size, executor, (chunkIdx, start, end) -> {
            BigDecimal local = BigDecimal.ZERO;
            boolean hasVal = false;
            for (int i = start; i < end; i++) {
                if (!isNull(i)) {
                    BigDecimal bd = Aggregations.toBigDecimal(getObject(i));
                    if (bd != null) {
                        local = local.add(bd);
                        hasVal = true;
                    }
                }
            }
            return hasVal ? local : null;
        });

        BigDecimal total = BigDecimal.ZERO;
        boolean hasVal = false;
        for (Object p : partialSums) {
            if (p != null) {
                total = total.add((BigDecimal) p);
                hasVal = true;
            }
        }
        if (!hasVal) {
            return null;
        }
        if (type == BigDecimal.class) {
            return total;
        }
        if (type == Integer.class || type == int.class) {
            return total.intValue();
        }
        if (type == Long.class || type == long.class) {
            return total.longValue();
        }
        if (type == Float.class || type == float.class) {
            return total.floatValue();
        }
        return total.doubleValue();
    }

    public Object sumIndexed(int[] indices, int count) {
        if (count <= 0) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        boolean hasVal = false;
        for (int i = 0; i < count; i++) {
            int row = indices[i];
            if (!isNull(row)) {
                BigDecimal bd = Aggregations.toBigDecimal(getObject(row));
                if (bd != null) {
                    sum = sum.add(bd);
                    hasVal = true;
                }
            }
        }
        if (!hasVal) {
            return null;
        }
        if (type == BigDecimal.class) {
            return sum;
        }
        if (type == Integer.class || type == int.class) {
            return sum.intValue();
        }
        if (type == Long.class || type == long.class) {
            return sum.longValue();
        }
        if (type == Float.class || type == float.class) {
            return sum.floatValue();
        }
        return sum.doubleValue();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Object min(int from, int to) {
        checkSliceBounds(from, to);
        Object best = null;
        BigDecimal bestBd = null;
        for (int i = from; i < to; i++) {
            if (!isNull(i)) {
                Object val = getObject(i);
                if (val == null || NA.isNA(val)) {
                    continue;
                }
                if (best == null) {
                    best = val;
                    if (val instanceof Number) {
                        bestBd = Aggregations.toBigDecimal(val);
                    }
                } else if (val instanceof Number && best instanceof Number) {
                    BigDecimal vBd = Aggregations.toBigDecimal(val);
                    if (vBd != null && (bestBd == null || vBd.compareTo(bestBd) < 0)) {
                        bestBd = vBd;
                        best = val;
                    }
                } else if (val instanceof Comparable comp && best instanceof Comparable) {
                    if (comp.compareTo(best) < 0) {
                        best = val;
                    }
                }
            }
        }
        return best;
    }

    public Object min(boolean parallel, ExecutorService executor, int parallelThreshold) {
        return parallel ? min(executor, parallelThreshold) : min(0, size);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Object min(ExecutorService executor, int parallelThreshold) {
        if (size < parallelThreshold || size <= 1) {
            return min(0, size);
        }
        List<Object> partials = ParallelVectorEngine.executeChunks(
            size, executor, (chunkIdx, start, end) -> min(start, end)
        );
        Object best = null;
        BigDecimal bestBd = null;
        for (Object val : partials) {
            if (val == null) continue;
            if (best == null) {
                best = val;
                if (val instanceof Number) {
                    bestBd = Aggregations.toBigDecimal(val);
                }
            } else if (val instanceof Number && best instanceof Number) {
                BigDecimal vBd = Aggregations.toBigDecimal(val);
                if (vBd != null && (bestBd == null || vBd.compareTo(bestBd) < 0)) {
                    bestBd = vBd;
                    best = val;
                }
            } else if (val instanceof Comparable comp && best instanceof Comparable) {
                if (comp.compareTo(best) < 0) {
                    best = val;
                }
            }
        }
        return best;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Object minIndexed(int[] indices, int count) {
        if (count <= 0) {
            return null;
        }
        Object best = null;
        BigDecimal bestBd = null;
        for (int i = 0; i < count; i++) {
            int row = indices[i];
            if (!isNull(row)) {
                Object val = getObject(row);
                if (val == null || NA.isNA(val)) continue;
                if (best == null) {
                    best = val;
                    if (val instanceof Number) {
                        bestBd = Aggregations.toBigDecimal(val);
                    }
                } else if (val instanceof Number && best instanceof Number) {
                    BigDecimal vBd = Aggregations.toBigDecimal(val);
                    if (vBd != null && (bestBd == null || vBd.compareTo(bestBd) < 0)) {
                        bestBd = vBd;
                        best = val;
                    }
                } else if (val instanceof Comparable comp && best instanceof Comparable) {
                    if (comp.compareTo(best) < 0) {
                        best = val;
                    }
                }
            }
        }
        return best;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Object max(int from, int to) {
        checkSliceBounds(from, to);
        Object best = null;
        BigDecimal bestBd = null;
        for (int i = from; i < to; i++) {
            if (!isNull(i)) {
                Object val = getObject(i);
                if (val == null || NA.isNA(val)) {
                    continue;
                }
                if (best == null) {
                    best = val;
                    if (val instanceof Number) {
                        bestBd = Aggregations.toBigDecimal(val);
                    }
                } else if (val instanceof Number && best instanceof Number) {
                    BigDecimal vBd = Aggregations.toBigDecimal(val);
                    if (vBd != null && (bestBd == null || vBd.compareTo(bestBd) > 0)) {
                        bestBd = vBd;
                        best = val;
                    }
                } else if (val instanceof Comparable comp && best instanceof Comparable) {
                    if (comp.compareTo(best) > 0) {
                        best = val;
                    }
                }
            }
        }
        return best;
    }

    public Object max(boolean parallel, ExecutorService executor, int parallelThreshold) {
        return parallel ? max(executor, parallelThreshold) : max(0, size);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Object max(ExecutorService executor, int parallelThreshold) {
        if (size < parallelThreshold || size <= 1) {
            return max(0, size);
        }
        List<Object> partials = ParallelVectorEngine.executeChunks(
            size, executor, (chunkIdx, start, end) -> max(start, end)
        );
        Object best = null;
        BigDecimal bestBd = null;
        for (Object val : partials) {
            if (val == null) continue;
            if (best == null) {
                best = val;
                if (val instanceof Number) {
                    bestBd = Aggregations.toBigDecimal(val);
                }
            } else if (val instanceof Number && best instanceof Number) {
                BigDecimal vBd = Aggregations.toBigDecimal(val);
                if (vBd != null && (bestBd == null || vBd.compareTo(bestBd) > 0)) {
                    bestBd = vBd;
                    best = val;
                }
            } else if (val instanceof Comparable comp && best instanceof Comparable) {
                if (comp.compareTo(best) > 0) {
                    best = val;
                }
            }
        }
        return best;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Object maxIndexed(int[] indices, int count) {
        if (count <= 0) {
            return null;
        }
        Object best = null;
        BigDecimal bestBd = null;
        for (int i = 0; i < count; i++) {
            int row = indices[i];
            if (!isNull(row)) {
                Object val = getObject(row);
                if (val == null || NA.isNA(val)) continue;
                if (best == null) {
                    best = val;
                    if (val instanceof Number) {
                        bestBd = Aggregations.toBigDecimal(val);
                    }
                } else if (val instanceof Number && best instanceof Number) {
                    BigDecimal vBd = Aggregations.toBigDecimal(val);
                    if (vBd != null && (bestBd == null || vBd.compareTo(bestBd) > 0)) {
                        bestBd = vBd;
                        best = val;
                    }
                } else if (val instanceof Comparable comp && best instanceof Comparable) {
                    if (comp.compareTo(best) > 0) {
                        best = val;
                    }
                }
            }
        }
        return best;
    }

    public Object mean(int from, int to) {
        checkSliceBounds(from, to);
        BigDecimal sum = BigDecimal.ZERO;
        long count = 0;
        for (int i = from; i < to; i++) {
            if (!isNull(i)) {
                BigDecimal bd = Aggregations.toBigDecimal(getObject(i));
                if (bd != null) {
                    sum = sum.add(bd);
                    count++;
                }
            }
        }
        if (count == 0) {
            return null;
        }
        BigDecimal avg = sum.divide(BigDecimal.valueOf(count), MathContext.DECIMAL128);
        if (type == BigDecimal.class) {
            return avg;
        }
        return avg.doubleValue();
    }

    public Object mean(boolean parallel, ExecutorService executor, int parallelThreshold) {
        return parallel ? mean(executor, parallelThreshold) : mean(0, size);
    }

    public Object mean(ExecutorService executor, int parallelThreshold) {
        if (size < parallelThreshold || size <= 1) {
            return mean(0, size);
        }
        record PartialMean(BigDecimal sum, long count) {}
        List<PartialMean> partials = ParallelVectorEngine.executeChunks(size, executor, (chunkIdx, start, end) -> {
            BigDecimal localSum = BigDecimal.ZERO;
            long localCount = 0;
            for (int i = start; i < end; i++) {
                if (!isNull(i)) {
                    BigDecimal bd = Aggregations.toBigDecimal(getObject(i));
                    if (bd != null) {
                        localSum = localSum.add(bd);
                        localCount++;
                    }
                }
            }
            return new PartialMean(localSum, localCount);
        });

        BigDecimal totalSum = BigDecimal.ZERO;
        long totalCount = 0;
        for (PartialMean pm : partials) {
            if (pm.count() > 0) {
                totalSum = totalSum.add(pm.sum());
                totalCount += pm.count();
            }
        }
        if (totalCount == 0) {
            return null;
        }
        BigDecimal avg = totalSum.divide(BigDecimal.valueOf(totalCount), MathContext.DECIMAL128);
        if (type == BigDecimal.class) {
            return avg;
        }
        return avg.doubleValue();
    }

    public Object meanIndexed(int[] indices, int count) {
        if (count <= 0) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        long validCount = 0;
        for (int i = 0; i < count; i++) {
            int row = indices[i];
            if (!isNull(row)) {
                BigDecimal bd = Aggregations.toBigDecimal(getObject(row));
                if (bd != null) {
                    sum = sum.add(bd);
                    validCount++;
                }
            }
        }
        if (validCount == 0) {
            return null;
        }
        BigDecimal avg = sum.divide(BigDecimal.valueOf(validCount), MathContext.DECIMAL128);
        if (type == BigDecimal.class) {
            return avg;
        }
        return avg.doubleValue();
    }

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
