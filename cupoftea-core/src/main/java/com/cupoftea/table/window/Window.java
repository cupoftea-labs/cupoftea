package com.cupoftea.table.window;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.exceptions.SchemaMismatchException;
import com.cupoftea.table.Column;
import com.cupoftea.table.NA;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.utils.interfaces.Order;
import com.cupoftea.utils.interfaces.Orderer;

/**
 * SQL-like analytical window specification and execution builder.
 * Supports partitionBy, orderBy, and analytical functions (rank, denseRank, percentRank, rowNumber, lead, lag, etc.)
 * over partitioned slices while preserving original DataFrame row alignment.
 */
public class Window {

    private final CupOfTea sourceWrapper;
    private final IDataFrame dataFrame;
    private final MutationMode mutationMode;
    private final List<String> partitionColumns = new ArrayList<>();
    private final List<Orderer> orderers = new ArrayList<>();

    public Window(CupOfTea source) {
        if (source == null) {
            throw new IllegalArgumentException("Source DataFrame cannot be null");
        }
        this.sourceWrapper = source;
        this.dataFrame = source.getDataFrame();
        this.mutationMode = source.getMutationMode();
    }

    public Window(IDataFrame dataFrame) {
        if (dataFrame == null) {
            throw new IllegalArgumentException("DataFrame cannot be null");
        }
        this.sourceWrapper = null;
        this.dataFrame = dataFrame;
        this.mutationMode = MutationMode.IN_PLACE;
    }

    public Window partitionBy(String... columns) {
        if (columns != null) {
            partitionBy(Arrays.asList(columns));
        }
        return this;
    }

    public Window partitionBy(List<String> columns) {
        this.partitionColumns.clear();
        if (columns != null) {
            for (String col : columns) {
                if (col != null) {
                    if (this.dataFrame.getColumnHeader(col) == null) {
                        throw new SchemaMismatchException("Partition column '" + col + "' does not exist in DataFrame");
                    }
                    this.partitionColumns.add(col);
                }
            }
        }
        return this;
    }

    public Window orderBy(Orderer... orderers) {
        this.orderers.clear();
        if (orderers != null) {
            for (Orderer ord : orderers) {
                if (ord != null && ord.column() != null) {
                    if (this.dataFrame.getColumnHeader(ord.column()) == null) {
                        throw new SchemaMismatchException("Order column '" + ord.column() + "' does not exist in DataFrame");
                    }
                    this.orderers.add(ord);
                }
            }
        }
        return this;
    }

    public Window orderBy(String column) {
        return orderBy(column, Order.ASCENDING);
    }

    public Window orderBy(String column, Order order) {
        if (column == null) {
            throw new IllegalArgumentException("Column cannot be null");
        }
        return orderBy(new Orderer(column, order != null ? order : Order.ASCENDING));
    }

    // ==========================================
    // Ranking Functions
    // ==========================================

    public CupOfTea rank() {
        return rank("rank");
    }

    public CupOfTea rank(String outputColumn) {
        return executeWindowMetric(outputColumn, Integer.class, (partitionIndices, df) -> {
            int k = partitionIndices.size();
            Object[] ranks = new Object[k];
            int currentRank = 1;
            for (int j = 0; j < k; j++) {
                if (j > 0) {
                    int prevIdx = partitionIndices.get(j - 1);
                    int currIdx = partitionIndices.get(j);
                    if (compareOrderTies(prevIdx, currIdx, df) != 0) {
                        currentRank = j + 1;
                    }
                }
                ranks[j] = currentRank;
            }
            return ranks;
        });
    }

    public CupOfTea denseRank() {
        return denseRank("dense_rank");
    }

    public CupOfTea denseRank(String outputColumn) {
        return executeWindowMetric(outputColumn, Integer.class, (partitionIndices, df) -> {
            int k = partitionIndices.size();
            Object[] ranks = new Object[k];
            int currentRank = 1;
            for (int j = 0; j < k; j++) {
                if (j > 0) {
                    int prevIdx = partitionIndices.get(j - 1);
                    int currIdx = partitionIndices.get(j);
                    if (compareOrderTies(prevIdx, currIdx, df) != 0) {
                        currentRank++;
                    }
                }
                ranks[j] = currentRank;
            }
            return ranks;
        });
    }

