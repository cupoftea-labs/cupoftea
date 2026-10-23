package com.cupoftea.table.window;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.ToDoubleFunction;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.exceptions.SchemaMismatchException;
import com.cupoftea.table.Column;
import com.cupoftea.table.NA;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.vector.ColumnVector;

/**
 * Expanding window builder and computation engine.
 * Window includes all historical observations from the start of the DataFrame up to the current row [0, i].
 * Supports O(1) incremental statistics (mean, sum, min, max, std, variance, count) and median.
 */
public class ExpandingWindow {

    private final CupOfTea sourceWrapper;
    private final IDataFrame dataFrame;
    private final MutationMode mutationMode;
    private int minPeriods;

    public ExpandingWindow(CupOfTea source) {
        this(source, 1);
    }

    public ExpandingWindow(CupOfTea source, int minPeriods) {
        if (source == null) {
            throw new IllegalArgumentException("Source DataFrame cannot be null");
        }
        if (minPeriods <= 0) {
            throw new IllegalArgumentException("minPeriods must be greater than 0, got: " + minPeriods);
        }
        this.sourceWrapper = source;
        this.dataFrame = source.getDataFrame();
        this.mutationMode = source.getMutationMode();
        this.minPeriods = minPeriods;
    }

    public ExpandingWindow(IDataFrame dataFrame) {
        this(dataFrame, 1);
    }

    public ExpandingWindow(IDataFrame dataFrame, int minPeriods) {
        if (dataFrame == null) {
            throw new IllegalArgumentException("DataFrame cannot be null");
        }
        if (minPeriods <= 0) {
            throw new IllegalArgumentException("minPeriods must be greater than 0, got: " + minPeriods);
        }
        this.sourceWrapper = null;
        this.dataFrame = dataFrame;
        this.mutationMode = MutationMode.IN_PLACE;
        this.minPeriods = minPeriods;
    }

    public ExpandingWindow minPeriods(int minPeriods) {
        if (minPeriods <= 0) {
            throw new IllegalArgumentException("minPeriods must be greater than 0, got: " + minPeriods);
        }
        this.minPeriods = minPeriods;
        return this;
    }

    public int getMinPeriods() {
        return minPeriods;
    }

    // ==========================================
    // Expanding Mean
    // ==========================================

    public CupOfTea mean(String... columns) {
        return executeExpandingOp("mean", columns, (col, targetVec) -> computeExpandingMean(targetVec));
    }

    public CupOfTea mean(String column, String outputColumn) {
        return executeExpandingOpSingle("mean", column, outputColumn, this::computeExpandingMean);
    }

    // ==========================================
    // Expanding Sum
    // ==========================================

    public CupOfTea sum(String... columns) {
        return executeExpandingOp("sum", columns, (col, targetVec) -> computeExpandingSum(targetVec));
    }

    public CupOfTea sum(String column, String outputColumn) {
        return executeExpandingOpSingle("sum", column, outputColumn, this::computeExpandingSum);
    }

    // ==========================================
    // Expanding Min
    // ==========================================

    public CupOfTea min(String... columns) {
        return executeExpandingOp("min", columns, (col, targetVec) -> computeExpandingMin(targetVec));
    }

    public CupOfTea min(String column, String outputColumn) {
        return executeExpandingOpSingle("min", column, outputColumn, this::computeExpandingMin);
    }

    // ==========================================
    // Expanding Max
    // ==========================================

    public CupOfTea max(String... columns) {
        return executeExpandingOp("max", columns, (col, targetVec) -> computeExpandingMax(targetVec));
    }

    public CupOfTea max(String column, String outputColumn) {
        return executeExpandingOpSingle("max", column, outputColumn, this::computeExpandingMax);
    }

    // ==========================================
    // Expanding Std & Variance
    // ==========================================

    public CupOfTea std(String... columns) {
        return std(true, columns);
    }

    public CupOfTea std(boolean isSample, String... columns) {
        return executeExpandingOp("std", columns, (col, targetVec) -> computeExpandingVarianceOrStd(targetVec, isSample, true));
    }

    public CupOfTea std(String column, String outputColumn) {
        return std(column, true, outputColumn);
    }

