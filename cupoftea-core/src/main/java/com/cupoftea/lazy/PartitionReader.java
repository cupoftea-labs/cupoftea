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

    @Override
    default void close() throws IOException {
    }
}
