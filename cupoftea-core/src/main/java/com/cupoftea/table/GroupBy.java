package com.cupoftea.table;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.exceptions.SchemaMismatchException;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.ParallelVectorEngine;

/**
 * Enterprise columnar GroupBy engine.
 * Uses an open-addressing hash table and single-pass aggregation to avoid key/row allocations
 * and repeated memory traversals.
 */
public class GroupBy {

    private final List<String> groupColumns;
    private final List<Column> sourceHeaders;
    private final Map<String, ColumnVector> columnVectors;
    private final GroupPartitionState partitionState;
    private final MutationMode mutationMode;
    private final boolean parallel;
    private final ExecutorService executor;
    private final int parallelThreshold;

    public GroupBy(CupOfTea source, String... columns) {
        if (source == null) {
            throw new IllegalArgumentException("Source DataFrame cannot be null");
        }
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("At least one grouping column must be specified");
        }
        IDataFrame df = source.getDataFrame();
        this.mutationMode = source.getMutationMode();
        this.parallel = source.isParallel();
        this.executor = source.getExecutor();
        this.parallelThreshold = source.getParallelThreshold();

        this.sourceHeaders = new ArrayList<>();
        if (source.getHeaders() != null) {
            for (Column col : source.getHeaders()) {
                this.sourceHeaders.add(new Column(col.columnName(), col.columnType(), col.nullable()));
            }
        }
        this.groupColumns = Arrays.asList(columns);

        for (String col : columns) {
            if (!hasColumn(col)) {
                throw new SchemaMismatchException("Column '" + col + "' does not exist in DataFrame");
            }
        }

        this.columnVectors = new LinkedHashMap<>(this.sourceHeaders.size());
        for (Column col : this.sourceHeaders) {
            ColumnVector vec = df.getColumnVector(col.columnName());
            if (vec != null) {
                this.columnVectors.put(col.columnName(), vec);
            }
        }

        int totalRows = source.size();
        ColumnVector[] keyVectors = new ColumnVector[columns.length];
        for (int i = 0; i < columns.length; i++) {
            keyVectors[i] = this.columnVectors.get(columns[i]);
        }