    public CupOfTea std(String column, boolean isSample, String outputColumn) {
        return executeExpandingOpSingle("std", column, outputColumn, vec -> computeExpandingVarianceOrStd(vec, isSample, true));
    }

    public CupOfTea variance(String... columns) {
        return variance(true, columns);
    }

    public CupOfTea variance(boolean isSample, String... columns) {
        return executeExpandingOp("variance", columns, (col, targetVec) -> computeExpandingVarianceOrStd(targetVec, isSample, false));
    }

    public CupOfTea var(String... columns) {
        return variance(true, columns);
    }

    public CupOfTea var(boolean isSample, String... columns) {
        return variance(isSample, columns);
    }

    public CupOfTea variance(String column, String outputColumn) {
        return variance(column, true, outputColumn);
    }

    public CupOfTea variance(String column, boolean isSample, String outputColumn) {
        return executeExpandingOpSingle("variance", column, outputColumn, vec -> computeExpandingVarianceOrStd(vec, isSample, false));
    }

    // ==========================================
    // Expanding Median
    // ==========================================

    public CupOfTea median(String... columns) {
        return executeExpandingOp("median", columns, (col, targetVec) -> computeExpandingMedian(targetVec));
    }

    public CupOfTea median(String column, String outputColumn) {
        return executeExpandingOpSingle("median", column, outputColumn, this::computeExpandingMedian);
    }

    // ==========================================
    // Expanding Count
    // ==========================================

    public CupOfTea count(String... columns) {
        return executeExpandingOp("count", columns, (col, targetVec) -> computeExpandingCount(targetVec));
    }

    public CupOfTea count(String column, String outputColumn) {
        return executeExpandingOpSingle("count", column, outputColumn, this::computeExpandingCount);
    }

    // ==========================================
    // Custom Aggregation (apply)
    // ==========================================

    public CupOfTea apply(String column, WindowFunction<List<Object>, Object> func) {
        return apply(column, column, func);
    }

    public CupOfTea apply(String column, String outputColumn, WindowFunction<List<Object>, Object> func) {
        return executeExpandingOpSingle("apply", column, outputColumn, vec -> computeExpandingApply(vec, func));
    }

    public CupOfTea apply(String column, ToDoubleFunction<double[]> func) {
        return apply(column, column, func);
    }

    public CupOfTea apply(String column, String outputColumn, ToDoubleFunction<double[]> func) {
        return executeExpandingOpSingle("apply", column, outputColumn, vec -> computeExpandingApplyPrimitive(vec, func));
    }

    // ==========================================
    // Internal Dispatchers & Mutation Targets
    // ==========================================

    @FunctionalInterface
    private interface ExpandingComputation {
        Object[] compute(ColumnVector vector);
    }

    @FunctionalInterface
    private interface VectorOp {
        Object[] apply(String colName, ColumnVector vector);
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

    private List<String> resolveTargetColumns(String... columns) {
        List<String> targetCols = new ArrayList<>();
        if (columns == null || columns.length == 0) {
            for (Column col : this.dataFrame.getHeaders()) {
                if (isNumericType(col.columnType())) {
                    targetCols.add(col.columnName());
                }
            }
        } else {
            for (String col : columns) {
                if (this.dataFrame.getColumnHeader(col) == null) {
                    throw new SchemaMismatchException("Column '" + col + "' does not exist in DataFrame");
                }
                targetCols.add(col);
            }
        }
        return targetCols;
    }

    private CupOfTea executeExpandingOp(String opName, String[] columns, VectorOp op) {
        List<String> targetCols = resolveTargetColumns(columns);
        CupOfTea target = getTarget();
        IDataFrame df = target.getDataFrame();

        for (String col : targetCols) {
            ColumnVector srcVec = df.getColumnVector(col);
            if (srcVec == null) {
                throw new SchemaMismatchException("Column '" + col + "' not found in DataFrame");
            }
            Object[] results = op.apply(col, srcVec);
            Class<?> resultType = determineResultType(srcVec.getType(), results);
            replaceOrAddColumn(df, col, resultType, results);
        }
        return target;
    }