    public CupOfTea percentRank() {
        return percentRank("percent_rank");
    }

    public CupOfTea percentRank(String outputColumn) {
        return executeWindowMetric(outputColumn, Double.class, (partitionIndices, df) -> {
            int k = partitionIndices.size();
            Object[] ranks = new Object[k];
            if (k <= 1) {
                for (int j = 0; j < k; j++) {
                    ranks[j] = 0.0;
                }
                return ranks;
            }

            int currentRank = 1;
            for (int j = 0; j < k; j++) {
                if (j > 0) {
                    int prevIdx = partitionIndices.get(j - 1);
                    int currIdx = partitionIndices.get(j);
                    if (compareOrderTies(prevIdx, currIdx, df) != 0) {
                        currentRank = j + 1;
                    }
                }
                ranks[j] = (double) (currentRank - 1) / (k - 1);
            }
            return ranks;
        });
    }

    public CupOfTea rowNumber() {
        return rowNumber("row_number");
    }

    public CupOfTea rowNumber(String outputColumn) {
        return executeWindowMetric(outputColumn, Integer.class, (partitionIndices, df) -> {
            int k = partitionIndices.size();
            Object[] numbers = new Object[k];
            for (int j = 0; j < k; j++) {
                numbers[j] = j + 1;
            }
            return numbers;
        });
    }

    // ==========================================
    // Analytic Shift & Difference
    // ==========================================

    public CupOfTea lead(String column) {
        return lead(column, 1, column);
    }

    public CupOfTea lead(String column, int offset) {
        return lead(column, offset, column);
    }

    public CupOfTea lead(String column, int offset, String outputColumn) {
        Column colHeader = requireColumn(column);
        return executeWindowMetric(outputColumn != null ? outputColumn : column, colHeader.columnType(), (partitionIndices, df) -> {
            int k = partitionIndices.size();
            Object[] values = new Object[k];
            ColumnVector vec = df.getColumnVector(column);
            for (int j = 0; j < k; j++) {
                int targetPos = j + offset;
                if (targetPos >= 0 && targetPos < k) {
                    int srcRow = partitionIndices.get(targetPos);
                    values[j] = vec.getObject(srcRow);
                } else {
                    values[j] = null;
                }
            }
            return values;
        });
    }

    public CupOfTea lag(String column) {
        return lag(column, 1, column);
    }

    public CupOfTea lag(String column, int offset) {
        return lag(column, offset, column);
    }

    public CupOfTea lag(String column, int offset, String outputColumn) {
        return lead(column, -offset, outputColumn);
    }

    public CupOfTea diff(String column) {
        return diff(column, 1, column);
    }

    public CupOfTea diff(String column, int periods) {
        return diff(column, periods, column);
    }

    public CupOfTea diff(String column, int periods, String outputColumn) {
        Column colHeader = requireColumn(column);
        Class<?> colType = colHeader.columnType();
        return executeWindowMetric(outputColumn != null ? outputColumn : column, colType == BigDecimal.class ? BigDecimal.class : Double.class, (partitionIndices, df) -> {
            int k = partitionIndices.size();
            Object[] values = new Object[k];
            ColumnVector vec = df.getColumnVector(column);
            boolean isDecimal = (colType == BigDecimal.class);

            for (int j = 0; j < k; j++) {
                int priorPos = j - periods;
                if (priorPos >= 0 && priorPos < k) {
                    int currRow = partitionIndices.get(j);
                    int priorRow = partitionIndices.get(priorPos);
                    if (vec.isNull(currRow) || vec.isNull(priorRow)) {
                        values[j] = null;
                    } else if (isDecimal) {
                        BigDecimal curr = (BigDecimal) vec.getObject(currRow);
                        BigDecimal prior = (BigDecimal) vec.getObject(priorRow);
                        values[j] = curr.subtract(prior);
                    } else if (colType == Integer.class || colType == int.class) {
                        values[j] = vec.getInt(currRow) - vec.getInt(priorRow);
                    } else if (colType == Long.class || colType == long.class) {
                        values[j] = vec.getLong(currRow) - vec.getLong(priorRow);
                    } else {
                        values[j] = vec.getDouble(currRow) - vec.getDouble(priorRow);
                    }
                } else {
                    values[j] = null;
                }
            }
            return values;
        });
    }

