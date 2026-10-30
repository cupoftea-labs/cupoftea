package com.cupoftea.lazy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.Predicate;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.CupOfTeaException;
import com.cupoftea.spi.ScanOptions;
import com.cupoftea.table.Aggregate;
import com.cupoftea.table.Aggregation;
import com.cupoftea.table.Column;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.Schema;
import com.cupoftea.utils.interfaces.IFilter;

/**
 * Fluent Lazy DataFrame evaluation node.
 * Evaluates execution graphs lazily over stream chunks/partitions for out-of-core data processing.
 */
public class LazyCupOfTea implements LazyDataFrame {

    private final LazySource source;
    private final List<IFilter> filters = new ArrayList<>();
    private List<String> selectColumns = null;
    private long limit = -1;
    private LazyGroupBy groupBy = null;
    private Aggregate[] aggregates = null;
    private List<String> orderColumns = null;

    private boolean parallel = true;
    private ExecutorService executor = null;
    private int batchSize = 65_536;
    private int chunkSizeBytes = 32 * 1024 * 1024;

    public LazyCupOfTea(LazySource source) {
        if (source == null) {
            throw new IllegalArgumentException("LazySource cannot be null");
        }
        this.source = source;
    }

    public LazyCupOfTea(LazySource source, ScanOptions options) {
        this(source);
        if (options != null) {
            this.parallel = options.isParallel();
            this.executor = options.getExecutor();
            if (options.getBatchSize() > 0) {
                this.batchSize = options.getBatchSize();
            }
            if (options.getChunkSizeBytes() > 0) {
                this.chunkSizeBytes = options.getChunkSizeBytes();
            }
        }
    }

    public static LazyCupOfTea fromSource(LazySource source) {
        return new LazyCupOfTea(source);
    }

    public static LazyCupOfTea fromDataFrame(CupOfTea df) {
        return new LazyCupOfTea(new InMemoryLazySource(df));
    }

    public LazySource getSource() {
        return source;
    }

    @Override
    public Schema getSchema() {
        return source.getSchema();
    }

    @Override
    public List<Column> getHeaders() {
        return source.getHeaders();
    }

    // --- Pipeline Operations ---

    @Override
    public LazyCupOfTea filter(IFilter filter) {
        if (filter != null) {
            this.filters.add(filter);
        }
        return this;
    }

    @Override
    public LazyCupOfTea filter(String column, Predicate<Object> predicate) {
        if (column == null || predicate == null) {
            throw new IllegalArgumentException("Column and predicate cannot be null");
        }
        return filter(row -> predicate.test(row.get(column)));
    }