    private CupOfTea executeExpandingOpSingle(String opName, String column, String outputColumn, ExpandingComputation computation) {
        if (column == null) {
            throw new IllegalArgumentException("Source column cannot be null");
        }
        if (this.dataFrame.getColumnHeader(column) == null) {
            throw new SchemaMismatchException("Column '" + column + "' does not exist in DataFrame");
        }
        String outCol = outputColumn != null ? outputColumn : column;
        CupOfTea target = getTarget();
        IDataFrame df = target.getDataFrame();
        ColumnVector srcVec = df.getColumnVector(column);
        Object[] results = computation.compute(srcVec);
        Class<?> resultType = determineResultType(srcVec.getType(), results);
        replaceOrAddColumn(df, outCol, resultType, results);
        return target;
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

    private static Class<?> determineResultType(Class<?> srcType, Object[] results) {
        if (srcType == BigDecimal.class) {
            return BigDecimal.class;
        }
        for (Object r : results) {
            if (r != null) {
                return r.getClass();
            }
        }
        return Double.class;
    }

    private static boolean isNumericType(Class<?> type) {
        if (type == null) return false;
        return Number.class.isAssignableFrom(type)
            || type == int.class
            || type == long.class
            || type == double.class
            || type == float.class
            || type == short.class
            || type == byte.class;
    }

    // ==========================================
    // Incremental Expanding Window Implementations
    // ==========================================

    private Object[] computeExpandingSum(ColumnVector vec) {
        int n = vec.size();
        Object[] result = new Object[n];
        if (n == 0) return result;

        boolean isDecimal = (vec.getType() == BigDecimal.class);
        int validCount = 0;
        double sum = 0.0;
        BigDecimal bigSum = BigDecimal.ZERO;

        for (int i = 0; i < n; i++) {
            if (!vec.isNull(i)) {
                validCount++;
                if (isDecimal) {
                    bigSum = bigSum.add((BigDecimal) vec.getObject(i));
                } else {
                    sum += vec.getDouble(i);
                }
            }

            if (validCount >= minPeriods) {
                if (isDecimal) {
                    result[i] = bigSum;
                } else if (vec.getType() == Integer.class || vec.getType() == int.class) {
                    result[i] = (int) Math.round(sum);
                } else if (vec.getType() == Long.class || vec.getType() == long.class) {
                    result[i] = Math.round(sum);
                } else {
                    result[i] = sum;
                }
            } else {
                result[i] = null;
            }
        }
        return result;
    }

    private Object[] computeExpandingMean(ColumnVector vec) {
        int n = vec.size();
        Object[] result = new Object[n];
        if (n == 0) return result;

        boolean isDecimal = (vec.getType() == BigDecimal.class);
        int validCount = 0;
        double sum = 0.0;
        BigDecimal bigSum = BigDecimal.ZERO;

        for (int i = 0; i < n; i++) {
            if (!vec.isNull(i)) {
                validCount++;
                if (isDecimal) {
                    bigSum = bigSum.add((BigDecimal) vec.getObject(i));
                } else {
                    sum += vec.getDouble(i);
                }
            }

            if (validCount >= minPeriods) {
                if (isDecimal) {
                    result[i] = bigSum.divide(BigDecimal.valueOf(validCount), java.math.MathContext.DECIMAL128);
                } else {
                    result[i] = sum / validCount;
                }
            } else {
                result[i] = null;
            }
        }
        return result;
    }

    private Object[] computeExpandingMin(ColumnVector vec) {
        int n = vec.size();
        Object[] result = new Object[n];
        if (n == 0) return result;

        int validCount = 0;
        Object minObj = null;
        double minVal = Double.POSITIVE_INFINITY;

        for (int i = 0; i < n; i++) {
            if (!vec.isNull(i)) {
                validCount++;
                double v = vec.getDouble(i);
                if (v < minVal) {
                    minVal = v;
                    minObj = vec.getObject(i);
                }
            }
            if (validCount >= minPeriods) {
                result[i] = minObj;
            } else {
                result[i] = null;
            }
        }
        return result;
    }

    private Object[] computeExpandingMax(ColumnVector vec) {
        int n = vec.size();
        Object[] result = new Object[n];
        if (n == 0) return result;

        int validCount = 0;
        Object maxObj = null;
        double maxVal = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < n; i++) {
            if (!vec.isNull(i)) {
                validCount++;
                double v = vec.getDouble(i);
                if (v > maxVal) {
                    maxVal = v;
                    maxObj = vec.getObject(i);
                }
            }
            if (validCount >= minPeriods) {
                result[i] = maxObj;
            } else {
                result[i] = null;
            }
        }
        return result;
    }