    public CupOfTea pctChange(String column) {
        return pctChange(column, 1, column);
    }

    public CupOfTea pctChange(String column, int periods) {
        return pctChange(column, periods, column);
    }

    public CupOfTea pctChange(String column, int periods, String outputColumn) {
        requireColumn(column);
        return executeWindowMetric(outputColumn != null ? outputColumn : column, Double.class, (partitionIndices, df) -> {
            int k = partitionIndices.size();
            Object[] values = new Object[k];
            ColumnVector vec = df.getColumnVector(column);

            for (int j = 0; j < k; j++) {
                int priorPos = j - periods;
                if (priorPos >= 0 && priorPos < k) {
                    int currRow = partitionIndices.get(j);
                    int priorRow = partitionIndices.get(priorPos);
                    if (vec.isNull(currRow) || vec.isNull(priorRow)) {
                        values[j] = null;
                    } else {
                        double priorVal = vec.getDouble(priorRow);
                        double currVal = vec.getDouble(currRow);
                        if (priorVal == 0.0) {
                            values[j] = currVal > 0 ? Double.POSITIVE_INFINITY : (currVal < 0 ? Double.NEGATIVE_INFINITY : Double.NaN);
                        } else {
                            values[j] = (currVal - priorVal) / priorVal;
                        }
                    }
                } else {
                    values[j] = null;
                }
            }
            return values;
        });
    }

    // ==========================================
    // Window Aggregates over Partition
    // ==========================================

    public CupOfTea sum(String column) {
        return sum(column, column);
    }

    public CupOfTea sum(String column, String outputColumn) {
        Column colHeader = requireColumn(column);
        boolean isDecimal = (colHeader.columnType() == BigDecimal.class);
        return executeWindowMetric(outputColumn != null ? outputColumn : column, isDecimal ? BigDecimal.class : Double.class, (partitionIndices, df) -> {
            int k = partitionIndices.size();
            Object[] values = new Object[k];
            ColumnVector vec = df.getColumnVector(column);
            double total = 0.0;
            BigDecimal decTotal = BigDecimal.ZERO;
            int nonNullCount = 0;
            for (int row : partitionIndices) {
                if (!vec.isNull(row)) {
                    nonNullCount++;
                    if (isDecimal) {
                        decTotal = decTotal.add((BigDecimal) vec.getObject(row));
                    } else {
                        total += vec.getDouble(row);
                    }
                }
            }
            Object res = nonNullCount == 0 ? null : (isDecimal ? decTotal : total);
            for (int j = 0; j < k; j++) {
                values[j] = res;
            }
            return values;
        });
    }

    public CupOfTea mean(String column) {
        return mean(column, column);
    }

    public CupOfTea mean(String column, String outputColumn) {
        Column colHeader = requireColumn(column);
        boolean isDecimal = (colHeader.columnType() == BigDecimal.class);
        return executeWindowMetric(outputColumn != null ? outputColumn : column, isDecimal ? BigDecimal.class : Double.class, (partitionIndices, df) -> {
            int k = partitionIndices.size();
            Object[] values = new Object[k];
            ColumnVector vec = df.getColumnVector(column);
            double total = 0.0;
            BigDecimal decTotal = BigDecimal.ZERO;
            int count = 0;
            for (int row : partitionIndices) {
                if (!vec.isNull(row)) {
                    count++;
                    if (isDecimal) {
                        decTotal = decTotal.add((BigDecimal) vec.getObject(row));
                    } else {
                        total += vec.getDouble(row);
                    }
                }
            }
            Object res = null;
            if (count > 0) {
                res = isDecimal ? decTotal.divide(BigDecimal.valueOf(count), java.math.MathContext.DECIMAL128) : (total / count);
            }
            for (int j = 0; j < k; j++) {
                values[j] = res;
            }
            return values;
        });
    }

