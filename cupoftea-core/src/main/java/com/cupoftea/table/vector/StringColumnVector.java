package com.cupoftea.table.vector;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

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

    public StringColumnVector(String name, String[] data, BitSet nulls, int size) {
        this(name, true, data, nulls, size);
    }

    public StringColumnVector(String name, boolean nullable, String[] data, BitSet nulls, int size) {
        super(name, String.class, nullable, data.length);
        this.data = data;
        if (nulls != null) {
            this.nulls.or(nulls);
        }
        this.size = size;
    }

    public String[] getRawData() {
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
    protected void setObjectValueOnly(int row, Object value) {
        if (value != null && !NA.isNA(value)) {
            data[row] = value.toString();
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
        if (hasNulls()) {
            for (int i = 0; i < len; i++) {
                if (nulls.get(from + i)) {
                    sliceNulls.set(i);
                }
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
        String[] newData = new String[retainedCount];
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
        return new StringColumnVector(name, nullable, newData, newNulls, retainedCount);
    }

    @Override
    public void filterInPlace(BitSet retainMask, int retainedCount) {
        String[] newData = new String[retainedCount];
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
        if (src instanceof StringColumnVector strSrc) {
            String[] srcData = strSrc.data;
            String[] dstData = this.data;
            for (int i = 0; i < count; i++) {
                dstData[dstOffset + i] = srcData[srcIndices[i]];
            }
        } else {
            super.copyIndexedValuesOnly(src, srcIndices, count, dstOffset);
        }
    }

    @Override
    public Object min(int from, int to) {
        checkSliceBounds(from, to);
        String best = null;
        boolean anyNulls = hasNulls();
        for (int i = from; i < to; i++) {
            if (!anyNulls || !nulls.get(i)) {
                String v = data[i];
                if (v != null && (best == null || v.compareTo(best) < 0)) {
                    best = v;
                }
            }
        }
        return best;
    }

    @Override
    public Object min(ExecutorService executor, int parallelThreshold) {
        if (size < parallelThreshold || size <= 1) {
            return min(0, size);
        }
        List<Object> partials = ParallelVectorEngine.executeChunks(
            size, executor, (chunkIdx, start, end) -> min(start, end)
        );
        String best = null;
        for (Object p : partials) {
            if (p != null) {
                String v = (String) p;
                if (best == null || v.compareTo(best) < 0) {
                    best = v;
                }
            }
        }
        return best;
    }

    @Override
    public Object max(int from, int to) {
        checkSliceBounds(from, to);
        String best = null;
        boolean anyNulls = hasNulls();
        for (int i = from; i < to; i++) {
            if (!anyNulls || !nulls.get(i)) {
                String v = data[i];
                if (v != null && (best == null || v.compareTo(best) > 0)) {
                    best = v;
                }
            }
        }
        return best;
    }

    @Override
    public Object max(ExecutorService executor, int parallelThreshold) {
        if (size < parallelThreshold || size <= 1) {
            return max(0, size);
        }
        List<Object> partials = ParallelVectorEngine.executeChunks(
            size, executor, (chunkIdx, start, end) -> max(start, end)
        );
        String best = null;
        for (Object p : partials) {
            if (p != null) {
                String v = (String) p;
                if (best == null || v.compareTo(best) > 0) {
                    best = v;
                }
            }
        }
        return best;
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

    // ==========================================
    // Vectorized String Operations
    // ==========================================

    public StringColumnVector toLowerCase() {
        return toLowerCase(false);
    }

    public StringColumnVector toLowerCaseInPlace() {
        return toLowerCase(true);
    }

    public StringColumnVector toLowerCase(boolean inPlace) {
        return applyTransform(inPlace, s -> s.toLowerCase(Locale.ROOT));
    }

    public StringColumnVector toUpperCase() {
        return toUpperCase(false);
    }

    public StringColumnVector toUpperCaseInPlace() {
        return toUpperCase(true);
    }

    public StringColumnVector toUpperCase(boolean inPlace) {
        return applyTransform(inPlace, s -> s.toUpperCase(Locale.ROOT));
    }

    public StringColumnVector capitalize() {
        return capitalize(false);
    }

    public StringColumnVector capitalizeInPlace() {
        return capitalize(true);
    }

    public StringColumnVector capitalize(boolean inPlace) {
        return applyTransform(inPlace, StringAccessor::capitalizeString);
    }

    public StringColumnVector trim() {
        return trim(false);
    }

    public StringColumnVector trimInPlace() {
        return trim(true);
    }

    public StringColumnVector trim(boolean inPlace) {
        return applyTransform(inPlace, String::trim);
    }

    public StringColumnVector strip() {
        return strip(false);
    }

    public StringColumnVector stripInPlace() {
        return strip(true);
    }

    public StringColumnVector strip(boolean inPlace) {
        return applyTransform(inPlace, String::strip);
    }

    public StringColumnVector lstrip() {
        return lstrip(false);
    }

    public StringColumnVector lstripInPlace() {
        return lstrip(true);
    }

    public StringColumnVector lstrip(boolean inPlace) {
        return applyTransform(inPlace, String::stripLeading);
    }

    public StringColumnVector rstrip() {
        return rstrip(false);
    }

    public StringColumnVector rstripInPlace() {
        return rstrip(true);
    }

    public StringColumnVector rstrip(boolean inPlace) {
        return applyTransform(inPlace, String::stripTrailing);
    }

    public BooleanColumnVector contains(CharSequence sub) {
        Objects.requireNonNull(sub, "sub cannot be null");
        BooleanColumnVector res = new BooleanColumnVector(name, size, true);
        for (int i = 0; i < size; i++) {
            if (nulls.get(i)) {
                res.appendNull();
            } else {
                res.appendBoolean(data[i].contains(sub));
            }
        }
        return res;
    }

    public BooleanColumnVector startsWith(String prefix) {
        Objects.requireNonNull(prefix, "prefix cannot be null");
        BooleanColumnVector res = new BooleanColumnVector(name, size, true);
        for (int i = 0; i < size; i++) {
            if (nulls.get(i)) {
                res.appendNull();
            } else {
                res.appendBoolean(data[i].startsWith(prefix));
            }
        }
        return res;
    }

    public BooleanColumnVector endsWith(String suffix) {
        Objects.requireNonNull(suffix, "suffix cannot be null");
        BooleanColumnVector res = new BooleanColumnVector(name, size, true);
        for (int i = 0; i < size; i++) {
            if (nulls.get(i)) {
                res.appendNull();
            } else {
                res.appendBoolean(data[i].endsWith(suffix));
            }
        }
        return res;
    }

    public StringColumnVector replace(String target, String replacement) {
        return replace(target, replacement, false);
    }

    public StringColumnVector replaceInPlace(String target, String replacement) {
        return replace(target, replacement, true);
    }

    public StringColumnVector replace(String target, String replacement, boolean inPlace) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(replacement, "replacement cannot be null");
        return applyTransform(inPlace, s -> s.replace(target, replacement));
    }

    public StringColumnVector replaceAll(String regex, String replacement) {
        return replaceAll(regex, replacement, false);
    }

    public StringColumnVector replaceAllInPlace(String regex, String replacement) {
        return replaceAll(regex, replacement, true);
    }

    public StringColumnVector replaceAll(String regex, String replacement, boolean inPlace) {
        Objects.requireNonNull(regex, "regex cannot be null");
        Objects.requireNonNull(replacement, "replacement cannot be null");
        return applyTransform(inPlace, s -> s.replaceAll(regex, replacement));
    }

    public ObjectColumnVector split(String regex, int n) {
        Objects.requireNonNull(regex, "regex cannot be null");
        ObjectColumnVector res = new ObjectColumnVector(name, String[].class, size, true);
        for (int i = 0; i < size; i++) {
            if (nulls.get(i)) {
                res.appendNull();
            } else {
                res.appendObject(data[i].split(regex, n));
            }
        }
        return res;
    }

    public ObjectColumnVector split(String regex) {
        return split(regex, -1);
    }

    public StringColumnVector substring(int start, int end) {
        return substring(start, end, false);
    }

    public StringColumnVector substringInPlace(int start, int end) {
        return substring(start, end, true);
    }

    public StringColumnVector substring(int start, int end, boolean inPlace) {
        return applyTransform(inPlace, s -> StringAccessor.sliceString(s, start, end));
    }

    public StringColumnVector substring(int start) {
        return substring(start, false);
    }

    public StringColumnVector substringInPlace(int start) {
        return substring(start, true);
    }

    public StringColumnVector substring(int start, boolean inPlace) {
        return applyTransform(inPlace, s -> StringAccessor.sliceString(s, start));
    }

    public IntColumnVector length() {
        IntColumnVector res = new IntColumnVector(name, size, true);
        for (int i = 0; i < size; i++) {
            if (nulls.get(i)) {
                res.appendNull();
            } else {
                res.appendInt(data[i].length());
            }
        }
        return res;
    }

    public StringColumnVector padLeft(int width, char pad) {
        return padLeft(width, pad, false);
    }

    public StringColumnVector padLeftInPlace(int width, char pad) {
        return padLeft(width, pad, true);
    }

    public StringColumnVector padLeft(int width, char pad, boolean inPlace) {
        return applyTransform(inPlace, s -> StringAccessor.padLeftString(s, width, pad));
    }

    public StringColumnVector padRight(int width, char pad) {
        return padRight(width, pad, false);
    }

    public StringColumnVector padRightInPlace(int width, char pad) {
        return padRight(width, pad, true);
    }

    public StringColumnVector padRight(int width, char pad, boolean inPlace) {
        return applyTransform(inPlace, s -> StringAccessor.padRightString(s, width, pad));
    }

    private StringColumnVector applyTransform(boolean inPlace, StringAccessor.StringTransformer transformer) {
        if (inPlace) {
            for (int i = 0; i < size; i++) {
                if (!nulls.get(i)) {
                    data[i] = transformer.transform(data[i]);
                }
            }
            return this;
        } else {
            String[] copyData = new String[size];
            for (int i = 0; i < size; i++) {
                if (!nulls.get(i)) {
                    copyData[i] = transformer.transform(data[i]);
                }
            }
            BitSet copyNulls = new BitSet(size);
            copyNulls.or(nulls);
            return new StringColumnVector(name, nullable, copyData, copyNulls, size);
        }
    }
}
