package com.cupoftea.lazy;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.Aggregate;
import com.cupoftea.table.Aggregation;
import com.cupoftea.table.Column;
import com.cupoftea.table.Schema;
import com.cupoftea.utils.interfaces.IFilter;

/**
 * Enterprise lazy / streaming DataFrame evaluation interface for out-of-core data processing.
 * Operations are recorded in an execution plan and executed across streaming chunks/partitions
 * only when materialized via {@link #collect()}.
 */
public interface LazyDataFrame {

    /**
     * Filters rows using an {@link IFilter} predicate during chunk processing.
     */
    LazyDataFrame filter(IFilter filter);

    /**
     * Filters rows by evaluating a predicate against the specified column.
     */
    LazyDataFrame filter(String column, Predicate<Object> predicate);

    /**
     * Projects only the specified columns, pruning unused columns early.
     */
    LazyDataFrame select(String... columns);

    /**
     * Drops the specified columns from the projection.
     */
    LazyDataFrame drop(String... columns);

    /**
     * Limits evaluation to at most {@code n} rows, stopping chunk reads early.
     */
    LazyDataFrame limit(long n);

    /**
     * Alias for {@link #limit(long)}.
     */
    default LazyDataFrame head(long n) {
        return limit(n);
    }

    /**
     * Groups by the specified column(s) for streaming group aggregations.
     */
    LazyGroupBy groupBy(String... columns);

    /**
     * Aggregates the entire dataset across streaming chunks into a single-row DataFrame.
     */
    LazyDataFrame agg(Aggregate... aggregates);

    /**
     * Aggregates the entire dataset across streaming chunks into a single-row DataFrame.
     */
    LazyDataFrame agg(Aggregation... aggregations);

    /**
     * Orders the collected result DataFrame by the specified columns.
     */
    LazyDataFrame orderBy(String... columns);

    /**
     * Enables parallel execution across stream partitions.
     */
    LazyDataFrame parallel();

    /**
     * Enables parallel execution using the specified {@link ExecutorService}.
     */
    LazyDataFrame parallel(ExecutorService executor);

    /**
     * Forces sequential execution across stream partitions.
     */
    LazyDataFrame sequential();

    /**
     * Sets the batch size in rows for chunk processing.
     */
    LazyDataFrame batchSize(int batchSize);

    /**
     * Sets the chunk target size in bytes for partition slicing.
     */
    LazyDataFrame chunkSize(int chunkSizeBytes);

    /**
     * Executes the streaming plan out-of-core and materializes the aggregated/filtered result {@link CupOfTea} DataFrame.
     */
    CupOfTea collect();

    /**
     * Counts the total number of matching rows in the streaming dataset without materializing full rows into memory.
     */
    long count();

    /**
     * Returns the schema of the source dataset.
     */
    Schema getSchema();

    /**
     * Returns the column headers of the source dataset.
     */
    List<Column> getHeaders();

    /**
     * Returns a human-readable description of the lazy execution plan.
     */
    String explain();
}