    public CupOfTea min(String column) {
        return min(column, column);
    }

    public CupOfTea min(String column, String outputColumn) {
        Column colHeader = requireColumn(column);
        return executeWindowMetric(outputColumn != null ? outputColumn : column, colHeader.columnType(), (partitionIndices, df) -> {
            int k = partitionIndices.size();
            Object[] values = new Object[k];
            ColumnVector vec = df.getColumnVector(column);
            Object minVal = null;
            double minDouble = Double.POSITIVE_INFINITY;
            for (int row : partitionIndices) {
                if (!vec.isNull(row)) {
                    double v = vec.getDouble(row);
                    if (v < minDouble) {
                        minDouble = v;
                        minVal = vec.getObject(row);
                    }
                }
            }
            for (int j = 0; j < k; j++) {
                values[j] = minVal;
            }
            return values;
        });
    }

    public CupOfTea max(String column) {
        return max(column, column);
    }

    public CupOfTea max(String column, String outputColumn) {
        Column colHeader = requireColumn(column);
        return executeWindowMetric(outputColumn != null ? outputColumn : column, colHeader.columnType(), (partitionIndices, df) -> {
            int k = partitionIndices.size();
            Object[] values = new Object[k];
            ColumnVector vec = df.getColumnVector(column);
            Object maxVal = null;
            double maxDouble = Double.NEGATIVE_INFINITY;
            for (int row : partitionIndices) {
                if (!vec.isNull(row)) {
                    double v = vec.getDouble(row);
                    if (v > maxDouble) {
                        maxDouble = v;
                        maxVal = vec.getObject(row);
                    }
                }
            }
            for (int j = 0; j < k; j++) {
                values[j] = maxVal;
            }
            return values;
        });
    }

    // ==========================================
    // Internal Partitioning & Execution
    // ==========================================

    @FunctionalInterface
    private interface PartitionComputation {
        Object[] compute(List<Integer> sortedPartitionIndices, IDataFrame df);
    }

    private Column requireColumn(String columnName) {
        if (columnName == null) {
            throw new IllegalArgumentException("Column cannot be null");
        }
        Column col = this.dataFrame.getColumnHeader(columnName);
        if (col == null) {
            throw new SchemaMismatchException("Column '" + columnName + "' does not exist in DataFrame");
        }
        return col;
    }

    private CupOfTea getTarget() {
        if (this.sourceWrapper != null) {
            if (this.mutationMode == MutationMode.COPY_ON_WRITE) {
                return new CupOfTea(this.dataFrame.copy(), this.mutationMode);
            }
            return this.sourceWrapper;
        }
        return new CupOfTea(this.dataFrame, this.mutationMode);
    }

    private CupOfTea executeWindowMetric(String outputColumn, Class<?> outputType, PartitionComputation computation) {
        CupOfTea target = getTarget();
        IDataFrame df = target.getDataFrame();
        int totalRows = df.size();
        Object[] fullResults = new Object[totalRows];

        // 1. Partition rows
        Map<List<Object>, List<Integer>> partitions = new LinkedHashMap<>();
        for (int i = 0; i < totalRows; i++) {
            List<Object> key = new ArrayList<>(partitionColumns.size());
            for (String partCol : partitionColumns) {
                ColumnVector vec = df.getColumnVector(partCol);
                key.add(vec != null ? vec.getObject(i) : null);
            }
            partitions.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }

        // 2. Sort inside partitions and compute window metric
        for (List<Integer> partitionIndices : partitions.values()) {
            if (!orderers.isEmpty() && partitionIndices.size() > 1) {
                partitionIndices.sort((idxA, idxB) -> comparePartitionRows(idxA, idxB, df));
            }
            Object[] partResults = computation.compute(partitionIndices, df);
            for (int j = 0; j < partitionIndices.size(); j++) {
                int originalRow = partitionIndices.get(j);
                fullResults[originalRow] = partResults[j];
            }
        }

        // 3. Put result into target DataFrame
        replaceOrAddColumn(df, outputColumn, outputType, fullResults);
        return target;
    }

