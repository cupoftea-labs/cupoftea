package com.cupoftea.table.vector;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import com.cupoftea.table.NA;

/**
 * High-performance columnar vector for UTF-8 strings backed by an Arrow-compatible
 * contiguous byte buffer ({@code byte[] values}), an offset index array ({@code int[] offsets}),
 * and a {@code BitSet nulls} bitmap.
 *
 * This design eliminates the array of 100M heap string pointers ({@code String[] data}),
 * dramatically cuts GC overhead, minimizes L1/L2 cache misses, and enables zero-copy
 * byte slice comparisons and hash calculations.
 */
public class StringColumnVector extends ColumnVector implements Serializable {

    private static final long serialVersionUID = 2L;

    private static final int DEFAULT_VALUE_CAPACITY = 128;

    private static final VarHandle LONG_LE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    private static final long PRIME64_1 = 0x9E3779B185EBCA87L;
    private static final long PRIME64_2 = 0xC2B2AE3D27D4EB4FL;
    private static final long PRIME64_3 = 0x165667B19E3779F9L;

    /** Contiguous UTF-8 encoded string bytes back-to-back. */
    private byte[] values;

    /**
     * Offset index array tracking string slice boundaries.
     * {@code offsets[i]} points to the start byte in {@link #values} for row {@code i},
     * and {@code offsets[i + 1]} points to its end boundary (exclusive).
     * Array length is at least {@code size + 1}.
     */
    private int[] offsets;

    /** Total number of populated bytes in {@link #values}. */
    private int valuesCount;

    /**
     * Constructs a new empty {@link StringColumnVector}.
     *
     * @param name column name
     * @param initialCapacity initial row capacity
     * @param nullable whether column supports nulls
     */
    public StringColumnVector(String name, int initialCapacity, boolean nullable) {
        super(name, String.class, nullable, initialCapacity);
        int cap = Math.max(DEFAULT_INITIAL_CAPACITY, initialCapacity);
        this.offsets = new int[cap + 1];
        this.offsets[0] = 0;
        this.values = new byte[Math.max(DEFAULT_VALUE_CAPACITY, cap * 8)];
        this.valuesCount = 0;
    }

    /**
     * Constructs a {@link StringColumnVector} from an existing array of Strings.
     */
    public StringColumnVector(String name, String[] data, BitSet nulls, int size) {
        this(name, true, data, nulls, size);
    }

    /**
     * Constructs a {@link StringColumnVector} from an existing array of Strings with nullability flag.
     */
    public StringColumnVector(String name, boolean nullable, String[] data, BitSet nulls, int size) {
        super(name, String.class, nullable, Math.max(DEFAULT_INITIAL_CAPACITY, size));
        int initialCap = Math.max(DEFAULT_INITIAL_CAPACITY, size);
        this.offsets = new int[initialCap + 1];
        this.offsets[0] = 0;
        this.values = new byte[Math.max(DEFAULT_VALUE_CAPACITY, initialCap * 8)];
        this.valuesCount = 0;
        this.size = size;

        if (nulls != null) {
            this.nulls.or(nulls);
        }

        if (data != null) {
            for (int i = 0; i < size; i++) {
                if (this.nulls.get(i)) {
                    offsets[i + 1] = offsets[i];
                } else {
                    String str = data[i];
                    if (str == null) {
                        this.nulls.set(i);
                        offsets[i + 1] = offsets[i];
                    } else {
                        byte[] strBytes = str.getBytes(StandardCharsets.UTF_8);
                        ensureValueCapacity(valuesCount + strBytes.length);
                        System.arraycopy(strBytes, 0, values, valuesCount, strBytes.length);
                        valuesCount += strBytes.length;
                        offsets[i + 1] = valuesCount;
                    }
                }
            }
        }
    }

    /**
     * Constructs a {@link StringColumnVector} directly from contiguous UTF-8 byte buffer and offset array.
     *
     * @param name column name
     * @param nullable whether column supports nulls
     * @param values contiguous byte buffer containing UTF-8 strings
     * @param valuesCount number of valid bytes in {@code values}
     * @param offsets offset array of size at least {@code size + 1}
     * @param nulls bitset of null rows
     * @param size total number of rows
     */
    public StringColumnVector(String name, boolean nullable, byte[] values, int valuesCount,
                              int[] offsets, BitSet nulls, int size) {
        super(name, String.class, nullable, Math.max(DEFAULT_INITIAL_CAPACITY, size));
        this.values = Objects.requireNonNull(values, "values cannot be null");
        this.valuesCount = valuesCount;
        this.offsets = Objects.requireNonNull(offsets, "offsets cannot be null");
        if (nulls != null) {
            this.nulls.or(nulls);
        }
        this.size = size;
    }

