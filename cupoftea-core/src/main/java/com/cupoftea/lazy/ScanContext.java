package com.cupoftea.lazy;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Context configuration passed to {@link LazySource} when requesting partitions.
 */
public class ScanContext {

    private final boolean parallel;
    private final ExecutorService executor;
    private final int batchSize;
    private final int chunkSizeBytes;
    private final List<String> projectedColumns;
    private final long projectedColumnsMask;

    public ScanContext(boolean parallel, ExecutorService executor, int batchSize, int chunkSizeBytes) {
        this(parallel, executor, batchSize, chunkSizeBytes, null, -1L);
    }

    public ScanContext(boolean parallel, ExecutorService executor, int batchSize, int chunkSizeBytes,
                       List<String> projectedColumns, long projectedColumnsMask) {
        this.parallel = parallel;
        this.executor = executor;
        this.batchSize = batchSize > 0 ? batchSize : 65_536;
        this.chunkSizeBytes = chunkSizeBytes > 0 ? chunkSizeBytes : 0;
        this.projectedColumns = projectedColumns != null ? Collections.unmodifiableList(projectedColumns) : null;
        this.projectedColumnsMask = projectedColumnsMask;
    }

    public boolean isParallel() {
        return parallel;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getChunkSizeBytes() {
        return chunkSizeBytes;
    }

    public List<String> getProjectedColumns() {
        return projectedColumns;
    }

    public long getProjectedColumnsMask() {
        return projectedColumnsMask;
    }

    public boolean isColumnProjected(int colIdx) {
        if (projectedColumnsMask == -1L) {
            return true;
        }
        if (colIdx >= 0 && colIdx < 64) {
            return (projectedColumnsMask & (1L << colIdx)) != 0L;
        }
        return true;
    }
}

