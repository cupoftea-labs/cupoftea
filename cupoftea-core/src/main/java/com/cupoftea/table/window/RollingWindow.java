package com.cupoftea.table.window;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * Sliding rolling window builder and computation engine.
 * Supports sliding window statistics (mean, sum, min, max, std, variance, median, count, apply)
 * using amortized O(1) sliding window algorithms (monotonic deque, running moments, etc.).
 */
public class RollingWindow {

    private final CupOfTea sourceWrapper;
    private final IDataFrame dataFrame;
    private final MutationMode mutationMode;
    private final int windowSize;
    private int minPeriods;
    private WindowDirection direction = WindowDirection.BACKWARD;

    public RollingWindow(CupOfTea source, int windowSize) {
        if (source == null) {
            throw new IllegalArgumentException("Source DataFrame cannot be null");
        }
        if (windowSize <= 0) {
            throw new IllegalArgumentException("Window size must be greater than 0, got: " + windowSize);
        }
        this.sourceWrapper = source;
        this.dataFrame = source.getDataFrame();
        this.mutationMode = source.getMutationMode();
        this.windowSize = windowSize;
        this.minPeriods = windowSize;
    }

    public RollingWindow(IDataFrame dataFrame, int windowSize) {
        if (dataFrame == null) {
            throw new IllegalArgumentException("DataFrame cannot be null");
        }
        if (windowSize <= 0) {
            throw new IllegalArgumentException("Window size must be greater than 0, got: " + windowSize);
        }
        this.sourceWrapper = null;
        this.dataFrame = dataFrame;
        this.mutationMode = MutationMode.IN_PLACE;
        this.windowSize = windowSize;
        this.minPeriods = windowSize;
    }

    public RollingWindow minPeriods(int minPeriods) {
        if (minPeriods <= 0) {
            throw new IllegalArgumentException("minPeriods must be greater than 0, got: " + minPeriods);
        }
        this.minPeriods = minPeriods;
        return this;
    }

    public RollingWindow center(boolean center) {
        this.direction = center ? WindowDirection.CENTERED : WindowDirection.BACKWARD;
        return this;
    }

    public RollingWindow center() {
        return center(true);
    }

    public RollingWindow centered(boolean centered) {
        return center(centered);
    }

    public RollingWindow centered() {
        return center(true);
    }

    public RollingWindow forward(boolean forward) {
        this.direction = forward ? WindowDirection.FORWARD : WindowDirection.BACKWARD;
        return this;
    }

    public RollingWindow forward() {
        return forward(true);
    }

    public RollingWindow backward() {
        this.direction = WindowDirection.BACKWARD;
        return this;
    }

