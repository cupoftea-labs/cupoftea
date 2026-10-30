package com.cupoftea.table;

import com.cupoftea.table.vector.ColumnVector;

/**
 * Accumulator for computing group aggregations in a single pass.
 */
public interface GroupAccumulator {

    /**
     * Accumulates row {@code row} from the source vector into group {@code groupId}.
     *
     * @param groupId the 0-based group ID
     * @param row     the row index in the source vector
     */
    void accumulate(int groupId, int row);

    /**
     * Merges another accumulator of the same type into this accumulator.
     * Used for parallel chunk reduction.
     *
     * @param other the other accumulator to merge
     */
    void merge(GroupAccumulator other);

    /**
     * Finishes accumulation and returns the typed output {@link ColumnVector} of size {@code numGroups}.
     *
     * @param numGroups total number of groups
     * @return the populated output vector
     */
    ColumnVector finish(int numGroups);
}