        if (totalRows == 0) {
            this.partitionState = new GroupPartitionState(
                0, new int[0], new int[0], 0, this.sourceHeaders, this.columnVectors.values().toArray(new ColumnVector[0])
            );
        } else if (this.parallel && totalRows >= this.parallelThreshold && totalRows > 1) {
            this.partitionState = buildParallelPartitions(keyVectors, totalRows);
        } else {
            this.partitionState = buildSequentialPartitions(keyVectors, totalRows);
        }
    }

    private GroupPartitionState buildSequentialPartitions(ColumnVector[] keyVectors, int totalRows) {
        GroupHashTable table = new GroupHashTable(keyVectors, totalRows);
        int[] rowGroups = new int[totalRows];
        for (int r = 0; r < totalRows; r++) {
            rowGroups[r] = table.findOrInsert(r);
        }
        return new GroupPartitionState(
            table.numGroups(),
            table.getFirstRowOfGroup(),
            rowGroups,
            totalRows,
            this.sourceHeaders,
            this.sourceHeaders.stream().map(c -> this.columnVectors.get(c.columnName())).toArray(ColumnVector[]::new)
        );
    }

    private static class ChunkPartition {
        final GroupHashTable table;
        final int[] rowGroups;
        final int start;
        final int end;

        ChunkPartition(GroupHashTable table, int[] rowGroups, int start, int end) {
            this.table = table;
            this.rowGroups = rowGroups;
            this.start = start;
            this.end = end;
        }
    }

    private GroupPartitionState buildParallelPartitions(ColumnVector[] keyVectors, int totalRows) {
        List<ChunkPartition> chunkPartitions = ParallelVectorEngine.executeChunks(
            totalRows, this.executor, (chunkIdx, start, end) -> {
                GroupHashTable localTable = new GroupHashTable(keyVectors, end - start);
                int[] chunkRowGroups = new int[end - start];
                for (int r = start; r < end; r++) {
                    chunkRowGroups[r - start] = localTable.findOrInsert(r);
                }
                return new ChunkPartition(localTable, chunkRowGroups, start, end);
            }
        );

        GroupHashTable globalTable = new GroupHashTable(keyVectors, totalRows);
        int[] globalRowGroups = new int[totalRows];

        for (ChunkPartition cp : chunkPartitions) {
            int localNumGroups = cp.table.numGroups();
            int[] localFirstRows = cp.table.getFirstRowOfGroup();
            int[] chunkToGlobal = new int[localNumGroups];
            for (int lg = 0; lg < localNumGroups; lg++) {
                chunkToGlobal[lg] = globalTable.findOrInsert(localFirstRows[lg]);
            }
            int start = cp.start;
            int len = cp.end - cp.start;
            for (int i = 0; i < len; i++) {
                globalRowGroups[start + i] = chunkToGlobal[cp.rowGroups[i]];
            }
        }

        return new GroupPartitionState(
            globalTable.numGroups(),
            globalTable.getFirstRowOfGroup(),
            globalRowGroups,
            totalRows,
            this.sourceHeaders,
            this.sourceHeaders.stream().map(c -> this.columnVectors.get(c.columnName())).toArray(ColumnVector[]::new)
        );
    }

    public CupOfTea agg(Aggregate... aggregates) {
        if (aggregates == null || aggregates.length == 0) {
            throw new IllegalArgumentException("At least one aggregate must be specified");
        }

        for (Aggregate agg : aggregates) {
            if (agg.getColumn() != null && !hasColumn(agg.getColumn())) {
                throw new SchemaMismatchException("Column '" + agg.getColumn() + "' not found in DataFrame");
            }
        }

        int numGroups = partitionState.getNumGroups();
        int totalRows = partitionState.getTotalRows();

        if (numGroups == 0) {
            DataFrame emptyDf = new DataFrame();
            if (this.parallel) {
                emptyDf.parallel(this.executor);
            }
            emptyDf.setParallelThreshold(this.parallelThreshold);

            ArrayList<Column> newHeaders = new ArrayList<>();
            for (String colName : groupColumns) {
                Column srcCol = findColumn(colName);
                newHeaders.add(new Column(srcCol.columnName(), srcCol.columnType(), srcCol.nullable()));
            }
            for (Aggregate agg : aggregates) {
                newHeaders.add(new Column(agg.getOutputColumnName(), agg.getResultType(this.sourceHeaders)));
            }
            emptyDf.setHeaders(newHeaders);
            return new CupOfTea(emptyDf, this.mutationMode);
        }

        int[] firstRowOfGroup = partitionState.getFirstRowOfGroup();
        List<ColumnVector> outputVectors = new ArrayList<>(groupColumns.size() + aggregates.length);

        // Populate key columns directly by index into typed ColumnVectors
        for (String colName : groupColumns) {
            ColumnVector srcVec = columnVectors.get(colName);
            ColumnVector outKeyVec = ColumnVector.create(colName, srcVec.getType(), Math.max(16, numGroups), srcVec.isNullable());
            outKeyVec.setSizeUnchecked(numGroups);
            outKeyVec.copyIndexedValuesOnly(srcVec, firstRowOfGroup, numGroups, 0);
            if (srcVec.hasNulls()) {
                outKeyVec.applyNullsFromIndexed(srcVec, firstRowOfGroup, numGroups, 0);
            }
            outputVectors.add(outKeyVec);
        }

        boolean allSinglePass = true;
        for (Aggregate agg : aggregates) {
            if (!agg.supportsSinglePass()) {
                allSinglePass = false;
                break;
            }
        }

        if (allSinglePass) {
            List<ColumnVector> aggVectors = computeSinglePassAggregates(aggregates, numGroups, totalRows);
            outputVectors.addAll(aggVectors);
        } else {
            List<ColumnVector> aggVectors = computeFallbackAggregates(aggregates, numGroups, totalRows);
            outputVectors.addAll(aggVectors);
        }

        DataFrame resultDf = DataFrame.fromVectors(outputVectors);
        if (this.parallel) {
            resultDf.parallel(this.executor);
        }
        resultDf.setParallelThreshold(this.parallelThreshold);
        return new CupOfTea(resultDf, this.mutationMode);
    }

    private List<ColumnVector> computeSinglePassAggregates(Aggregate[] aggregates, int numGroups, int totalRows) {
        int[] rowGroups = partitionState.getRowGroups();
        if (this.parallel && totalRows >= this.parallelThreshold && totalRows > 1) {
            List<GroupAccumulator[]> chunkAccsList = ParallelVectorEngine.executeChunks(
                totalRows, this.executor, (chunkIdx, start, end) -> {
                    GroupAccumulator[] localAccs = new GroupAccumulator[aggregates.length];
                    for (int a = 0; a < aggregates.length; a++) {
                        Aggregate agg = aggregates[a];
                        ColumnVector vec = agg.getColumn() != null ? columnVectors.get(agg.getColumn()) : null;
                        localAccs[a] = agg.createAccumulator(numGroups, vec, this.sourceHeaders);
                    }
                    for (int r = start; r < end; r++) {
                        int g = rowGroups[r];
                        for (int a = 0; a < localAccs.length; a++) {
                            localAccs[a].accumulate(g, r);
                        }
                    }
                    return localAccs;
                }
            );

            GroupAccumulator[] globalAccs = chunkAccsList.get(0);
            for (int c = 1; c < chunkAccsList.size(); c++) {
                GroupAccumulator[] other = chunkAccsList.get(c);
                for (int a = 0; a < globalAccs.length; a++) {
                    globalAccs[a].merge(other[a]);
                }
            }

            List<ColumnVector> result = new ArrayList<>(aggregates.length);
            for (int a = 0; a < aggregates.length; a++) {
                result.add(globalAccs[a].finish(numGroups));
            }
            return result;
        } else {
            GroupAccumulator[] accs = new GroupAccumulator[aggregates.length];
            for (int a = 0; a < aggregates.length; a++) {
                Aggregate agg = aggregates[a];
                ColumnVector vec = agg.getColumn() != null ? columnVectors.get(agg.getColumn()) : null;
                accs[a] = agg.createAccumulator(numGroups, vec, this.sourceHeaders);
            }

            for (int r = 0; r < totalRows; r++) {
                int g = rowGroups[r];
                for (int a = 0; a < accs.length; a++) {
                    accs[a].accumulate(g, r);
                }
            }

            List<ColumnVector> result = new ArrayList<>(aggregates.length);
            for (int a = 0; a < aggregates.length; a++) {
                result.add(accs[a].finish(numGroups));
            }
            return result;
        }
    }

    private List<ColumnVector> computeFallbackAggregates(Aggregate[] aggregates, int numGroups, int totalRows) {
        int[][] groupIndices = partitionState.getGroupIndices();
        int[] rowGroups = partitionState.getRowGroups();
        List<ColumnVector> result = new ArrayList<>(aggregates.length);

        for (Aggregate agg : aggregates) {
            ColumnVector srcVec = agg.getColumn() != null ? columnVectors.get(agg.getColumn()) : null;

            if (agg.supportsSinglePass()) {
                GroupAccumulator acc = agg.createAccumulator(numGroups, srcVec, this.sourceHeaders);
                for (int r = 0; r < totalRows; r++) {
                    acc.accumulate(rowGroups[r], r);
                }
                result.add(acc.finish(numGroups));
            } else if (agg.supportsVectorizedExecution()) {
                Class<?> resType = agg.getResultType(this.sourceHeaders);
                ColumnVector outVec = ColumnVector.create(agg.getOutputColumnName(), resType, Math.max(16, numGroups), true);
                outVec.setSizeUnchecked(numGroups);
                for (int g = 0; g < numGroups; g++) {
                    int[] indices = groupIndices[g];
                    Object val = agg.computeIndexedVector(srcVec, indices, indices.length, this.sourceHeaders);
                    outVec.setObject(g, val);
                }
                result.add(outVec);
            } else {
                Class<?> resType = agg.getResultType(this.sourceHeaders);
                ColumnVector outVec = ColumnVector.create(agg.getOutputColumnName(), resType, Math.max(16, numGroups), true);
                outVec.setSizeUnchecked(numGroups);
                for (int g = 0; g < numGroups; g++) {
                    Object val = agg.compute(partitionState.getGroupRowMaps(g), this.sourceHeaders);
                    outVec.setObject(g, val);
                }
                result.add(outVec);
            }
        }
        return result;
    }

    public CupOfTea sum(String column) {
        return agg(Aggregations.sum(column));
    }

    public CupOfTea count(String column) {
        return agg(Aggregations.count(column));
    }

    public CupOfTea count() {
        return agg(Aggregations.count());
    }

    public CupOfTea mean(String column) {
        return agg(Aggregations.mean(column));
    }

    public CupOfTea average(String column) {
        return agg(Aggregations.average(column));
    }

    public CupOfTea avg(String column) {
        return agg(Aggregations.avg(column));
    }

    public CupOfTea min(String column) {
        return agg(Aggregations.min(column));
    }

    public CupOfTea minimum(String column) {
        return agg(Aggregations.minimum(column));
    }

    public CupOfTea max(String column) {
        return agg(Aggregations.max(column));
    }

    public CupOfTea maximum(String column) {
        return agg(Aggregations.maximum(column));
    }

    public CupOfTea aggregate(String column, String separator) {
        return agg(Aggregations.aggregate(column, separator));
    }

    public CupOfTea listAgg(String column, String separator) {
        return agg(Aggregations.listAgg(column, separator));
    }

    public CupOfTea median(String column) {
        return agg(Aggregations.median(column));
    }

    public CupOfTea variance(String column) {
        return agg(Aggregations.variance(column));
    }

    public CupOfTea var(String column) {
        return agg(Aggregations.var(column));
    }

    public CupOfTea standardDeviation(String column) {
        return agg(Aggregations.standardDeviation(column));
    }

    public CupOfTea std(String column) {
        return agg(Aggregations.std(column));
    }

    public CupOfTea quantile(String column, double q) {
        return agg(Aggregations.quantile(column, q));
    }

    public CupOfTea skewness(String column) {
        return agg(Aggregations.skewness(column));
    }

    public CupOfTea kurtosis(String column) {
        return agg(Aggregations.kurtosis(column));
    }

    public CupOfTea agg(Aggregation aggregation, String column) {
        if (aggregation == null) {
            throw new IllegalArgumentException("Aggregation cannot be null");
        }
        return agg(aggregation.of(column));
    }

    public CupOfTea agg(Aggregation aggregation, String column, String alias) {
        if (aggregation == null) {
            throw new IllegalArgumentException("Aggregation cannot be null");
        }
        return agg(aggregation.of(column, alias));
    }

    public CupOfTea agg(Aggregation aggregation, String column, double quantile) {
        if (aggregation == null) {
            throw new IllegalArgumentException("Aggregation cannot be null");
        }
        return agg(aggregation.of(column, quantile));
    }

    public CupOfTea agg(Aggregation aggregation, String column, double quantile, String alias) {
        if (aggregation == null) {
            throw new IllegalArgumentException("Aggregation cannot be null");
        }
        return agg(aggregation.of(column, quantile, alias));
    }

    private boolean hasColumn(String colName) {
        return findColumn(colName) != null;
    }

    private Column findColumn(String colName) {
        if (colName == null || sourceHeaders == null) return null;
        for (Column col : sourceHeaders) {
            if (colName.equals(col.columnName())) {
                return col;
            }
        }
        return null;
    }
}