    /**
     * Returns the contiguous byte buffer storing UTF-8 string bytes back-to-back.
     */
    public byte[] getValues() {
        return values;
    }

    /**
     * Returns the offset array tracking string slice boundaries.
     */
    public int[] getOffsets() {
        return offsets;
    }

    /**
     * Returns the total valid UTF-8 byte count stored in {@link #getValues()}.
     */
    public int getValuesCount() {
        return valuesCount;
    }

    /**
     * Returns an instantiated String array for backward compatibility.
     */
    public String[] getRawData() {
        return toStringArray();
    }

    private void ensureRowCapacity(int minCapacity) {
        if (minCapacity + 1 > offsets.length) {
            int newCap = Math.max(offsets.length * 2, minCapacity + 1);
            offsets = Arrays.copyOf(offsets, newCap);
        }
    }

    private void ensureValueCapacity(int minBytes) {
        if (minBytes > values.length) {
            int newCap = Math.max(values.length * 2, minBytes);
            values = Arrays.copyOf(values, newCap);
        }
    }

    @Override
    public void setSizeUnchecked(int newSize) {
        ensureRowCapacity(newSize);
        if (newSize > this.size) {
            int lastOffset = offsets[this.size];
            for (int i = this.size; i < newSize; i++) {
                nulls.set(i);
                offsets[i + 1] = lastOffset;
            }
        }
        this.size = newSize;
    }

    @Override
    public String getString(int row) {
        checkRowBounds(row);
        if (nulls.get(row)) {
            return null;
        }
        int start = offsets[row];
        int end = offsets[row + 1];
        int len = end - start;
        if (len <= 0) {
            return "";
        }
        return new String(values, start, len, StandardCharsets.UTF_8);
    }

    /**
     * Returns the byte slice length for the given row.
     */
    public int getByteLength(int row) {
        checkRowBounds(row);
        if (nulls.get(row)) {
            return 0;
        }
        return offsets[row + 1] - offsets[row];
    }

    /**
     * Returns the byte start offset in {@link #getValues()} for the given row.
     */
    public int getByteOffset(int row) {
        checkRowBounds(row);
        return offsets[row];
    }

    @Override
    public void setString(int row, String val) {
        checkRowBounds(row);
        if (val == null) {
            nulls.set(row);
            replaceRowBytes(row, null, 0, 0);
        } else {
            nulls.clear(row);
            byte[] bytes = val.getBytes(StandardCharsets.UTF_8);
            replaceRowBytes(row, bytes, 0, bytes.length);
        }
    }

    /**
     * Appends a UTF-8 byte slice directly without allocating a {@link String} instance.
     *
     * @param buf byte buffer containing UTF-8 bytes
     * @param start start index in {@code buf}
     * @param length length in bytes
     */
    public void appendUtf8Bytes(byte[] buf, int start, int length) {
        ensureRowCapacity(size + 1);
        if (buf == null) {
            nulls.set(size);
            offsets[size + 1] = valuesCount;
        } else {
            nulls.clear(size);
            if (length > 0) {
                ensureValueCapacity(valuesCount + length);
                System.arraycopy(buf, start, values, valuesCount, length);
                valuesCount += length;
            }
            offsets[size + 1] = valuesCount;
        }
        size++;
    }

    @Override
    public void appendString(String val) {
        ensureRowCapacity(size + 1);
        if (val == null) {
            nulls.set(size);
            offsets[size + 1] = valuesCount;
        } else {
            nulls.clear(size);
            byte[] strBytes = val.getBytes(StandardCharsets.UTF_8);
            if (strBytes.length > 0) {
                ensureValueCapacity(valuesCount + strBytes.length);
                System.arraycopy(strBytes, 0, values, valuesCount, strBytes.length);
                valuesCount += strBytes.length;
            }
            offsets[size + 1] = valuesCount;
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
            nulls.set(row);
            replaceRowBytes(row, null, 0, 0);
        } else {
            setString(row, value.toString());
        }
    }

