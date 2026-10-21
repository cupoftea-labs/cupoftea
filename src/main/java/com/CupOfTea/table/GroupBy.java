package com.CupOfTea.table;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.MutationMode;
import com.CupOfTea.exceptions.SchemaMismatchException;

public class GroupBy {

    private final List<String> groupColumns;
    private final List<Column> sourceHeaders;
    private final Map<List<Object>, List<HashMap<String, Object>>> partitions;
    private final MutationMode mutationMode;

    public GroupBy(CupOfTea source, String... columns) {
        if (source == null) {
            throw new IllegalArgumentException("Source DataFrame cannot be null");
        }
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("At least one grouping column must be specified");
        }
        this.mutationMode = source.getMutationMode();
        this.sourceHeaders = new ArrayList<>();
        if (source.getHeaders() != null) {
            for (Column col : source.getHeaders()) {
                this.sourceHeaders.add(new Column(col.columnName(), col.columnType()));
            }
        }
        this.groupColumns = Arrays.asList(columns);

        for (String col : columns) {
            if (!hasColumn(col)) {
                throw new SchemaMismatchException("Column '" + col + "' does not exist in DataFrame");
            }
        }

        this.partitions = new LinkedHashMap<>();
        for (Row row : source.getRows()) {
            List<Object> key = new ArrayList<>(columns.length);
            for (String col : columns) {
                key.add(row.get(col));
            }
            partitions.computeIfAbsent(key, k -> new ArrayList<>()).add(new HashMap<>(row.toMap()));
        }
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
        ArrayList<Column> newHeaders = new ArrayList<>();

        for (String colName : groupColumns) {
            Column srcCol = findColumn(colName);
            newHeaders.add(new Column(srcCol.columnName(), srcCol.columnType()));
        }

        List<Column> srcHeaders = this.sourceHeaders;
        for (Aggregate agg : aggregates) {
            newHeaders.add(new Column(agg.getOutputColumnName(), agg.getResultType(srcHeaders)));
        }

        resultDf.setHeaders(newHeaders);

        for (Map.Entry<List<Object>, List<HashMap<String, Object>>> entry : partitions.entrySet()) {
            HashMap<String, Object> newRow = new HashMap<>();
            List<Object> key = entry.getKey();
            for (int i = 0; i < groupColumns.size(); i++) {
                newRow.put(groupColumns.get(i), key.get(i));
            }
            List<HashMap<String, Object>> groupRows = entry.getValue();
            for (Aggregate agg : aggregates) {
                Object val = agg.compute(groupRows, srcHeaders);
                newRow.put(agg.getOutputColumnName(), val);
            }
            resultDf.addRow(newRow);
        }

        return new CupOfTea(resultDf, this.mutationMode);
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
