package com.cupoftea.table.vector;

import java.io.Serializable;
import java.util.Objects;

/**
 * A zero-copy contiguous view over a range {@code [offset, offset + length)} of a {@link ColumnVector}.
 * <p>
 * Partitioning a {@link ColumnVector} into {@code ColumnVectorSlice} instances does not copy the
 * underlying primitive arrays or null bitmaps, enabling cache-friendly parallel chunk evaluation.
 */
public final class ColumnVectorSlice implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ColumnVector vector;
    private final int offset;
    private final int length;

    public ColumnVectorSlice(ColumnVector vector, int offset, int length) {
        this.vector = Objects.requireNonNull(vector, "Underlying ColumnVector cannot be null");
        if (offset < 0 || length < 0 || offset + length > vector.size()) {
            throw new IndexOutOfBoundsException(
                "Invalid slice [offset=" + offset + ", length=" + length + "] for vector '"
                    + vector.getName() + "' of size " + vector.size()
            );
        }
        this.offset = offset;
        this.length = length;
    }

    public ColumnVector getVector() {
        return vector;
    }

    public String getName() {
        return vector.getName();
    }

    public Class<?> getType() {
        return vector.getType();
    }

    /**
     * Starting row index (inclusive) in the parent {@link ColumnVector}.
     */
    public int getOffset() {
        return offset;
    }

    /**
     * Ending row index (exclusive) in the parent {@link ColumnVector}.
     */
    public int getEnd() {
        return offset + length;
    }

    /**
     * Number of elements in this slice.
     */
    public int size() {
        return length;
    }

    public boolean isEmpty() {
        return length == 0;
    }

    private void checkLocalBounds(int localRow) {
        if (localRow < 0 || localRow >= length) {
            throw new IndexOutOfBoundsException(
                "Local index " + localRow + " out of bounds for slice of length " + length
            );
        }
    }

    public boolean isNull(int localRow) {
        checkLocalBounds(localRow);
        return vector.isNull(offset + localRow);
    }

    public int toGlobalIndex(int localRow) {
        checkLocalBounds(localRow);
        return offset + localRow;
    }

    public Object get(int localRow) {
        return getObject(localRow);
    }

    public Object getObject(int localRow) {
        checkLocalBounds(localRow);
        return vector.getObject(offset + localRow);
    }

    public int getInt(int localRow) {
        checkLocalBounds(localRow);
        return vector.getInt(offset + localRow);
    }

    public long getLong(int localRow) {
        checkLocalBounds(localRow);
        return vector.getLong(offset + localRow);
    }

    public double getDouble(int localRow) {
        checkLocalBounds(localRow);
        return vector.getDouble(offset + localRow);
    }

    public float getFloat(int localRow) {
        checkLocalBounds(localRow);
        return vector.getFloat(offset + localRow);
    }

    public boolean getBoolean(int localRow) {
        checkLocalBounds(localRow);
        return vector.getBoolean(offset + localRow);
    }

    public String getString(int localRow) {
        checkLocalBounds(localRow);
        return vector.getString(offset + localRow);
    }

    /**
     * Creates a zero-copy sub-slice of this slice for range {@code [localFrom, localTo)}.
     */
    public ColumnVectorSlice sliceView(int localFrom, int localTo) {
        if (localFrom < 0 || localTo > length || localFrom > localTo) {
            throw new IndexOutOfBoundsException(
                "Sub-slice range [" + localFrom + ", " + localTo + ") out of bounds for slice of length " + length
            );
        }
        return new ColumnVectorSlice(vector, offset + localFrom, localTo - localFrom);
    }

    /**
     * Creates a zero-copy sub-slice starting at {@code localOffset} with length {@code subLength}.
     */
    public ColumnVectorSlice subSlice(int localOffset, int subLength) {
        if (localOffset < 0 || subLength < 0 || localOffset + subLength > length) {
            throw new IndexOutOfBoundsException(
                "Sub-slice [offset=" + localOffset + ", length=" + subLength + "] out of bounds for slice of length " + length
            );
        }
        return new ColumnVectorSlice(vector, offset + localOffset, subLength);
    }

    /**
     * Materializes this zero-copy slice into an independent {@link ColumnVector} copy.
     */
    public ColumnVector materialize() {
        return vector.slice(offset, offset + length);
    }

    /**
     * Alias for {@link #materialize()}.
     */
    public ColumnVector toColumnVector() {
        return materialize();
    }

    /**
     * Counts non-null elements within this slice without copying memory.
     */
    public int countNonNull() {
        return vector.countNonNull(offset, offset + length);
    }

    /**
     * Computes the sum of non-null elements within this slice.
     */
    public Object sum() {
        return vector.sum(offset, offset + length);
    }

    /**
     * Computes the minimum of non-null elements within this slice.
     */
    public Object min() {
        return vector.min(offset, offset + length);
    }

    /**
     * Computes the maximum of non-null elements within this slice.
     */
    public Object max() {
        return vector.max(offset, offset + length);
    }

    /**
     * Computes the arithmetic mean of non-null elements within this slice.
     */
    public Object mean() {
        return vector.mean(offset, offset + length);
    }
}