    @Override
    protected void setObjectValueOnly(int row, Object value) {
        if (value != null && !NA.isNA(value)) {
            setString(row, value.toString());
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
        ensureRowCapacity(size + 1);
        nulls.set(size);
        offsets[size + 1] = valuesCount;
        size++;
    }

    private void replaceRowBytes(int row, byte[] newBytes, int start, int len) {
        int oldStart = offsets[row];
        int oldEnd = offsets[row + 1];
        int oldLen = oldEnd - oldStart;
        int delta = len - oldLen;

        if (delta != 0) {
            ensureValueCapacity(valuesCount + delta);
            int tailBytes = valuesCount - oldEnd;
            if (tailBytes > 0) {
                System.arraycopy(values, oldEnd, values, oldEnd + delta, tailBytes);
            }
            valuesCount += delta;
            for (int i = row + 1; i <= size; i++) {
                offsets[i] += delta;
            }
        }

        if (len > 0) {
            System.arraycopy(newBytes, start, values, oldStart, len);
        }
    }

    @Override
    public void remove(int row) {
        checkRowBounds(row);
        int rowStart = offsets[row];
        int rowEnd = offsets[row + 1];
        int rowLen = rowEnd - rowStart;

        // Shift bytes if row has content
        if (rowLen > 0) {
            int tailBytes = valuesCount - rowEnd;
            if (tailBytes > 0) {
                System.arraycopy(values, rowEnd, values, rowStart, tailBytes);
            }
            valuesCount -= rowLen;
        }

        // Shift offsets
        for (int i = row; i < size; i++) {
            offsets[i] = offsets[i + 1] - rowLen;
        }

        // Shift nulls bitset
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
        byte[] copyValues = Arrays.copyOf(values, valuesCount);
        int[] copyOffsets = Arrays.copyOf(offsets, size + 1);
        BitSet copyNulls = new BitSet(size);
        copyNulls.or(nulls);
        return new StringColumnVector(name, nullable, copyValues, valuesCount, copyOffsets, copyNulls, size);
    }

    @Override
    public ColumnVector slice(int from, int to) {
        checkSliceBounds(from, to);
        int len = to - from;
        if (len == 0) {
            return new StringColumnVector(name, 0, nullable);
        }

        int byteStart = offsets[from];
        int byteEnd = offsets[to];
        int byteLen = byteEnd - byteStart;

        byte[] sliceValues = new byte[byteLen];
        if (byteLen > 0) {
            System.arraycopy(values, byteStart, sliceValues, 0, byteLen);
        }

        int[] sliceOffsets = new int[len + 1];
        for (int i = 0; i <= len; i++) {
            sliceOffsets[i] = offsets[from + i] - byteStart;
        }

        BitSet sliceNulls = new BitSet(len);
        if (hasNulls()) {
            for (int i = 0; i < len; i++) {
                if (nulls.get(from + i)) {
                    sliceNulls.set(i);
                }
            }
        }

        return new StringColumnVector(name, nullable, sliceValues, byteLen, sliceOffsets, sliceNulls, len);
    }

    @Override
    public void fillna(Object value) {
        if (NA.isNA(value)) {
            return;
        }
        String fillVal = value.toString();
        byte[] fillBytes = fillVal.getBytes(StandardCharsets.UTF_8);
        for (int i = nulls.nextSetBit(0); i >= 0 && i < size; i = nulls.nextSetBit(i + 1)) {
            nulls.clear(i);
            replaceRowBytes(i, fillBytes, 0, fillBytes.length);
        }
    }

    @Override
    public void permute(int[] order) {
        // Calculate total byte size required
        int newTotalBytes = 0;
        for (int i = 0; i < size; i++) {
            int src = order[i];
            if (!nulls.get(src)) {
                newTotalBytes += (offsets[src + 1] - offsets[src]);
            }
        }

        byte[] newValues = new byte[newTotalBytes];
        int[] newOffsets = new int[size + 1];
        BitSet newNulls = new BitSet(size);
        int currByte = 0;
        newOffsets[0] = 0;

        for (int i = 0; i < size; i++) {
            int src = order[i];
            if (nulls.get(src)) {
                newNulls.set(i);
                newOffsets[i + 1] = currByte;
            } else {
                int srcStart = offsets[src];
                int srcLen = offsets[src + 1] - srcStart;
                if (srcLen > 0) {
                    System.arraycopy(values, srcStart, newValues, currByte, srcLen);
                    currByte += srcLen;
                }
                newOffsets[i + 1] = currByte;
            }
        }

        this.values = newValues;
        this.valuesCount = currByte;
        this.offsets = newOffsets;
        this.nulls.clear();
        this.nulls.or(newNulls);
    }

    @Override
    public ColumnVector filter(BitSet retainMask, int retainedCount) {
        int newTotalBytes = 0;
        for (int i = retainMask.nextSetBit(0); i >= 0 && i < size; i = retainMask.nextSetBit(i + 1)) {
            if (!nulls.get(i)) {
                newTotalBytes += (offsets[i + 1] - offsets[i]);
            }
        }

        byte[] newValues = new byte[newTotalBytes];
        int[] newOffsets = new int[retainedCount + 1];
        BitSet newNulls = new BitSet(retainedCount);
        int currByte = 0;
        newOffsets[0] = 0;
        int dst = 0;

        for (int i = retainMask.nextSetBit(0); i >= 0 && i < size; i = retainMask.nextSetBit(i + 1)) {
            if (nulls.get(i)) {
                newNulls.set(dst);
                newOffsets[dst + 1] = currByte;
            } else {
                int srcStart = offsets[i];
                int srcLen = offsets[i + 1] - srcStart;
                if (srcLen > 0) {
                    System.arraycopy(values, srcStart, newValues, currByte, srcLen);
                    currByte += srcLen;
                }
                newOffsets[dst + 1] = currByte;
            }
            dst++;
        }

        return new StringColumnVector(name, nullable, newValues, currByte, newOffsets, newNulls, retainedCount);
    }

    @Override
    public void filterInPlace(BitSet retainMask, int retainedCount) {
        StringColumnVector filtered = (StringColumnVector) filter(retainMask, retainedCount);
        this.values = filtered.values;
        this.valuesCount = filtered.valuesCount;
        this.offsets = filtered.offsets;
        this.nulls.clear();
        this.nulls.or(filtered.nulls);
        this.size = retainedCount;
    }

    @Override
    public synchronized void copyIndexedValuesOnly(ColumnVector src, int[] srcIndices, int count, int dstOffset) {
        if (src instanceof StringColumnVector strSrc) {
            if (dstOffset == 0 && count == this.size && this.valuesCount == 0) {
                int totalBytes = 0;
                for (int i = 0; i < count; i++) {
                    int srcRow = srcIndices[i];
                    if (!strSrc.nulls.get(srcRow)) {
                        totalBytes += (strSrc.offsets[srcRow + 1] - strSrc.offsets[srcRow]);
                    }
                }
                ensureValueCapacity(totalBytes);
                ensureRowCapacity(count);
                int currByte = 0;
                this.offsets[0] = 0;
                for (int i = 0; i < count; i++) {
                    int srcRow = srcIndices[i];
                    if (strSrc.nulls.get(srcRow)) {
                        this.nulls.set(i);
                    } else {
                        this.nulls.clear(i);
                        int sStart = strSrc.offsets[srcRow];
                        int sLen = strSrc.offsets[srcRow + 1] - sStart;
                        if (sLen > 0) {
                            System.arraycopy(strSrc.values, sStart, this.values, currByte, sLen);
                            currByte += sLen;
                        }
                    }
                    this.offsets[i + 1] = currByte;
                }
                this.valuesCount = currByte;
                return;
            }
            for (int i = 0; i < count; i++) {
                int srcRow = srcIndices[i];
                int dstRow = dstOffset + i;
                if (strSrc.nulls.get(srcRow)) {
                    this.nulls.set(dstRow);
                    replaceRowBytes(dstRow, null, 0, 0);
                } else {
                    this.nulls.clear(dstRow);
                    int sStart = strSrc.offsets[srcRow];
                    int sLen = strSrc.offsets[srcRow + 1] - sStart;
                    replaceRowBytes(dstRow, strSrc.values, sStart, sLen);
                }
            }
        } else {
            super.copyIndexedValuesOnly(src, srcIndices, count, dstOffset);
        }
    }

    // ==========================================
    // Zero-Copy Byte Slice Comparisons & Hash
    // ==========================================

    /**
     * Computes a 32-bit hash over the row's UTF-8 byte slice using 64-bit SWAR block hashing
     * without String instantiation.
     */
    public int hashRow(int row) {
        if (nulls.get(row)) {
            return 0;
        }
        int start = offsets[row];
        int len = offsets[row + 1] - start;
        if (len <= 0) {
            return 0;
        }
        return hashBytes(values, start, len);
    }

    /**
     * Computes a 32-bit hash over a UTF-8 byte slice {@code [start, start + len)} using 64-bit
     * block reads, primitive multiplications/rotations, and a bit-masked tail finalizer.
     *
     * @param buf byte buffer containing the slice
     * @param start start offset in {@code buf}
     * @param len length in bytes
     * @return 32-bit mixed hash code, or {@code 0} if {@code buf == null} or {@code len <= 0}
     */
    public static int hashBytes(byte[] buf, int start, int len) {
        if (buf == null || len <= 0) {
            return 0;
        }
        int end = start + len;
        long h = (len & 0xFFFFFFFFL) * PRIME64_1;
        int i = start;
        for (int limit = end - 9; i <= limit; i += 8) {
            h = Long.rotateLeft((h ^ (long) LONG_LE.get(buf, i)) * PRIME64_1, 31);
        }
        int shift = (8 - (end - i)) << 3;
        long tail;
        if (i + 8 <= buf.length) {
            tail = ((long) LONG_LE.get(buf, i)) & (~0L >>> shift);
        } else if (end >= 8) {
            tail = ((long) LONG_LE.get(buf, end - 8)) >>> shift;
        } else {
            tail = 0L;
            for (int k = end - 1; k >= i; k--) {
                tail = (tail << 8) | (buf[k] & 0xFFL);
            }
        }
        h = (h ^ tail) * PRIME64_2;
        h ^= h >>> 33;
        h *= PRIME64_3;
        return (int) (h ^ (h >>> 29));
    }

    /**
     * Computes the exact same 32-bit SWAR hash as {@link #hashBytes(byte[], int, int)} for slices
     * of length {@code 1..16} whose two little-endian 64-bit words have already been loaded into registers.
     */
    public static int hashShortWords(long p1, long p2, int len) {
        if (len <= 0) {
            return 0;
        }
        long h = (len & 0xFFFFFFFFL) * PRIME64_1;
        if (len <= 8) {
            h = (h ^ p1) * PRIME64_2;
        } else {
            h = Long.rotateLeft((h ^ p1) * PRIME64_1, 31);
            h = (h ^ p2) * PRIME64_2;
        }
        h ^= h >>> 33;
        h *= PRIME64_3;
        return (int) (h ^ (h >>> 29));
    }

    /**
     * Compares the byte slice of two rows for equality without String instantiation.
     */
    public boolean rowsEqual(int rowA, int rowB) {
        if (rowA == rowB) return true;
        boolean nullA = nulls.get(rowA);
        boolean nullB = nulls.get(rowB);
        if (nullA != nullB) return false;
        if (nullA) return true;

        int startA = offsets[rowA];
        int lenA = offsets[rowA + 1] - startA;
        int startB = offsets[rowB];
        int lenB = offsets[rowB + 1] - startB;

        if (lenA != lenB) return false;
        return Arrays.equals(values, startA, startA + lenA, values, startB, startB + lenB);
    }

    /**
     * Compares the byte slice of this row with a raw byte slice for equality.
     */
    public boolean rowEqualsBytes(int row, byte[] other, int otherStart, int otherLen) {
        if (nulls.get(row)) {
            return other == null;
        }
        if (other == null) return false;
        int start = offsets[row];
        int len = offsets[row + 1] - start;
        if (len != otherLen) return false;
        return Arrays.equals(values, start, start + len, other, otherStart, otherStart + otherLen);
    }

    /**
     * Performs lexicographical comparison of two row byte slices (matching UTF-8 order) without String instantiation.
     */
    @Override
    public int compareRows(int rowA, int rowB) {
        boolean nullA = nulls.get(rowA);
        boolean nullB = nulls.get(rowB);
        if (nullA && nullB) return 0;
        if (nullA) return 1;
        if (nullB) return -1;

        int startA = offsets[rowA];
        int endA = offsets[rowA + 1];
        int startB = offsets[rowB];
        int endB = offsets[rowB + 1];

        return Arrays.compare(values, startA, endA, values, startB, endB);
    }

    /**
     * Compares a row byte slice with a target String lexicographically.
     */
    public int compareRowToString(int row, String target) {
        if (nulls.get(row)) {
            return target == null ? 0 : 1;
        }
        if (target == null) return -1;
        byte[] targetBytes = target.getBytes(StandardCharsets.UTF_8);
        int start = offsets[row];
        int end = offsets[row + 1];
        return Arrays.compare(values, start, end, targetBytes, 0, targetBytes.length);
    }

    @Override
    public Object min(int from, int to) {
        checkSliceBounds(from, to);
        int bestRow = -1;
        boolean anyNulls = hasNulls();
        for (int i = from; i < to; i++) {
            if (!anyNulls || !nulls.get(i)) {
                if (bestRow == -1 || compareRows(i, bestRow) < 0) {
                    bestRow = i;
                }
            }
        }
        return bestRow != -1 ? getString(bestRow) : null;
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
        int bestRow = -1;
        boolean anyNulls = hasNulls();
        for (int i = from; i < to; i++) {
            if (!anyNulls || !nulls.get(i)) {
                if (bestRow == -1 || compareRows(i, bestRow) > 0) {
                    bestRow = i;
                }
            }
        }
        return bestRow != -1 ? getString(bestRow) : null;
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
    public void clear() {
        this.size = 0;
        this.valuesCount = 0;
        this.offsets[0] = 0;
        this.nulls.clear();
    }

    public String[] toStringArray() {
        String[] arr = new String[size];
        for (int i = 0; i < size; i++) {
            arr[i] = getString(i);
        }
        return arr;
    }

    /**
     * Encodes this string vector into a {@link DictionaryColumnVector} backed by {@code int[]} codes.
     */
    @Override
    public DictionaryColumnVector toDictionary() {
        return DictionaryColumnVector.fromStringVector(this, false);
    }

    /**
     * Encodes this string vector into a {@link DictionaryColumnVector}, optionally using {@code short[]} codes.
     */
    @Override
    public DictionaryColumnVector toDictionary(boolean useShortCodes) {
        return DictionaryColumnVector.fromStringVector(this, useShortCodes);
    }

    /**
     * Alias for {@link #toDictionary()}.
     */
    @Override
    public DictionaryColumnVector toCategorical() {
        return toDictionary(false);
    }

    /**
     * Alias for {@link #toDictionary()}.
     */
    public DictionaryColumnVector encodeDictionary() {
        return toDictionary(false);
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
                res.appendBoolean(getString(i).contains(sub));
            }
        }
        return res;
    }

