package com.cupoftea.table.vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.IntBinaryOperator;

import com.cupoftea.exceptions.CupOfTeaException;

/**
 * Parallel execution engine for partitioning contiguous {@link ColumnVector} segments across worker threads,
 * performing parallel prefix-sum (scan) compaction, and executing map-reduce aggregations.
 */
public final class ParallelVectorEngine {

    /**
     * Default row count threshold below which operations stay sequential to avoid thread dispatch overhead.
     */
    public static final int DEFAULT_PARALLEL_THRESHOLD = 10_000;

    /**
     * Fixed block size (in elements) used for deterministic floating-point summation.
     * 8192 doubles = 64 KB (fits in L1 cache) and guarantees bit-for-bit identical summation
     * across sequential and parallel execution regardless of thread count.
     */
    public static final int DETERMINISTIC_FP_BLOCK_SIZE = 8192;

    private ParallelVectorEngine() {
    }

    /**
     * Resolves the effective {@link ExecutorService}, defaulting to {@link ForkJoinPool#commonPool()}.
     */
    public static ExecutorService resolveExecutor(ExecutorService customExecutor) {
        return customExecutor != null ? customExecutor : ForkJoinPool.commonPool();
    }

    /**
     * Determines the degree of parallelism for a given executor.
     */
    public static int getParallelism(ExecutorService executor) {
        if (executor instanceof ForkJoinPool fjp) {
            return Math.max(1, fjp.getParallelism());
        }
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Computes the number of contiguous chunks to partition {@code totalSize} rows into.
     */
    public static int computeChunkCount(int totalSize, ExecutorService executor) {
        if (totalSize <= 0) {
            return 0;
        }
        int parallelism = getParallelism(executor);
        return Math.min(totalSize, Math.max(1, parallelism));
    }

    /**
     * Partitions {@code [0, totalSize)} into {@code numChunks} contiguous {@code [start, end)} index pairs.
     */
    public static int[][] partitionRanges(int totalSize, int numChunks) {
        if (totalSize <= 0 || numChunks <= 0) {
            return new int[0][2];
        }
        int actualChunks = Math.min(totalSize, numChunks);
        int[][] ranges = new int[actualChunks][2];
        int baseSize = totalSize / actualChunks;
        int remainder = totalSize % actualChunks;
        int current = 0;
        for (int c = 0; c < actualChunks; c++) {
            int len = baseSize + (c < remainder ? 1 : 0);
            ranges[c][0] = current;
            ranges[c][1] = current + len;
            current += len;
        }
        return ranges;
    }

    /**
     * Computes exclusive prefix sums (parallel scan offsets) over chunk counts.
     *
     * @param chunkCounts count of items produced by each chunk
     * @param offsets     output array of length {@code chunkCounts.length} populated with starting offsets
     * @return total sum of all chunk counts
     */
    public static int computePrefixSums(int[] chunkCounts, int[] offsets) {
        int total = 0;
        for (int i = 0; i < chunkCounts.length; i++) {
            offsets[i] = total;
            total += chunkCounts[i];
        }
        return total;
    }

    /**
     * Computes exclusive prefix sums (parallel scan offsets) over chunk counts, returning an array
     * of length {@code chunkCounts.length + 1} where the last element is the total sum.
     */
    public static int[] computePrefixSums(int[] chunkCounts) {
        int[] offsets = new int[chunkCounts.length + 1];
        int total = 0;
        for (int i = 0; i < chunkCounts.length; i++) {
            offsets[i] = total;
            total += chunkCounts[i];
        }
        offsets[chunkCounts.length] = total;
        return offsets;
    }

    /**
     * Functional interface for a task operating on a chunk {@code (chunkIndex, startInclusive, endExclusive)}.
     */
    @FunctionalInterface
    public interface ChunkTask<T> {
        T execute(int chunkIndex, int start, int end);
    }

    /**
     * Executes chunk tasks across the provided {@link ExecutorService} and returns results in deterministic chunk order.
     */
    public static <T> List<T> executeChunks(int totalSize, ExecutorService executor, ChunkTask<T> task) {
        ExecutorService pool = resolveExecutor(executor);
        int numChunks = computeChunkCount(totalSize, pool);
        int[][] ranges = partitionRanges(totalSize, numChunks);
        return executeRanges(ranges, pool, task);
    }

    /**
     * Executes chunk tasks over explicit {@code [start, end)} ranges across the provided {@link ExecutorService}.
     */
    public static <T> List<T> executeRanges(int[][] ranges, ExecutorService executor, ChunkTask<T> task) {
        int numChunks = ranges.length;
        if (numChunks == 0) {
            return List.of();
        }
        if (numChunks == 1) {
            List<T> single = new ArrayList<>(1);
            single.add(task.execute(0, ranges[0][0], ranges[0][1]));
            return single;
        }

        ExecutorService pool = resolveExecutor(executor);
        List<Callable<T>> callables = new ArrayList<>(numChunks);
        for (int c = 0; c < numChunks; c++) {
            final int chunkIdx = c;
            final int start = ranges[c][0];
            final int end = ranges[c][1];
            callables.add(() -> task.execute(chunkIdx, start, end));
        }

        List<Future<T>> futures = new ArrayList<>(numChunks);
        for (Callable<T> callable : callables) {
            futures.add(pool.submit(callable));
        }

        List<T> results = new ArrayList<>(numChunks);
        Throwable firstError = null;
        for (Future<T> future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (firstError == null) {
                    firstError = new CupOfTeaException("Parallel execution interrupted", e);
                }
            } catch (ExecutionException e) {
                if (firstError == null) {
                    firstError = e.getCause() != null ? e.getCause() : e;
                }
            }
        }

        if (firstError != null) {
            if (firstError instanceof RuntimeException re) {
                throw re;
            }
            if (firstError instanceof Error err) {
                throw err;
            }
            throw new CupOfTeaException("Parallel execution failed", firstError);
        }

        return results;
    }
}
