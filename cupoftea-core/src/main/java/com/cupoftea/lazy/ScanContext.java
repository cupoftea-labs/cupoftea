package com.cupoftea.lazy;

import java.util.concurrent.ExecutorService;

/**
 * Context configuration passed to {@link LazySource} when requesting partitions.
 */
public class ScanContext {

    private final boolean parallel;
    private final ExecutorService executor;
    private final int batchSize;
    private final int chunkSizeBytes;

    public ScanContext(boolean parallel, ExecutorService executor, int batchSize, int chunkSizeBytes) {
        this.parallel = parallel;
        this.executor = executor;
        this.batchSize = batchSize > 0 ? batchSize : 65_536;
        this.chunkSizeBytes = chunkSizeBytes > 0 ? chunkSizeBytes : 32 * 1024 * 1024;
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
}