    private int comparePartitionRows(int idxA, int idxB, IDataFrame df) {
        for (Orderer ord : this.orderers) {
            if (ord == null || ord.column() == null) continue;
            String colName = ord.column();
            ColumnVector vec = df.getColumnVector(colName);
            if (vec == null) continue;

            Object valA = vec.getObject(idxA);
            Object valB = vec.getObject(idxB);

            boolean nullA = NA.isNA(valA);
            boolean nullB = NA.isNA(valB);

            if (nullA && nullB) continue;
            if (nullA) return 1;  // nulls sort last
            if (nullB) return -1;

            int cmp = compareValues(valA, valB);
            if (cmp != 0) {
                return ord.type() == Order.DESCENDING ? -cmp : cmp;
            }
        }
        return 0;
    }

    private int compareOrderTies(int idxA, int idxB, IDataFrame df) {
        if (this.orderers.isEmpty()) {
            return 0; // all tied if no order by
        }
        for (Orderer ord : this.orderers) {
            if (ord == null || ord.column() == null) continue;
            String colName = ord.column();
            ColumnVector vec = df.getColumnVector(colName);
            if (vec == null) continue;

            Object valA = vec.getObject(idxA);
            Object valB = vec.getObject(idxB);

            boolean nullA = NA.isNA(valA);
            boolean nullB = NA.isNA(valB);

            if (nullA && nullB) continue;
            if (nullA || nullB) return 1; // not tied

            int cmp = compareValues(valA, valB);
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static int compareValues(Object valA, Object valB) {
        if (valA instanceof Number numA && valB instanceof Number numB) {
            return Double.compare(numA.doubleValue(), numB.doubleValue());
        }
        if (valA instanceof Comparable compA && valB instanceof Comparable compB) {
            try {
                return compA.compareTo(compB);
            } catch (ClassCastException ignored) {}
        }
        return valA.toString().compareTo(valB.toString());
    }

    private static void replaceOrAddColumn(IDataFrame df, String columnName, Class<?> type, Object[] values) {
        if (df instanceof com.cupoftea.table.DataFrame d) {
            d.replaceOrAddColumn(columnName, type, values);
            return;
        }
        int total = values.length;
        ColumnVector newVec = ColumnVector.create(columnName, type != null ? type : Object.class, Math.max(16, total), true);
        for (int i = 0; i < total; i++) {
            if (values[i] != null && !NA.isNA(values[i])) {
                newVec.appendObject(values[i]);
            } else {
                newVec.appendNull();
            }
        }
        int existingIdx = -1;
        List<ColumnVector> vectors = df.getColumnVectors();
        for (int i = 0; i < vectors.size(); i++) {
            if (columnName.equals(vectors.get(i).getName())) {
                existingIdx = i;
                break;
            }
        }
        if (existingIdx != -1) {
            ColumnVector inserted = df.getColumnVector(columnName);
            for (int i = 0; i < total; i++) {
                if (newVec.isNull(i)) {
                    inserted.setNull(i);
                } else {
                    inserted.setObject(i, newVec.getObject(i));
                }
            }
        } else {
            df.addColumn(columnName, type != null ? type : Object.class);
            ColumnVector inserted = df.getColumnVector(columnName);
            for (int i = 0; i < total; i++) {
                if (newVec.isNull(i)) {
                    inserted.setNull(i);
                } else {
                    inserted.setObject(i, newVec.getObject(i));
                }
            }
        }
    }
}
