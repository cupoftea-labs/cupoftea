package com.cupoftea.lazy;

import java.io.Closeable;
import java.io.IOException;

import com.cupoftea.CupOfTea;

/**
 * Sequential reader for a single stream partition or chunk slice.
 */
public interface PartitionReader extends Closeable {

    /**
     * Reads the next batch of rows from this partition as a {@link CupOfTea} DataFrame.
     * Returns {@code null} when this partition has been completely exhausted.
     *
     * @return next batch DataFrame, or null if end of partition reached
     * @throws IOException if an I/O error occurs while reading
     */
    CupOfTea nextBatch() throws IOException;

    /**
     * Indicates whether this partition reader supports fused scan-aggregate streaming directly
     * into a {@link StreamingDirectAggregator} without constructing intermediate DataFrame batches.
     *
     * @return true if direct fused aggregation is supported
     */
    default boolean supportsDirectAggregation() {
        return false;
    }

    /**
     * Evaluates this partition directly into the provided {@link StreamingDirectAggregator}.
     * Default implementation falls back to reading batches via {@link #nextBatch()} and accumulating.
     *
     * @param aggregator target aggregator
     * @throws IOException if an I/O error occurs while reading
     */
    default void aggregateDirect(StreamingDirectAggregator aggregator) throws IOException {
        CupOfTea batch;
        while ((batch = nextBatch()) != null) {
            aggregator.accumulateBatch(batch);
        }
    }

    @Override
    default void close() throws IOException {
    }
}
