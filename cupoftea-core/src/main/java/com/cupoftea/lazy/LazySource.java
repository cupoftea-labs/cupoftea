package com.cupoftea.lazy;

import java.util.List;

import com.cupoftea.table.Column;
import com.cupoftea.table.Schema;

/**
 * Tabular data source capable of streaming partitions/chunks lazily.
 */
public interface LazySource {

    /**
     * Returns the schema for this data source.
     */
    Schema getSchema();

    /**
     * Returns the column headers for this data source.
     */
    List<Column> getHeaders();

    /**
     * Partitions the source into one or more {@link PartitionReader}s matching the given context.
     * Each partition can be read concurrently in parallel or sequentially.
     */
    List<PartitionReader> getPartitions(ScanContext context);

    /**
     * Returns an estimated total row count, or -1 if unknown until scanned.
     */
    default long estimatedSize() {
        return -1;
    }

    /**
     * Source description for execution plan visualization.
     */
    default String describe() {
        return getClass().getSimpleName();
    }
}