    public BooleanColumnVector startsWith(String prefix) {
        Objects.requireNonNull(prefix, "prefix cannot be null");
        BooleanColumnVector res = new BooleanColumnVector(name, size, true);
        byte[] pBytes = prefix.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < size; i++) {
            if (nulls.get(i)) {
                res.appendNull();
            } else {
                int start = offsets[i];
                int len = offsets[i + 1] - start;
                if (len < pBytes.length) {
                    res.appendBoolean(false);
                } else {
                    res.appendBoolean(Arrays.equals(values, start, start + pBytes.length, pBytes, 0, pBytes.length));
                }
            }
        }
        return res;
    }

    public BooleanColumnVector endsWith(String suffix) {
        Objects.requireNonNull(suffix, "suffix cannot be null");
        BooleanColumnVector res = new BooleanColumnVector(name, size, true);
        byte[] sBytes = suffix.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < size; i++) {
            if (nulls.get(i)) {
                res.appendNull();
            } else {
                int start = offsets[i];
                int len = offsets[i + 1] - start;
                if (len < sBytes.length) {
                    res.appendBoolean(false);
                } else {
                    res.appendBoolean(Arrays.equals(values, start + len - sBytes.length, start + len, sBytes, 0, sBytes.length));
                }
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
                res.appendObject(getString(i).split(regex, n));
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
                res.appendInt(getString(i).length());
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
        StringColumnVector target = inPlace ? this : new StringColumnVector(name, size, nullable);
        if (inPlace) {
            for (int i = 0; i < size; i++) {
                if (!nulls.get(i)) {
                    String transformed = transformer.transform(getString(i));
                    setString(i, transformed);
                }
            }
            return this;
        } else {
            for (int i = 0; i < size; i++) {
                if (nulls.get(i)) {
                    target.appendNull();
                } else {
                    target.appendString(transformer.transform(getString(i)));
                }
            }
            return target;
        }
    }
}