    private Object[] computeExpandingVarianceOrStd(ColumnVector vec, boolean isSample, boolean returnStd) {
        int n = vec.size();
        Object[] result = new Object[n];
        if (n == 0) return result;

        int validCount = 0;
        double sumX = 0.0;
        double sumX2 = 0.0;

        for (int i = 0; i < n; i++) {
            if (!vec.isNull(i)) {
                validCount++;
                double v = vec.getDouble(i);
                sumX += v;
                sumX2 += v * v;
            }

            int minRequired = isSample ? Math.max(2, minPeriods) : Math.max(1, minPeriods);
            if (validCount >= minRequired) {
                double mean = sumX / validCount;
                double numerator = sumX2 - sumX * mean;
                if (numerator < 0) {
                    numerator = 0.0;
                }
                double var = numerator / (isSample ? (validCount - 1) : validCount);
                result[i] = returnStd ? Math.sqrt(var) : var;
            } else {
                result[i] = null;
            }
        }
        return result;
    }

    private Object[] computeExpandingMedian(ColumnVector vec) {
        int n = vec.size();
        Object[] result = new Object[n];
        if (n == 0) return result;

        PriorityQueue<Double> maxHeap = new PriorityQueue<>(Collections.reverseOrder());
        PriorityQueue<Double> minHeap = new PriorityQueue<>();

        int validCount = 0;
        for (int i = 0; i < n; i++) {
            if (!vec.isNull(i)) {
                validCount++;
                double val = vec.getDouble(i);
                if (maxHeap.isEmpty() || val <= maxHeap.peek()) {
                    maxHeap.offer(val);
                } else {
                    minHeap.offer(val);
                }
                while (maxHeap.size() > minHeap.size() + 1) {
                    minHeap.offer(maxHeap.poll());
                }
                while (maxHeap.size() < minHeap.size()) {
                    maxHeap.offer(minHeap.poll());
                }
            }

            if (validCount >= minPeriods && (maxHeap.size() + minHeap.size() > 0)) {
                if ((maxHeap.size() + minHeap.size()) % 2 == 1) {
                    result[i] = maxHeap.peek();
                } else {
                    result[i] = (maxHeap.peek() + minHeap.peek()) / 2.0;
                }
            } else {
                result[i] = null;
            }
        }
        return result;
    }

    private Object[] computeExpandingCount(ColumnVector vec) {
        int n = vec.size();
        Object[] result = new Object[n];
        if (n == 0) return result;

        int validCount = 0;
        for (int i = 0; i < n; i++) {
            if (!vec.isNull(i)) {
                validCount++;
            }
            result[i] = validCount;
        }
        return result;
    }

    private Object[] computeExpandingApply(ColumnVector vec, WindowFunction<List<Object>, Object> func) {
        int n = vec.size();
        Object[] result = new Object[n];
        if (n == 0) return result;

        List<Object> windowVals = new ArrayList<>();
        int validCount = 0;
        for (int i = 0; i < n; i++) {
            Object val = vec.getObject(i);
            windowVals.add(val);
            if (val != null && !NA.isNA(val)) {
                validCount++;
            }
            if (validCount >= minPeriods) {
                result[i] = func.apply(new ArrayList<>(windowVals));
            } else {
                result[i] = null;
            }
        }
        return result;
    }

    private Object[] computeExpandingApplyPrimitive(ColumnVector vec, ToDoubleFunction<double[]> func) {
        int n = vec.size();
        Object[] result = new Object[n];
        if (n == 0) return result;

        List<Double> validVals = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!vec.isNull(i)) {
                validVals.add(vec.getDouble(i));
            }
            if (validVals.size() >= minPeriods) {
                double[] prims = new double[validVals.size()];
                for (int j = 0; j < validVals.size(); j++) {
                    prims[j] = validVals.get(j);
                }
                result[i] = func.applyAsDouble(prims);
            } else {
                result[i] = null;
            }
        }
        return result;
    }
}
