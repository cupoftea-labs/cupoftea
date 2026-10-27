package com.cupoftea.table;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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

public class GroupBy {

    private static final class IntList {
        int[] data;
        int size;

        IntList(int initialCapacity) {
            this.data = new int[Math.max(8, initialCapacity)];
            this.size = 0;
        }

        void add(int val) {
            if (size == data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            data[size++] = val;
        }

        void addAll(IntList other) {
            if (other.size == 0) return;
            if (size + other.size > data.length) {
                int newCap = Math.max(data.length * 2, size + other.size);
                data = Arrays.copyOf(data, newCap);
            }
            System.arraycopy(other.data, 0, data, size, other.size);
            size += other.size;
        }
    }

    private final List<String> groupColumns;
    private final List<Column> sourceHeaders;
    private final Map<String, ColumnVector> columnVectors;
    private final Map<List<Object>, IntList> partitions;
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
                this.columnVectors.put(col.columnName(), vec.copy());
            }
        }

        int totalRows = source.size();
        ColumnVector[] keyVectors = new ColumnVector[columns.length];
        for (int i = 0; i < columns.length; i++) {
            keyVectors[i] = this.columnVectors.get(columns[i]);
        }

        this.partitions = new LinkedHashMap<>();
        if (totalRows > 0) {
            if (this.parallel && totalRows >= this.parallelThreshold && totalRows > 1) {
                List<Map<List<Object>, IntList>> chunkMaps = ParallelVectorEngine.executeChunks(
                    totalRows, this.executor, (chunkIdx, start, end) -> {
                        Map<List<Object>, IntList> localPartitions = new LinkedHashMap<>();
                        for (int r = start; r < end; r++) {
                            List<Object> key = extractKey(keyVectors, r);
                            localPartitions.computeIfAbsent(key, k -> new IntList(16)).add(r);
                        }
                        return localPartitions;
                    }
                );
                for (Map<List<Object>, IntList> chunkMap : chunkMaps) {
                    for (Map.Entry<List<Object>, IntList> entry : chunkMap.entrySet()) {
                        this.partitions.computeIfAbsent(entry.getKey(), k -> new IntList(entry.getValue().size))
                                .addAll(entry.getValue());
                    }
                }
            } else {
                for (int r = 0; r < totalRows; r++) {
                    List<Object> key = extractKey(keyVectors, r);
                    this.partitions.computeIfAbsent(key, k -> new IntList(16)).add(r);
                }
            }
        }
    }

    private static List<Object> extractKey(ColumnVector[] keyVectors, int row) {
        if (keyVectors.length == 1) {
            Object val = keyVectors[0] != null ? keyVectors[0].getObject(row) : null;
            List<Object> single = new ArrayList<>(1);
            single.add(val);
            return single;
        }
        List<Object> key = new ArrayList<>(keyVectors.length);
        for (ColumnVector kv : keyVectors) {
            key.add(kv != null ? kv.getObject(row) : null);
        }
        return key;
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

        DataFrame resultDf = new DataFrame();
        if (this.parallel) {
            resultDf.parallel(this.executor);
        }
        resultDf.setParallelThreshold(this.parallelThreshold);

        ArrayList<Column> newHeaders = new ArrayList<>();
        for (String colName : groupColumns) {
            Column srcCol = findColumn(colName);
            newHeaders.add(new Column(srcCol.columnName(), srcCol.columnType(), srcCol.nullable()));
        }

        List<Column> srcHeaders = this.sourceHeaders;
        for (Aggregate agg : aggregates) {
            newHeaders.add(new Column(agg.getOutputColumnName(), agg.getResultType(srcHeaders)));
        }

        resultDf.setHeaders(newHeaders);

        int numGroups = partitions.size();
        if (numGroups == 0) {
            return new CupOfTea(resultDf, this.mutationMode);
        }

        List<Map.Entry<List<Object>, IntList>> entries = new ArrayList<>(partitions.entrySet());
        if (this.parallel && numGroups >= 32) {
            List<List<Map<String, Object>>> chunkRows = ParallelVectorEngine.executeChunks(
                numGroups, this.executor, (chunkIdx, start, end) -> {
                    List<Map<String, Object>> localRows = new ArrayList<>(end - start);
                    for (int g = start; g < end; g++) {
                        localRows.add(computeGroupRow(entries.get(g), aggregates, srcHeaders));
                    }
                    return localRows;
                }
            );
            for (List<Map<String, Object>> rows : chunkRows) {
                for (Map<String, Object> row : rows) {
                    resultDf.addRow(row);
                }
            }
        } else {
            for (Map.Entry<List<Object>, IntList> entry : entries) {
                resultDf.addRow(computeGroupRow(entry, aggregates, srcHeaders));
            }
        }

        return new CupOfTea(resultDf, this.mutationMode);
    }

    private Map<String, Object> computeGroupRow(
            Map.Entry<List<Object>, IntList> entry,
            Aggregate[] aggregates,
            List<Column> srcHeaders) {
        HashMap<String, Object> newRow = new HashMap<>(groupColumns.size() + aggregates.length);
        List<Object> key = entry.getKey();
        for (int i = 0; i < groupColumns.size(); i++) {
            newRow.put(groupColumns.get(i), key.get(i));
        }

        IntList groupIndices = entry.getValue();
        List<HashMap<String, Object>> lazyRowMaps = null;

        for (Aggregate agg : aggregates) {
            Object val;
            if (agg.supportsVectorizedExecution()) {
                ColumnVector vec = agg.getColumn() != null ? columnVectors.get(agg.getColumn()) : null;
                val = agg.computeIndexedVector(vec, groupIndices.data, groupIndices.size, srcHeaders);
            } else {
                if (lazyRowMaps == null) {
                    lazyRowMaps = materializeRowMaps(groupIndices);
                }
                val = agg.compute(lazyRowMaps, srcHeaders);
            }
            newRow.put(agg.getOutputColumnName(), val);
        }
        return newRow;
    }

    private List<HashMap<String, Object>> materializeRowMaps(IntList indices) {
        List<HashMap<String, Object>> list = new ArrayList<>(indices.size);
        for (int i = 0; i < indices.size; i++) {
            int rowIdx = indices.data[i];
            HashMap<String, Object> map = new HashMap<>(sourceHeaders.size());
            for (Column col : sourceHeaders) {
                ColumnVector vec = columnVectors.get(col.columnName());
                map.put(col.columnName(), vec != null ? vec.getObject(rowIdx) : null);
            }
            list.add(map);
        }
        return list;
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
