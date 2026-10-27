package com.cupoftea.table;

import static com.cupoftea.table.Aggregations.average;
import static com.cupoftea.table.Aggregations.count;
import static com.cupoftea.table.Aggregations.listAgg;
import static com.cupoftea.table.Aggregations.max;
import static com.cupoftea.table.Aggregations.mean;
import static com.cupoftea.table.Aggregations.min;
import static com.cupoftea.table.Aggregations.sum;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.exceptions.CupOfTeaException;
import com.cupoftea.exceptions.SchemaMismatchException;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.ColumnVectorSlice;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.ObjectColumnVector;
import com.cupoftea.table.vector.ParallelVectorEngine;
import com.cupoftea.table.vector.StringColumnVector;

public class ParallelDataFrameTest {

    private static CupOfTea buildLargeDataFrame(int rows) {
        int[] ids = new int[rows];
        long[] timestamps = new long[rows];
        double[] prices = new double[rows];
        float[] scores = new float[rows];
        boolean[] flags = new boolean[rows];
        String[] categories = new String[rows];
        BitSet priceNulls = new BitSet(rows);

        String[] catPool = {"ALPHA", "BETA", "GAMMA", "DELTA", "EPSILON"};
        for (int i = 0; i < rows; i++) {
            ids[i] = i;
            timestamps[i] = 1_700_000_000L + (i * 60L);
            if (i % 17 == 0) {
                priceNulls.set(i);
            } else {
                prices[i] = ((i * 31) % 10_000) * 0.25 + (i % 7) * 0.125;
            }
            scores[i] = (i % 100) * 0.5f;
            flags[i] = (i % 3 == 0);
            categories[i] = catPool[i % catPool.length];
        }

        List<ColumnVector> vectors = List.of(
            new IntColumnVector("id", ids, new BitSet(), rows),
            new LongColumnVector("ts", timestamps, new BitSet(), rows),
            new DoubleColumnVector("price", prices, priceNulls, rows),
            new FloatColumnVector("score", scores, new BitSet(), rows),
            new BooleanColumnVector("active", flags, new BitSet(), rows),
            new StringColumnVector("category", categories, new BitSet(), rows)
        );
        return CupOfTea.fromVectors(vectors);
    }

    @Test
    @DisplayName("Should toggle parallel and sequential modes in-place and via copy-on-write")
    void testParallelAndSequentialModeToggles() {
        CupOfTea df = buildLargeDataFrame(100);
        assertThat(df.isParallel()).isFalse();
        assertThat(df.getParallelThreshold()).isEqualTo(DataFrame.DEFAULT_PARALLEL_THRESHOLD);

        df.parallel();
        assertThat(df.isParallel()).isTrue();
        assertThat(df.getExecutor()).isSameAs(ForkJoinPool.commonPool());

        df.sequential();
        assertThat(df.isParallel()).isFalse();

        // Copy-on-write via asParallel / asSequential
        CupOfTea parallelCopy = df.asParallel().withParallelThreshold(50);
        assertThat(df.isParallel()).isFalse();
        assertThat(parallelCopy.isParallel()).isTrue();
        assertThat(parallelCopy.getParallelThreshold()).isEqualTo(50);

        CupOfTea seqCopy = parallelCopy.asSequential();
        assertThat(parallelCopy.isParallel()).isTrue();
        assertThat(seqCopy.isParallel()).isFalse();

        // MutationMode.COPY_ON_WRITE behavior on parallel() / sequential()
        CupOfTea cowBase = df.withMutationMode(MutationMode.COPY_ON_WRITE);
        CupOfTea cowParallel = cowBase.parallel();
        assertThat(cowBase.isParallel()).isFalse();
        assertThat(cowParallel.isParallel()).isTrue();

        CupOfTea cowSeq = cowParallel.sequential();
        assertThat(cowParallel.isParallel()).isTrue();
        assertThat(cowSeq.isParallel()).isFalse();
    }