    @Override
    public LazyCupOfTea select(String... columns) {
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("At least one column must be specified");
        }
        this.selectColumns = Arrays.asList(columns);
        return this;
    }

    public LazyCupOfTea select(List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("Columns list cannot be null or empty");
        }
        this.selectColumns = new ArrayList<>(columns);
        return this;
    }

    @Override
    public LazyCupOfTea drop(String... columns) {
        if (columns == null || columns.length == 0) {
            return this;
        }
        Set<String> dropSet = new HashSet<>(Arrays.asList(columns));
        List<Column> headers = getHeaders();
        List<String> remaining = new ArrayList<>();
        if (headers != null) {
            for (Column c : headers) {
                if (!dropSet.contains(c.columnName())) {
                    remaining.add(c.columnName());
                }
            }
        }
        this.selectColumns = remaining;
        return this;
    }

    @Override
    public LazyCupOfTea limit(long n) {
        if (n < 0) {
            throw new IllegalArgumentException("Limit must be non-negative");
        }
        this.limit = n;
        return this;
    }

    @Override
    public LazyGroupBy groupBy(String... columns) {
        return new LazyGroupBy(this, columns);
    }

    @Override
    public LazyCupOfTea agg(Aggregate... aggregates) {
        if (aggregates == null || aggregates.length == 0) {
            throw new IllegalArgumentException("At least one aggregate must be specified");
        }
        this.aggregates = aggregates.clone();
        return this;
    }

    @Override
    public LazyCupOfTea agg(Aggregation... aggregations) {
        if (aggregations == null || aggregations.length == 0) {
            throw new IllegalArgumentException("At least one aggregation must be specified");
        }
        List<Column> headers = getHeaders();
        String firstCol = (headers != null && !headers.isEmpty()) ? headers.get(0).columnName() : "0";
        Aggregate[] aggs = new Aggregate[aggregations.length];
        for (int i = 0; i < aggregations.length; i++) {
            aggs[i] = aggregations[i].of(firstCol);
        }
        return agg(aggs);
    }

    @Override
    public LazyCupOfTea orderBy(String... columns) {
        if (columns != null && columns.length > 0) {
            this.orderColumns = Arrays.asList(columns);
        }
        return this;
    }

    public LazyCupOfTea sort(String... columns) {
        return orderBy(columns);
    }

    LazyCupOfTea withGroupBy(LazyGroupBy groupBy) {
        this.groupBy = groupBy;
        return this;
    }

    // --- Configuration ---

    @Override
    public LazyCupOfTea parallel() {
        this.parallel = true;
        return this;
    }

    @Override
    public LazyCupOfTea parallel(ExecutorService executor) {
        this.parallel = true;
        this.executor = executor;
        return this;
    }

    @Override
    public LazyCupOfTea sequential() {
        this.parallel = false;
        return this;
    }

    @Override
    public LazyCupOfTea batchSize(int batchSize) {
        if (batchSize > 0) {
            this.batchSize = batchSize;
        }
        return this;
    }

    @Override
    public LazyCupOfTea chunkSize(int chunkSizeBytes) {
        if (chunkSizeBytes > 0) {
            this.chunkSizeBytes = chunkSizeBytes;
        }
        return this;
    }

    // --- Execution Plan & Count ---

    @Override
    public long count() {
        ScanContext context = new ScanContext(parallel, executor, batchSize, chunkSizeBytes);
        List<PartitionReader> partitions = source.getPartitions(context);
        if (partitions.isEmpty()) {
            return 0;
        }

        if (parallel && partitions.size() > 1) {
            ExecutorService exec = executor != null ? executor : ForkJoinPool.commonPool();
            List<Future<Long>> futures = new ArrayList<>(partitions.size());
            for (PartitionReader partition : partitions) {
                futures.add(exec.submit(() -> countPartition(partition)));
            }
            long total = 0;
            for (Future<Long> f : futures) {
                try {
                    total += f.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new CupOfTeaException("Failed counting partitions in parallel", e);
                }
            }
            return total;
        } else {
            long total = 0;
            for (PartitionReader partition : partitions) {
                total += countPartition(partition);
            }
            return total;
        }
    }

    private long countPartition(PartitionReader partition) {
        long count = 0;
        try (partition) {
            CupOfTea batch;
            while ((batch = partition.nextBatch()) != null) {
                for (IFilter filter : filters) {
                    batch = batch.filter(filter);
                    if (batch.size() == 0) break;
                }
                count += batch.size();
            }
        } catch (IOException e) {
            throw new CupOfTeaException("I/O error during lazy count scan", e);
        }
        return count;
    }

    @Override
    public String explain() {
        StringBuilder sb = new StringBuilder();
        sb.append("LazyExecutionPlan:\n");
        sb.append("  Source: ").append(source.describe()).append("\n");
        if (!filters.isEmpty()) {
            sb.append("  Filter: ").append(filters.size()).append(" predicate(s)\n");
        }
        if (selectColumns != null) {
            sb.append("  Select: ").append(selectColumns).append("\n");
        }
        if (groupBy != null) {
            sb.append("  GroupBy: ").append(Arrays.toString(groupBy.getColumns())).append("\n");
            if (groupBy.getAggregates() != null) {
                sb.append("  Aggregations: ").append(Arrays.toString(groupBy.getAggregates())).append("\n");
            }
        } else if (aggregates != null) {
            sb.append("  Aggregations (Global): ").append(Arrays.toString(aggregates)).append("\n");
        }
        if (limit >= 0) {
            sb.append("  Limit: ").append(limit).append("\n");
        }
        if (orderColumns != null) {
            sb.append("  OrderBy: ").append(orderColumns).append("\n");
        }
        sb.append("  Execution Mode: ").append(parallel ? "Parallel" : "Sequential")
          .append(" (BatchSize=").append(batchSize).append(", ChunkSize=").append(chunkSizeBytes).append(")\n");
        return sb.toString();
    }

    // --- Materialization (collect) ---

    @Override
    public CupOfTea collect() {
        ScanContext context = new ScanContext(parallel, executor, batchSize, chunkSizeBytes);
        List<PartitionReader> partitions = source.getPartitions(context);

        boolean hasGroupBy = groupBy != null;
        boolean hasAgg = (hasGroupBy && groupBy.getAggregates() != null && groupBy.getAggregates().length > 0)
                || (aggregates != null && aggregates.length > 0);

        if (hasAgg) {
            return collectAggregates(partitions, context);
        } else {
            return collectRows(partitions);
        }
    }

    private CupOfTea collectAggregates(List<PartitionReader> partitions, ScanContext context) {
        String[] groupCols = groupBy != null ? groupBy.getColumns() : new String[0];
        Aggregate[] aggs = groupBy != null ? groupBy.getAggregates() : aggregates;
        List<Column> headers = source.getHeaders();

        if (partitions.isEmpty()) {
            CompactRunningGroupBy emptyState = new CompactRunningGroupBy(groupCols, aggs, headers);
            return emptyState.finish();
        }

        if (parallel && partitions.size() > 1) {
            ExecutorService exec = executor != null ? executor : ForkJoinPool.commonPool();
            List<Future<CompactRunningGroupBy>> futures = new ArrayList<>(partitions.size());

            for (PartitionReader partition : partitions) {
                futures.add(exec.submit(() -> {
                    CompactRunningGroupBy localState = new CompactRunningGroupBy(groupCols, aggs, headers);
                    try (partition) {
                        CupOfTea batch;
                        while ((batch = partition.nextBatch()) != null) {
                            for (IFilter filter : filters) {
                                batch = batch.filter(filter);
                                if (batch.size() == 0) break;
                            }
                            if (batch.size() > 0) {
                                localState.accumulateBatch(batch);
                            }
                        }
                    }
                    return localState;
                }));
            }

            CompactRunningGroupBy globalState = null;
            for (Future<CompactRunningGroupBy> future : futures) {
                try {
                    CompactRunningGroupBy state = future.get();
                    if (globalState == null) {
                        globalState = state;
                    } else {
                        globalState.merge(state);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    throw new CupOfTeaException("Parallel partition aggregation error", e);
                }
            }

            CupOfTea result = globalState != null ? globalState.finish() : new CupOfTea();
            if (orderColumns != null && !orderColumns.isEmpty()) {
                result = result.orderBy(orderColumns.toArray(new String[0]));
            }
            return result;
        } else {
            CompactRunningGroupBy state = new CompactRunningGroupBy(groupCols, aggs, headers);
            for (PartitionReader partition : partitions) {
                try (partition) {
                    CupOfTea batch;
                    while ((batch = partition.nextBatch()) != null) {
                        for (IFilter filter : filters) {
                            batch = batch.filter(filter);
                            if (batch.size() == 0) break;
                        }
                        if (batch.size() > 0) {
                            state.accumulateBatch(batch);
                        }
                    }
                } catch (IOException e) {
                    throw new CupOfTeaException("I/O error during streaming aggregation", e);
                }
            }

            CupOfTea result = state.finish();
            if (orderColumns != null && !orderColumns.isEmpty()) {
                result = result.orderBy(orderColumns.toArray(new String[0]));
            }
            return result;
        }
    }

    private CupOfTea collectRows(List<PartitionReader> partitions) {
        List<CupOfTea> collected = new ArrayList<>();
        long remaining = limit >= 0 ? limit : Long.MAX_VALUE;

        for (PartitionReader partition : partitions) {
            try (partition) {
                CupOfTea batch;
                while ((batch = partition.nextBatch()) != null && remaining > 0) {
                    for (IFilter filter : filters) {
                        batch = batch.filter(filter);
                        if (batch.size() == 0) break;
                    }
                    if (batch.size() > 0) {
                        if (selectColumns != null && !selectColumns.isEmpty()) {
                            batch = batch.select(selectColumns);
                        }
                        if (batch.size() > remaining) {
                            batch = batch.head((int) remaining);
                        }
                        remaining -= batch.size();
                        collected.add(batch);
                    }
                }
            } catch (IOException e) {
                throw new CupOfTeaException("I/O error during lazy collect", e);
            }
            if (remaining <= 0) break;
        }

        if (collected.isEmpty()) {
            Schema schema = source.getSchema();
            return schema != null ? new CupOfTea(schema) : new CupOfTea();
        }

        CupOfTea result = CupOfTea.concat(collected);
        if (orderColumns != null && !orderColumns.isEmpty()) {
            result = result.orderBy(orderColumns.toArray(new String[0]));
        }
        return result;
    }
}