    public RollingWindow direction(WindowDirection direction) {
        this.direction = direction != null ? direction : WindowDirection.BACKWARD;
        return this;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public int getMinPeriods() {
        return minPeriods;
    }

    public WindowDirection getDirection() {
        return direction;
    }

    // ==========================================
    // Rolling Mean
    // ==========================================

    public CupOfTea mean(String... columns) {
        return executeRollingOp("mean", columns, (col, targetVec) -> computeRollingMean(targetVec));
    }

    public CupOfTea mean(String column, String outputColumn) {
        return executeRollingOpSingle("mean", column, outputColumn, this::computeRollingMean);
    }

    // ==========================================
    // Rolling Sum
    // ==========================================

    public CupOfTea sum(String... columns) {
        return executeRollingOp("sum", columns, (col, targetVec) -> computeRollingSum(targetVec));
    }

    public CupOfTea sum(String column, String outputColumn) {
        return executeRollingOpSingle("sum", column, outputColumn, this::computeRollingSum);
    }

    // ==========================================
    // Rolling Min
    // ==========================================

    public CupOfTea min(String... columns) {
        return executeRollingOp("min", columns, (col, targetVec) -> computeRollingMin(targetVec));
    }

    public CupOfTea min(String column, String outputColumn) {
        return executeRollingOpSingle("min", column, outputColumn, this::computeRollingMin);
    }

    // ==========================================
    // Rolling Max
    // ==========================================

    public CupOfTea max(String... columns) {
        return executeRollingOp("max", columns, (col, targetVec) -> computeRollingMax(targetVec));
    }

    public CupOfTea max(String column, String outputColumn) {
        return executeRollingOpSingle("max", column, outputColumn, this::computeRollingMax);
    }

    // ==========================================
    // Rolling Std & Variance
    // ==========================================

    public CupOfTea std(String... columns) {
        return std(true, columns);
    }

    public CupOfTea std(boolean isSample, String... columns) {
        return executeRollingOp("std", columns, (col, targetVec) -> computeRollingVarianceOrStd(targetVec, isSample, true));
    }

    public CupOfTea std(String column, String outputColumn) {
        return std(column, true, outputColumn);
    }

    public CupOfTea std(String column, boolean isSample, String outputColumn) {
        return executeRollingOpSingle("std", column, outputColumn, vec -> computeRollingVarianceOrStd(vec, isSample, true));
    }

    public CupOfTea variance(String... columns) {
        return variance(true, columns);
    }

    public CupOfTea variance(boolean isSample, String... columns) {
        return executeRollingOp("variance", columns, (col, targetVec) -> computeRollingVarianceOrStd(targetVec, isSample, false));
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
        return executeRollingOpSingle("variance", column, outputColumn, vec -> computeRollingVarianceOrStd(vec, isSample, false));
    }

    // ==========================================
    // Rolling Median
    // ==========================================

    public CupOfTea median(String... columns) {
        return executeRollingOp("median", columns, (col, targetVec) -> computeRollingMedian(targetVec));
    }

    public CupOfTea median(String column, String outputColumn) {
        return executeRollingOpSingle("median", column, outputColumn, this::computeRollingMedian);
    }

    // ==========================================
    // Rolling Count
    // ==========================================

    public CupOfTea count(String... columns) {
        return executeRollingOp("count", columns, (col, targetVec) -> computeRollingCount(targetVec));
    }

    public CupOfTea count(String column, String outputColumn) {
        return executeRollingOpSingle("count", column, outputColumn, this::computeRollingCount);
    }

    // ==========================================
    // Custom Aggregation (apply)
    // ==========================================

    public CupOfTea apply(String column, WindowFunction<List<Object>, Object> func) {
        return apply(column, column, func);
    }

    public CupOfTea apply(String column, String outputColumn, WindowFunction<List<Object>, Object> func) {
        return executeRollingOpSingle("apply", column, outputColumn, vec -> computeRollingApply(vec, func));
    }

    public CupOfTea apply(String column, ToDoubleFunction<double[]> func) {
        return apply(column, column, func);
    }

    public CupOfTea apply(String column, String outputColumn, ToDoubleFunction<double[]> func) {
        return executeRollingOpSingle("apply", column, outputColumn, vec -> computeRollingApplyPrimitive(vec, func));
    }

    // ==========================================
    // Internal Dispatchers & Mutation Targets
    // ==========================================

    @FunctionalInterface
    private interface RollingComputation {
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

    private CupOfTea executeRollingOp(String opName, String[] columns, VectorOp op) {
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

    private CupOfTea executeRollingOpSingle(String opName, String column, String outputColumn, RollingComputation computation) {
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
    // High-Performance Sliding Window Implementations
    // ==========================================

    /**
     * O(1) amortized sliding window sum.
     */
    private Object[] computeRollingSum(ColumnVector vec) {
        int n = vec.size();
        Object[] result = new Object[n];
        if (n == 0) return result;

        boolean isDecimal = (vec.getType() == BigDecimal.class);

        int leftOffset = (direction == WindowDirection.CENTERED) ? (windowSize - 1) / 2 : 0;
        int rightOffset = (direction == WindowDirection.CENTERED) ? (windowSize - 1 - leftOffset)
                : (direction == WindowDirection.FORWARD ? windowSize - 1 : 0);

        int validCount = 0;
        double sum = 0.0;
        BigDecimal bigSum = BigDecimal.ZERO;

        int initEnd = Math.min(n - 1, rightOffset);
        for (int j = 0; j <= initEnd; j++) {
            if (!vec.isNull(j)) {
                validCount++;
                if (isDecimal) {
                    bigSum = bigSum.add((BigDecimal) vec.getObject(j));
                } else {
                    sum += vec.getDouble(j);
                }
            }
        }

        for (int i = 0; i < n; i++) {
            if (i > 0) {
                int inIdx = (direction == WindowDirection.BACKWARD) ? i : (i + rightOffset);
                if (inIdx < n && !vec.isNull(inIdx)) {
                    validCount++;
                    if (isDecimal) {
                        bigSum = bigSum.add((BigDecimal) vec.getObject(inIdx));
                    } else {
                        sum += vec.getDouble(inIdx);
                    }
                }

                int outIdx = (direction == WindowDirection.BACKWARD) ? (i - windowSize)
                        : ((direction == WindowDirection.FORWARD) ? (i - 1) : (i - 1 - leftOffset));
                if (outIdx >= 0 && !vec.isNull(outIdx)) {
                    validCount--;
                    if (isDecimal) {
                        bigSum = bigSum.subtract((BigDecimal) vec.getObject(outIdx));
                    } else {
                        sum -= vec.getDouble(outIdx);
                    }
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

    /**
     * O(1) amortized sliding window mean.
     */
    private Object[] computeRollingMean(ColumnVector vec) {
        int n = vec.size();
        Object[] result = new Object[n];
        if (n == 0) return result;

        boolean isDecimal = (vec.getType() == BigDecimal.class);

        int leftOffset = (direction == WindowDirection.CENTERED) ? (windowSize - 1) / 2 : 0;
        int rightOffset = (direction == WindowDirection.CENTERED) ? (windowSize - 1 - leftOffset)
                : (direction == WindowDirection.FORWARD ? windowSize - 1 : 0);

        int validCount = 0;
        double sum = 0.0;
        BigDecimal bigSum = BigDecimal.ZERO;

        int initEnd = Math.min(n - 1, rightOffset);
        for (int j = 0; j <= initEnd; j++) {
            if (!vec.isNull(j)) {
                validCount++;
                if (isDecimal) {
                    bigSum = bigSum.add((BigDecimal) vec.getObject(j));
                } else {
                    sum += vec.getDouble(j);
                }
            }
        }

        for (int i = 0; i < n; i++) {
            if (i > 0) {
                int inIdx = (direction == WindowDirection.BACKWARD) ? i : (i + rightOffset);
                if (inIdx < n && !vec.isNull(inIdx)) {
                    validCount++;
                    if (isDecimal) {
                        bigSum = bigSum.add((BigDecimal) vec.getObject(inIdx));
                    } else {
                        sum += vec.getDouble(inIdx);
                    }
                }

                int outIdx = (direction == WindowDirection.BACKWARD) ? (i - windowSize)
                        : ((direction == WindowDirection.FORWARD) ? (i - 1) : (i - 1 - leftOffset));
                if (outIdx >= 0 && !vec.isNull(outIdx)) {
                    validCount--;
                    if (isDecimal) {
                        bigSum = bigSum.subtract((BigDecimal) vec.getObject(outIdx));
                    } else {
                        sum -= vec.getDouble(outIdx);
                    }
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

    /**
     * O(1) amortized sliding window minimum via Monotonic Deque.
     */
    private Object[] computeRollingMin(ColumnVector vec) {
        int n = vec.size();
        Object[] result = new Object[n];
        if (n == 0) return result;

        int leftOffset = (direction == WindowDirection.CENTERED) ? (windowSize - 1) / 2 : 0;
        int rightOffset = (direction == WindowDirection.CENTERED) ? (windowSize - 1 - leftOffset)
                : (direction == WindowDirection.FORWARD ? windowSize - 1 : 0);

        ArrayDeque<Integer> deque = new ArrayDeque<>();
        int validCount = 0;

        int initEnd = Math.min(n - 1, rightOffset);
        for (int j = 0; j <= initEnd; j++) {
            if (!vec.isNull(j)) {
                validCount++;
                double v = vec.getDouble(j);
                while (!deque.isEmpty() && vec.getDouble(deque.peekLast()) >= v) {
                    deque.pollLast();
                }
                deque.addLast(j);
            }
        }

        for (int i = 0; i < n; i++) {
            if (i > 0) {
                int inIdx = (direction == WindowDirection.BACKWARD) ? i : (i + rightOffset);
                if (inIdx < n && !vec.isNull(inIdx)) {
                    validCount++;
                    double v = vec.getDouble(inIdx);
                    while (!deque.isEmpty() && vec.getDouble(deque.peekLast()) >= v) {
                        deque.pollLast();
                    }
                    deque.addLast(inIdx);
                }

                int outIdx = (direction == WindowDirection.BACKWARD) ? (i - windowSize)
                        : ((direction == WindowDirection.FORWARD) ? (i - 1) : (i - 1 - leftOffset));
                if (outIdx >= 0) {
                    if (!vec.isNull(outIdx)) {
                        validCount--;
                    }
                    if (!deque.isEmpty() && deque.peekFirst() == outIdx) {
                        deque.pollFirst();
                    }
                }
            }

            if (validCount >= minPeriods && !deque.isEmpty()) {
                result[i] = vec.getObject(deque.peekFirst());
            } else {
                result[i] = null;
            }
        }
        return result;
    }

    /**
     * O(1) amortized sliding window maximum via Monotonic Deque.
     */
    private Object[] computeRollingMax(ColumnVector vec) {
        int n = vec.size();
        Object[] result = new Object[n];
        if (n == 0) return result;

        int leftOffset = (direction == WindowDirection.CENTERED) ? (windowSize - 1) / 2 : 0;
        int rightOffset = (direction == WindowDirection.CENTERED) ? (windowSize - 1 - leftOffset)
                : (direction == WindowDirection.FORWARD ? windowSize - 1 : 0);

        ArrayDeque<Integer> deque = new ArrayDeque<>();
        int validCount = 0;

        int initEnd = Math.min(n - 1, rightOffset);
        for (int j = 0; j <= initEnd; j++) {
            if (!vec.isNull(j)) {
                validCount++;
                double v = vec.getDouble(j);
                while (!deque.isEmpty() && vec.getDouble(deque.peekLast()) <= v) {
                    deque.pollLast();
                }
                deque.addLast(j);
            }
        }

        for (int i = 0; i < n; i++) {
            if (i > 0) {
                int inIdx = (direction == WindowDirection.BACKWARD) ? i : (i + rightOffset);
                if (inIdx < n && !vec.isNull(inIdx)) {
                    validCount++;
                    double v = vec.getDouble(inIdx);
                    while (!deque.isEmpty() && vec.getDouble(deque.peekLast()) <= v) {
                        deque.pollLast();
                    }
                    deque.addLast(inIdx);
                }

                int outIdx = (direction == WindowDirection.BACKWARD) ? (i - windowSize)
                        : ((direction == WindowDirection.FORWARD) ? (i - 1) : (i - 1 - leftOffset));
                if (outIdx >= 0) {
                    if (!vec.isNull(outIdx)) {
                        validCount--;
                    }
                    if (!deque.isEmpty() && deque.peekFirst() == outIdx) {
                        deque.pollFirst();
                    }
                }
            }

            if (validCount >= minPeriods && !deque.isEmpty()) {
                result[i] = vec.getObject(deque.peekFirst());
            } else {
                result[i] = null;
            }
        }
        return result;
    }

    /**
     * O(1) amortized sliding window variance and standard deviation.
     */
    private Object[] computeRollingVarianceOrStd(ColumnVector vec, boolean isSample, boolean returnStd) {
        int n = vec.size();
        Object[] result = new Object[n];
        if (n == 0) return result;

        int leftOffset = (direction == WindowDirection.CENTERED) ? (windowSize - 1) / 2 : 0;
        int rightOffset = (direction == WindowDirection.CENTERED) ? (windowSize - 1 - leftOffset)
                : (direction == WindowDirection.FORWARD ? windowSize - 1 : 0);

        int validCount = 0;
        double sumX = 0.0;
        double sumX2 = 0.0;

        int initEnd = Math.min(n - 1, rightOffset);
        for (int j = 0; j <= initEnd; j++) {
            if (!vec.isNull(j)) {
                validCount++;
                double v = vec.getDouble(j);
                sumX += v;
                sumX2 += v * v;
            }
        }

        for (int i = 0; i < n; i++) {
            if (i > 0) {
                int inIdx = (direction == WindowDirection.BACKWARD) ? i : (i + rightOffset);
                if (inIdx < n && !vec.isNull(inIdx)) {
                    validCount++;
                    double v = vec.getDouble(inIdx);
                    sumX += v;
                    sumX2 += v * v;
                }

                int outIdx = (direction == WindowDirection.BACKWARD) ? (i - windowSize)
                        : ((direction == WindowDirection.FORWARD) ? (i - 1) : (i - 1 - leftOffset));
                if (outIdx >= 0 && !vec.isNull(outIdx)) {
                    validCount--;
                    double v = vec.getDouble(outIdx);
                    sumX -= v;
                    sumX2 -= v * v;
                }
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

    /**
     * O(log W) sliding window median via dual heaps with lazy deletion.
     */
    private Object[] computeRollingMedian(ColumnVector vec) {
        int n = vec.size();
        Object[] result = new Object[n];
        if (n == 0) return result;

        int leftOffset = (direction == WindowDirection.CENTERED) ? (windowSize - 1) / 2 : 0;
        int rightOffset = (direction == WindowDirection.CENTERED) ? (windowSize - 1 - leftOffset)
                : (direction == WindowDirection.FORWARD ? windowSize - 1 : 0);

        PriorityQueue<Double> maxHeap = new PriorityQueue<>(Collections.reverseOrder());
        PriorityQueue<Double> minHeap = new PriorityQueue<>();
        Map<Double, Integer> delayed = new HashMap<>();
        int[] heapSizes = new int[2]; // 0: maxHeap, 1: minHeap

        Runnable rebalance = () -> {
            while (heapSizes[0] > heapSizes[1] + 1) {
                pruneHeap(maxHeap, delayed);
                double val = maxHeap.poll();
                minHeap.offer(val);
                heapSizes[0]--;
                heapSizes[1]++;
                pruneHeap(maxHeap, delayed);
                pruneHeap(minHeap, delayed);
            }
            while (heapSizes[0] < heapSizes[1]) {
                pruneHeap(minHeap, delayed);
                double val = minHeap.poll();
                maxHeap.offer(val);
                heapSizes[1]--;
                heapSizes[0]++;
                pruneHeap(maxHeap, delayed);
                pruneHeap(minHeap, delayed);
            }
        };

        java.util.function.Consumer<Double> addElement = val -> {
            pruneHeap(maxHeap, delayed);
            if (maxHeap.isEmpty() || val <= maxHeap.peek()) {
                maxHeap.offer(val);
                heapSizes[0]++;
            } else {
                minHeap.offer(val);
                heapSizes[1]++;
            }
            rebalance.run();
        };

        java.util.function.Consumer<Double> removeElement = val -> {
            delayed.put(val, delayed.getOrDefault(val, 0) + 1);
            pruneHeap(maxHeap, delayed);
            if (!maxHeap.isEmpty() && val <= maxHeap.peek()) {
                heapSizes[0]--;
            } else {
                heapSizes[1]--;
            }
            rebalance.run();
        };

        int validCount = 0;
        int initEnd = Math.min(n - 1, rightOffset);
        for (int j = 0; j <= initEnd; j++) {
            if (!vec.isNull(j)) {
                validCount++;
                addElement.accept(vec.getDouble(j));
            }
        }

        for (int i = 0; i < n; i++) {
            if (i > 0) {
                int inIdx = (direction == WindowDirection.BACKWARD) ? i : (i + rightOffset);
                if (inIdx < n && !vec.isNull(inIdx)) {
                    validCount++;
                    addElement.accept(vec.getDouble(inIdx));
                }

                int outIdx = (direction == WindowDirection.BACKWARD) ? (i - windowSize)
                        : ((direction == WindowDirection.FORWARD) ? (i - 1) : (i - 1 - leftOffset));
                if (outIdx >= 0 && !vec.isNull(outIdx)) {
                    validCount--;
                    removeElement.accept(vec.getDouble(outIdx));
                }
            }

            if (validCount >= minPeriods && (heapSizes[0] + heapSizes[1] > 0)) {
                pruneHeap(maxHeap, delayed);
                pruneHeap(minHeap, delayed);
                if ((heapSizes[0] + heapSizes[1]) % 2 == 1) {
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

    private static void pruneHeap(PriorityQueue<Double> heap, Map<Double, Integer> delayed) {
        while (!heap.isEmpty()) {
            double peek = heap.peek();
            Integer count = delayed.get(peek);
            if (count != null && count > 0) {
                heap.poll();
                if (count == 1) {
                    delayed.remove(peek);
                } else {
                    delayed.put(peek, count - 1);
                }
            } else {
                break;
            }
        }
    }

    /**
     * O(1) amortized sliding window valid observations count.
     */
    private Object[] computeRollingCount(ColumnVector vec) {
        int n = vec.size();
        Object[] result = new Object[n];
        if (n == 0) return result;

        int leftOffset = (direction == WindowDirection.CENTERED) ? (windowSize - 1) / 2 : 0;
        int rightOffset = (direction == WindowDirection.CENTERED) ? (windowSize - 1 - leftOffset)
                : (direction == WindowDirection.FORWARD ? windowSize - 1 : 0);

        int validCount = 0;
        int initEnd = Math.min(n - 1, rightOffset);
        for (int j = 0; j <= initEnd; j++) {
            if (!vec.isNull(j)) {
                validCount++;
            }
        }

        for (int i = 0; i < n; i++) {
            if (i > 0) {
                int inIdx = (direction == WindowDirection.BACKWARD) ? i : (i + rightOffset);
                if (inIdx < n && !vec.isNull(inIdx)) {
                    validCount++;
                }

                int outIdx = (direction == WindowDirection.BACKWARD) ? (i - windowSize)
                        : ((direction == WindowDirection.FORWARD) ? (i - 1) : (i - 1 - leftOffset));
                if (outIdx >= 0 && !vec.isNull(outIdx)) {
                    validCount--;
                }
            }
            result[i] = validCount;
        }
        return result;
    }

    /**
     * Sliding window apply with object list.
     */
    private Object[] computeRollingApply(ColumnVector vec, WindowFunction<List<Object>, Object> func) {
        int n = vec.size();
        Object[] result = new Object[n];
        if (n == 0) return result;

        int leftOffset = (direction == WindowDirection.CENTERED) ? (windowSize - 1) / 2 : 0;
        int rightOffset = (direction == WindowDirection.CENTERED) ? (windowSize - 1 - leftOffset)
                : (direction == WindowDirection.FORWARD ? windowSize - 1 : 0);

        for (int i = 0; i < n; i++) {
            int start = (direction == WindowDirection.BACKWARD) ? Math.max(0, i - windowSize + 1)
                    : ((direction == WindowDirection.FORWARD) ? i : Math.max(0, i - leftOffset));
            int end = (direction == WindowDirection.BACKWARD) ? i
                    : ((direction == WindowDirection.FORWARD) ? Math.min(n - 1, i + windowSize - 1) : Math.min(n - 1, i + rightOffset));

            List<Object> windowVals = new ArrayList<>();
            int validCount = 0;
            for (int j = start; j <= end; j++) {
                Object val = vec.getObject(j);
                windowVals.add(val);
                if (val != null && !NA.isNA(val)) {
                    validCount++;
                }
            }

            if (validCount >= minPeriods) {
                result[i] = func.apply(windowVals);
            } else {
                result[i] = null;
            }
        }
        return result;
    }

    /**
     * Sliding window apply with primitive double array.
     */
    private Object[] computeRollingApplyPrimitive(ColumnVector vec, ToDoubleFunction<double[]> func) {
        int n = vec.size();
        Object[] result = new Object[n];
        if (n == 0) return result;

        int leftOffset = (direction == WindowDirection.CENTERED) ? (windowSize - 1) / 2 : 0;
        int rightOffset = (direction == WindowDirection.CENTERED) ? (windowSize - 1 - leftOffset)
                : (direction == WindowDirection.FORWARD ? windowSize - 1 : 0);

        for (int i = 0; i < n; i++) {
            int start = (direction == WindowDirection.BACKWARD) ? Math.max(0, i - windowSize + 1)
                    : ((direction == WindowDirection.FORWARD) ? i : Math.max(0, i - leftOffset));
            int end = (direction == WindowDirection.BACKWARD) ? i
                    : ((direction == WindowDirection.FORWARD) ? Math.min(n - 1, i + windowSize - 1) : Math.min(n - 1, i + rightOffset));

            int validCount = 0;
            for (int j = start; j <= end; j++) {
                if (!vec.isNull(j)) {
                    validCount++;
                }
            }

            if (validCount >= minPeriods) {
                double[] prims = new double[validCount];
                int idx = 0;
                for (int j = start; j <= end; j++) {
                    if (!vec.isNull(j)) {
                        prims[idx++] = vec.getDouble(j);
                    }
                }
                result[i] = func.applyAsDouble(prims);
            } else {
                result[i] = null;
            }
        }
        return result;
    }
}
