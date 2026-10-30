package com.cupoftea.table;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;

import com.cupoftea.table.vector.ColumnVector;

public interface Aggregate {
    String getColumn();
    String getOutputColumnName();
    Aggregate as(String alias);
    Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders);
    Class<?> getResultType(List<Column> sourceHeaders);

    /**
     * Returns the high-level {@link Aggregation} type, or {@code null} if custom.
     */
    default Aggregation getAggregationType() {
        return null;
    }

    /**
     * Computes this aggregate directly over a {@link ColumnVector} (sequentially or in parallel).
     */
    default Object computeVector(
            ColumnVector vector,
             int totalRows,
            List<Column> sourceHeaders,
            boolean parallel,
            ExecutorService executor,
            int parallelThreshold) {
        return null;
    }

    /**
     * Computes this aggregate over a subset of row indices {@code rowIndices[0 .. count)} of a {@link ColumnVector}.
     */
    default Object computeIndexedVector(
            ColumnVector vector,
            int[] rowIndices,
            int count,
            List<Column> sourceHeaders) {
        return null;
    }

    /**
     * Indicates whether this aggregate supports direct columnar vector evaluation.
     */
    default boolean supportsVectorizedExecution() {
        return false;
    }

    /**
     * Indicates whether this aggregate supports single-pass accumulation across groups.
     */
    default boolean supportsSinglePass() {
        return false;
    }

    /**
     * Creates an accumulator for single-pass accumulation across {@code numGroups} groups.
     */
    default GroupAccumulator createAccumulator(int numGroups, ColumnVector vector, List<Column> sourceHeaders) {
        return null;
    }
}