    @Test
    @DisplayName("Should preserve parallel configuration across derived DataFrames")
    void testParallelConfigPropagatesAcrossDerivedFrames() {
        ForkJoinPool customPool = new ForkJoinPool(2);
        try {
            CupOfTea df = buildLargeDataFrame(200)
                .parallel(customPool)
                .withParallelThreshold(64);

            CupOfTea copy = df.copy();
            assertThat(copy.isParallel()).isTrue();
            assertThat(copy.getExecutor()).isSameAs(customPool);
            assertThat(copy.getParallelThreshold()).isEqualTo(64);

            CupOfTea sliced = df.slice(10, 100);
            assertThat(sliced.isParallel()).isTrue();
            assertThat(sliced.getExecutor()).isSameAs(customPool);
            assertThat(sliced.getParallelThreshold()).isEqualTo(64);

            CupOfTea filtered = df.filter(r -> r.getInt("id") < 120);
            assertThat(filtered.isParallel()).isTrue();
            assertThat(filtered.getExecutor()).isSameAs(customPool);
            assertThat(filtered.getParallelThreshold()).isEqualTo(64);
        } finally {
            customPool.shutdown();
        }
    }

    @Test
    @DisplayName("Should partition ColumnVector into zero-copy ColumnVectorSlices without copying memory")
    void testColumnVectorSliceZeroCopyAndPartitioning() {
        int size = 1_000;
        int[] values = new int[size];
        BitSet nulls = new BitSet(size);
        for (int i = 0; i < size; i++) {
            if (i % 10 == 0) {
                nulls.set(i);
            } else {
                values[i] = i * 2;
            }
        }
        IntColumnVector vector = new IntColumnVector("nums", values, nulls, size);

        List<ColumnVectorSlice> slices = vector.partition(4);
        assertThat(slices).hasSize(4);

        int totalLen = 0;
        long totalNonNull = 0;
        double sumFromSlices = 0.0;
        for (ColumnVectorSlice slice : slices) {
            assertThat(slice.getVector()).isSameAs(vector);
            assertThat(slice.getName()).isEqualTo("nums");
            assertThat(slice.getType()).isEqualTo(Integer.class);
            totalLen += slice.size();
            totalNonNull += slice.countNonNull();
            sumFromSlices += ((Number) slice.sum()).doubleValue();

            // Verify zero-copy: reading through slice matches parent vector at global index
            for (int local = 0; local < slice.size(); local++) {
                int global = slice.toGlobalIndex(local);
                assertThat(slice.isNull(local)).isEqualTo(vector.isNull(global));
                if (!slice.isNull(local)) {
                    assertThat(slice.getInt(local)).isEqualTo(vector.getInt(global));
                }
            }
        }

        assertThat(totalLen).isEqualTo(size);
        assertThat(totalNonNull).isEqualTo(vector.countNonNull());
        assertThat(sumFromSlices).isEqualTo(((Number) vector.sum()).doubleValue());

        // Sub-slice and materialization
        ColumnVectorSlice first = slices.get(0);
        ColumnVectorSlice sub = first.subSlice(10, 25);
        assertThat(sub.getOffset()).isEqualTo(first.getOffset() + 10);
        assertThat(sub.size()).isEqualTo(25);
        ColumnVector materialized = sub.toColumnVector();
        assertThat(materialized.size()).isEqualTo(25);
        for (int i = 0; i < 25; i++) {
            assertThat(materialized.get(i)).isEqualTo(vector.get(10 + i));
        }

        // Bounds checks
        assertThatThrownBy(() -> vector.sliceView(-1, 10))
            .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> vector.sliceView(995, 10))
            .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> sub.get(25))
            .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("Should stay sequential when dataset size is below parallelThreshold")
    void testParallelThresholdFallback() {
        AtomicInteger submittedTasks = new AtomicInteger(0);
        ExecutorService realPool = Executors.newFixedThreadPool(2);
        ExecutorService trackingExecutor = new ExecutorService() {
            @Override
            public void execute(Runnable command) {
                submittedTasks.incrementAndGet();
                realPool.execute(command);
            }
            @Override
            public void shutdown() { realPool.shutdown(); }
            @Override
            public List<Runnable> shutdownNow() { return realPool.shutdownNow(); }
            @Override
            public boolean isShutdown() { return realPool.isShutdown(); }
            @Override
            public boolean isTerminated() { return realPool.isTerminated(); }
            @Override
            public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
                return realPool.awaitTermination(timeout, unit);
            }
            @Override
            public <T> Future<T> submit(Callable<T> task) {
                submittedTasks.incrementAndGet();
                return realPool.submit(task);
            }
            @Override
            public <T> Future<T> submit(Runnable task, T result) {
                submittedTasks.incrementAndGet();
                return realPool.submit(task, result);
            }
            @Override
            public Future<?> submit(Runnable task) {
                submittedTasks.incrementAndGet();
                return realPool.submit(task);
            }
            @Override
            public <T> List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks) throws InterruptedException {
                submittedTasks.addAndGet(tasks.size());
                return realPool.invokeAll(tasks);
            }
            @Override
            public <T> List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks, long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
                submittedTasks.addAndGet(tasks.size());
                return realPool.invokeAll(tasks, timeout, unit);
            }
            @Override
            public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks) throws InterruptedException, java.util.concurrent.ExecutionException {
                submittedTasks.addAndGet(tasks.size());
                return realPool.invokeAny(tasks);
            }
            @Override
            public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks, long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException, java.util.concurrent.ExecutionException, java.util.concurrent.TimeoutException {
                submittedTasks.addAndGet(tasks.size());
                return realPool.invokeAny(tasks, timeout, unit);
            }
        };

        try {
            // 500 rows < default threshold (10,000) -> should not submit to executor
            CupOfTea df = buildLargeDataFrame(500).parallel(trackingExecutor);
            CupOfTea filtered = df.filter(r -> r.getInt("id") % 2 == 0);
            assertThat(filtered.size()).isEqualTo(250);
            assertThat(submittedTasks.get()).isEqualTo(0);

            // Lower threshold to 100 -> now 500 rows >= 100, should dispatch to trackingExecutor
            df.setParallelThreshold(100);
            CupOfTea filteredParallel = df.filter(r -> r.getInt("id") % 2 == 0);
            assertThat(filteredParallel.size()).isEqualTo(250);
            assertThat(submittedTasks.get()).isGreaterThan(0);
        } finally {
            realPool.shutdown();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8})
    @DisplayName("Should produce identical filter and removeRows results across thread pool sizes via prefix-sum scan")
    void testParallelFilterAndRemoveRowsDeterministicAcrossThreads(int threads) {
        CupOfTea base = buildLargeDataFrame(15_000);
        CupOfTea seqFiltered = base.asSequential().filter(
            row -> row.getBoolean("active") && row.get("price") != null && row.getDouble("price") > 500.0
        );

        ForkJoinPool pool = new ForkJoinPool(threads);
        try {
            CupOfTea parFiltered = base.asParallel(pool).withParallelThreshold(1_000).filter(
                row -> row.getBoolean("active") && row.get("price") != null && row.getDouble("price") > 500.0
            );

            assertThat(parFiltered.size()).isEqualTo(seqFiltered.size());
            for (String col : seqFiltered.getColumnNames()) {
                ColumnVector seqVec = seqFiltered.getColumnVector(col);
                ColumnVector parVec = parFiltered.getColumnVector(col);
                assertThat(parVec.size()).isEqualTo(seqVec.size());
                assertThat(parVec.getNullCount()).isEqualTo(seqVec.getNullCount());
                for (int i = 0; i < seqVec.size(); i++) {
                    assertThat(parVec.get(i)).isEqualTo(seqVec.get(i));
                }
            }

            // Also verify removeRows in parallel
            CupOfTea seqRemoved = base.copy().asSequential();
            seqRemoved.removeRows(row -> row.getInt("id") % 3 != 0);

            CupOfTea parRemoved = base.copy().asParallel(pool).withParallelThreshold(1_000);
            parRemoved.removeRows(row -> row.getInt("id") % 3 != 0);

            assertThat(parRemoved.size()).isEqualTo(seqRemoved.size());
            for (int i = 0; i < seqRemoved.size(); i++) {
                assertThat(parRemoved.getColumnVector("id").getInt(i))
                    .isEqualTo(seqRemoved.getColumnVector("id").getInt(i));
            }
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("Should handle edge-case filters in parallel (all match and none match)")
    void testParallelFilterEdgeCases() {
        CupOfTea df = buildLargeDataFrame(12_000).parallel().withParallelThreshold(1_000);

        CupOfTea noneMatch = df.filter(r -> false);
        assertThat(noneMatch.size()).isEqualTo(0);
        assertThat(noneMatch.getColumnNames()).containsExactlyElementsOf(df.getColumnNames());

        CupOfTea allMatch = df.filter(r -> true);
        assertThat(allMatch.size()).isEqualTo(df.size());
        for (int i = 0; i < df.size(); i++) {
            assertThat(allMatch.getColumnVector("id").getInt(i)).isEqualTo(i);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8})
    @DisplayName("Should execute parallel column projection and primitive vector maps identically to sequential")
    void testParallelColumnProjectionsAndVectorMaps(int threads) {
        CupOfTea seqDf = buildLargeDataFrame(12_000).asSequential();
        ForkJoinPool pool = new ForkJoinPool(threads);
        try {
            CupOfTea parDf = buildLargeDataFrame(12_000).asParallel(pool).withParallelThreshold(1_000);

            // 1. Primitive mapInt, mapLong, mapDouble
            seqDf.mapInt("id", v -> v * 3 + 1);
            parDf.mapInt("id", v -> v * 3 + 1);

            seqDf.mapLong("ts", v -> v + 10_000L);
            parDf.mapLong("ts", v -> v + 10_000L);

            seqDf.mapDouble("price", v -> v * 1.15);
            parDf.mapDouble("price", v -> v * 1.15);

            // 2. Object mapColumn
            seqDf.mapColumn("category", String.class, s -> s == null ? null : s.toLowerCase());
            parDf.mapColumn("category", String.class, s -> s == null ? null : s.toLowerCase());

            // 3. Row-based addColumn with IColumnCreator
            seqDf.addColumn(
                new Column("computed", Double.class),
                row -> row.get("price") == null ? null : row.getDouble("price") + row.getInt("id")
            );
            parDf.addColumn(
                new Column("computed", Double.class),
                row -> row.get("price") == null ? null : row.getDouble("price") + row.getInt("id")
            );

            // 4. alterColumnType in parallel
            seqDf.alterColumnType("id", Long.class);
            parDf.alterColumnType("id", Long.class);

            for (String col : seqDf.getColumnNames()) {
                ColumnVector sVec = seqDf.getColumnVector(col);
                ColumnVector pVec = parDf.getColumnVector(col);
                assertThat(pVec.getType()).isEqualTo(sVec.getType());
                assertThat(pVec.size()).isEqualTo(sVec.size());
                assertThat(pVec.getNullCount()).isEqualTo(sVec.getNullCount());
                for (int i = 0; i < sVec.size(); i++) {
                    assertThat(pVec.get(i)).isEqualTo(sVec.get(i));
                }
            }
        } finally {
            pool.shutdown();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8})
    @DisplayName("Should compute identical map-reduce aggregations across sequential and parallel modes")
    void testParallelAggregationsDeterministic(int threads) {
        CupOfTea seqDf = buildLargeDataFrame(20_000).asSequential();
        ForkJoinPool pool = new ForkJoinPool(threads);
        try {
            CupOfTea parDf = seqDf.copy().asParallel(pool).withParallelThreshold(1_000);

            // Whole-DataFrame scalar shortcuts
            assertThat(parDf.sumOf("id")).isEqualTo(seqDf.sumOf("id"));
            assertThat(parDf.sumOf("ts")).isEqualTo(seqDf.sumOf("ts"));
            assertThat(parDf.sumOf("price")).isEqualTo(seqDf.sumOf("price"));
            assertThat(parDf.sumOf("score")).isEqualTo(seqDf.sumOf("score"));

            assertThat(parDf.meanOf("id")).isEqualTo(seqDf.meanOf("id"));
            assertThat(parDf.meanOf("price")).isEqualTo(seqDf.meanOf("price"));
            assertThat(parDf.averageOf("score")).isEqualTo(seqDf.averageOf("score"));

            assertThat(parDf.minOf("id")).isEqualTo(seqDf.minOf("id"));
            assertThat(parDf.maxOf("id")).isEqualTo(seqDf.maxOf("id"));
            assertThat(parDf.minOf("price")).isEqualTo(seqDf.minOf("price"));
            assertThat(parDf.maxOf("price")).isEqualTo(seqDf.maxOf("price"));
            assertThat(parDf.minOf("category")).isEqualTo(seqDf.minOf("category"));
            assertThat(parDf.maxOf("category")).isEqualTo(seqDf.maxOf("category"));

            assertThat(parDf.countOf("price")).isEqualTo(seqDf.countOf("price"));
            assertThat(parDf.countOf("id")).isEqualTo(seqDf.countOf("id"));

            // Whole-DataFrame agg(Aggregate...)
            CupOfTea seqAgg = seqDf.agg(
                sum("id").as("sum_id"),
                sum("price").as("sum_price"),
                mean("price").as("mean_price"),
                min("score").as("min_score"),
                max("score").as("max_score"),
                count("price").as("cnt_price"),
                min("category").as("min_cat"),
                max("category").as("max_cat")
            );
            CupOfTea parAgg = parDf.agg(
                sum("id").as("sum_id"),
                sum("price").as("sum_price"),
                mean("price").as("mean_price"),
                min("score").as("min_score"),
                max("score").as("max_score"),
                count("price").as("cnt_price"),
                min("category").as("min_cat"),
                max("category").as("max_cat")
            );

            assertThat(parAgg.getRow(0)).isEqualTo(seqAgg.getRow(0));
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("Should support BigDecimal parallel aggregations on ObjectColumnVector")
    void testObjectColumnVectorBigDecimalParallelAggregations() {
        int size = 12_000;
        Object[] decimals = new Object[size];
        BitSet nulls = new BitSet(size);
        for (int i = 0; i < size; i++) {
            if (i % 25 == 0) {
                nulls.set(i);
            } else {
                decimals[i] = BigDecimal.valueOf(i * 10L + 5, 2);
            }
        }
        ObjectColumnVector vec = new ObjectColumnVector("amount", BigDecimal.class, decimals, nulls, size);

        BigDecimal seqSum = (BigDecimal) vec.sum(false, null, 1_000);
        BigDecimal parSum = (BigDecimal) vec.sum(true, ForkJoinPool.commonPool(), 1_000);
        assertThat(parSum).isEqualByComparingTo(seqSum);

        BigDecimal seqMean = (BigDecimal) vec.mean(false, null, 1_000);
        BigDecimal parMean = (BigDecimal) vec.mean(true, ForkJoinPool.commonPool(), 1_000);
        assertThat(parMean).isEqualByComparingTo(seqMean);

        BigDecimal seqMin = (BigDecimal) vec.min(false, null, 1_000);
        BigDecimal parMin = (BigDecimal) vec.min(true, ForkJoinPool.commonPool(), 1_000);
        assertThat(parMin).isEqualByComparingTo(seqMin);

        BigDecimal seqMax = (BigDecimal) vec.max(false, null, 1_000);
        BigDecimal parMax = (BigDecimal) vec.max(true, ForkJoinPool.commonPool(), 1_000);
        assertThat(parMax).isEqualByComparingTo(seqMax);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8})
    @DisplayName("Should execute parallel GroupBy.agg with deterministic group and row order")
    void testParallelGroupByAggregationsDeterministic(int threads) {
        CupOfTea seqDf = buildLargeDataFrame(25_000).asSequential();
        ForkJoinPool pool = new ForkJoinPool(threads);
        try {
            CupOfTea parDf = seqDf.copy().asParallel(pool).withParallelThreshold(1_000);

            CupOfTea seqGrouped = seqDf.groupBy("category", "active").agg(
                sum("id").as("id_sum"),
                sum("price").as("price_sum"),
                average("price").as("price_avg"),
                min("score").as("score_min"),
                max("score").as("score_max"),
                count("price").as("price_count")
            );

            CupOfTea parGrouped = parDf.groupBy("category", "active").agg(
                sum("id").as("id_sum"),
                sum("price").as("price_sum"),
                average("price").as("price_avg"),
                min("score").as("score_min"),
                max("score").as("score_max"),
                count("price").as("price_count")
            );

            assertThat(parGrouped.size()).isEqualTo(seqGrouped.size());
            for (int r = 0; r < seqGrouped.size(); r++) {
                assertThat(parGrouped.getRow(r)).isEqualTo(seqGrouped.getRow(r));
            }
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("Should preserve deterministic order in parallel GroupBy listAgg")
    void testParallelGroupByListAggOrder() {
        CupOfTea seqDf = buildLargeDataFrame(2_000).asSequential();
        CupOfTea parDf = seqDf.copy().asParallel().withParallelThreshold(200);

        CupOfTea seqRes = seqDf.groupBy("category").agg(listAgg("id", "|").as("ids"));
        CupOfTea parRes = parDf.groupBy("category").agg(listAgg("id", "|").as("ids"));

        assertThat(parRes.size()).isEqualTo(seqRes.size());
        for (int i = 0; i < seqRes.size(); i++) {
            assertThat(parRes.getRow(i)).isEqualTo(seqRes.getRow(i));
        }
    }

    @Test
    @DisplayName("Should be thread-safe under concurrent multi-threaded operations on a shared DataFrame")
    void testConcurrentOperationsThreadSafety() throws Exception {
        CupOfTea sharedDf = buildLargeDataFrame(15_000)
            .withMutationMode(MutationMode.COPY_ON_WRITE)
            .parallel()
            .withParallelThreshold(1_000);

        Double expectedPriceSum = (Double) sharedDf.sumOf("price");
        int expectedFilteredCount = sharedDf.filter(r -> r.getBoolean("active")).size();

        int numCallers = 8;
        ExecutorService callerPool = Executors.newFixedThreadPool(numCallers);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < numCallers; i++) {
                final int workerId = i;
                tasks.add(() -> {
                    for (int iter = 0; iter < 5; iter++) {
                        CupOfTea f = sharedDf.filter(r -> r.getBoolean("active"));
                        if (f.size() != expectedFilteredCount) {
                            return false;
                        }
                        Double s = (Double) sharedDf.sumOf("price");
                        if (!expectedPriceSum.equals(s)) {
                            return false;
                        }
                        CupOfTea mapped = sharedDf.mapInt("id", v -> v + workerId);
                        if (mapped.getColumnVector("id").getInt(10) != 10 + workerId) {
                            return false;
                        }
                        CupOfTea grouped = sharedDf.groupBy("category").agg(count("id").as("cnt"));
                        if (grouped.size() != 5) {
                            return false;
                        }
                    }
                    return true;
                });
            }

            List<Future<Boolean>> futures = callerPool.invokeAll(tasks);
            for (Future<Boolean> future : futures) {
                assertThat(future.get()).isTrue();
            }
            // Shared DataFrame must remain untouched
            assertThat(sharedDf.getColumnVector("id").getInt(10)).isEqualTo(10);
        } finally {
            callerPool.shutdown();
        }
    }

    @Test
    @DisplayName("Should validate ParallelVectorEngine prefix sums and exception unwrapping")
    void testParallelVectorEngineUtilities() {
        int[] counts = {3, 0, 5, 2};
        int[] offsets = ParallelVectorEngine.computePrefixSums(counts);
        assertThat(offsets).containsExactly(0, 3, 3, 8, 10);

        int[][] ranges = ParallelVectorEngine.partitionRanges(105, 4);
        assertThat(ranges).hasDimensions(4, 2);
        assertThat(ranges[0]).containsExactly(0, 27);
        assertThat(ranges[3][1]).isEqualTo(105);

        CupOfTea df = buildLargeDataFrame(5_000).parallel().withParallelThreshold(500);
        assertThatThrownBy(() -> df.sumOf("category"))
            .isInstanceOf(SchemaMismatchException.class);

        assertThatThrownBy(() -> df.filter(r -> {
            if (r.getInt("id") == 2500) {
                throw new CupOfTeaException("Boom in predicate");
            }
            return true;
        })).isInstanceOf(CupOfTeaException.class)
            .hasMessageContaining("Boom in predicate");
    }
}
