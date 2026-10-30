package com.cupoftea.table;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.Introselect;
import com.cupoftea.table.vector.ParallelVectorEngine;

public class Aggregations {

    public static Aggregate sum(String column) {
        return new BaseAggregate(column, column + "Sum") {
            @Override
            public boolean supportsVectorizedExecution() {
                return true;
            }

            @Override
            public boolean supportsSinglePass() {
                return true;
            }

            @Override
            public GroupAccumulator createAccumulator(int numGroups, ColumnVector vector, List<Column> sourceHeaders) {
                return GroupAccumulators.createSumAccumulator(numGroups, vector, getOutputColumnName(), getResultType(sourceHeaders));
            }

            @Override
            public Object computeVector(
                    ColumnVector vector,
                    int totalRows,
                    List<Column> sourceHeaders,
                    boolean parallel,
                    ExecutorService executor,
                    int parallelThreshold) {
                if (vector == null || vector.size() == 0) {
                    return null;
                }
                Object raw = parallel
                        ? vector.sum(executor, parallelThreshold)
                        : vector.sum(0, vector.size());
                if (raw == null) {
                    return null;
                }
                Class<?> colType = findColumnType(column, sourceHeaders);
                return coerceSumType(raw, colType);
            }

            @Override
            public Object computeIndexedVector(
                    ColumnVector vector,
                    int[] rowIndices,
                    int count,
                    List<Column> sourceHeaders) {
                if (vector == null || count <= 0) {
                    return null;
                }
                Object raw = vector.sumIndexed(rowIndices, count);
                if (raw == null) {
                    return null;
                }
                Class<?> colType = findColumnType(column, sourceHeaders);
                return coerceSumType(raw, colType);
            }

            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                Class<?> colType = findColumnType(column, sourceHeaders);
                BigDecimal sum = BigDecimal.ZERO;
                boolean hasNonNull = false;
                for (HashMap<String, Object> row : rows) {
                    Object val = row.get(column);
                    BigDecimal bd = toBigDecimal(val);
                    if (bd != null) {
                        sum = sum.add(bd);
                        hasNonNull = true;
                    }
                }
                if (!hasNonNull) {
                    return null;
                }
                return convertFromBigDecimal(sum, colType);
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                Class<?> colType = findColumnType(column, sourceHeaders);
                if (colType == null) return Double.class;
                if (colType == Integer.class || colType == Long.class || colType == Float.class ||
                    colType == Double.class || colType == BigDecimal.class) {
                    return colType;
                }
                return Double.class;
            }
        };
    }

    public static Aggregate mean(String column) {
        return createMeanAggregate(column, column + "Mean");
    }

    public static Aggregate average(String column) {
        return createMeanAggregate(column, column + "Average");
    }

    public static Aggregate avg(String column) {
        return createMeanAggregate(column, column + "Avg");
    }

    private static Aggregate createMeanAggregate(String column, String defaultOutputName) {
        return new BaseAggregate(column, defaultOutputName) {
            @Override
            public boolean supportsVectorizedExecution() {
                return true;
            }

            @Override
            public boolean supportsSinglePass() {
                return true;
            }

            @Override
            public GroupAccumulator createAccumulator(int numGroups, ColumnVector vector, List<Column> sourceHeaders) {
                return GroupAccumulators.createMeanAccumulator(numGroups, vector, getOutputColumnName(), getResultType(sourceHeaders));
            }

            @Override
            public Object computeVector(
                    ColumnVector vector,
                    int totalRows,
                    List<Column> sourceHeaders,
                    boolean parallel,
                    ExecutorService executor,
                    int parallelThreshold) {
                if (vector == null || vector.size() == 0) {
                    return null;
                }
                return parallel
                        ? vector.mean(executor, parallelThreshold)
                        : vector.mean(0, vector.size());
            }

            @Override
            public Object computeIndexedVector(
                    ColumnVector vector,
                    int[] rowIndices,
                    int count,
                    List<Column> sourceHeaders) {
                if (vector == null || count <= 0) {
                    return null;
                }
                return vector.meanIndexed(rowIndices, count);
            }

            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                BigDecimal sum = BigDecimal.ZERO;
                long count = 0;
                for (HashMap<String, Object> row : rows) {
                    Object val = row.get(column);
                    BigDecimal bd = toBigDecimal(val);
                    if (bd != null) {
                        sum = sum.add(bd);
                        count++;
                    }
                }
                if (count == 0) {
                    return null;
                }
                Class<?> colType = findColumnType(column, sourceHeaders);
                BigDecimal avg = sum.divide(BigDecimal.valueOf(count), MathContext.DECIMAL128);
                if (colType == BigDecimal.class) {
                    return avg;
                }
                return avg.doubleValue();
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                Class<?> colType = findColumnType(column, sourceHeaders);
                if (colType == BigDecimal.class) {
                    return BigDecimal.class;
                }
                return Double.class;
            }
        };
    }

    public static Aggregate count(String column) {
        return new BaseAggregate(column, column + "Count") {
            @Override
            public boolean supportsVectorizedExecution() {
                return true;
            }

            @Override
            public boolean supportsSinglePass() {
                return true;
            }

            @Override
            public GroupAccumulator createAccumulator(int numGroups, ColumnVector vector, List<Column> sourceHeaders) {
                return GroupAccumulators.createNonNullCountAccumulator(numGroups, vector, getOutputColumnName());
            }

            @Override
            public Object computeVector(
                    ColumnVector vector,
                    int totalRows,
                    List<Column> sourceHeaders,
                    boolean parallel,
                    ExecutorService executor,
                    int parallelThreshold) {
                if (vector == null) {
                    return 0;
                }
                return parallel
                        ? vector.countNonNull(executor, parallelThreshold)
                        : vector.countNonNull();
            }

            @Override
            public Object computeIndexedVector(
                    ColumnVector vector,
                    int[] rowIndices,
                    int count,
                    List<Column> sourceHeaders) {
                if (vector == null || count <= 0) {
                    return 0;
                }
                return vector.countNonNullIndexed(rowIndices, count);
            }

            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                int count = 0;
                for (HashMap<String, Object> row : rows) {
                    Object val = row.get(column);
                    if (val != null && !NA.isNA(val)) {
                        count++;
                    }
                }
                return count;
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                return Integer.class;
            }
        };
    }

    public static Aggregate count() {
        return new BaseAggregate(null, "count") {
            @Override
            public boolean supportsVectorizedExecution() {
                return true;
            }

            @Override
            public boolean supportsSinglePass() {
                return true;
            }

            @Override
            public GroupAccumulator createAccumulator(int numGroups, ColumnVector vector, List<Column> sourceHeaders) {
                return GroupAccumulators.createCountAccumulator(numGroups, getOutputColumnName());
            }

            @Override
            public Object computeVector(
                    ColumnVector vector,
                    int totalRows,
                    List<Column> sourceHeaders,
                    boolean parallel,
                    ExecutorService executor,
                    int parallelThreshold) {
                return totalRows;
            }

            @Override
            public Object computeIndexedVector(
                    ColumnVector vector,
                    int[] rowIndices,
                    int count,
                    List<Column> sourceHeaders) {
                return count;
            }

            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                return rows.size();
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                return Integer.class;
            }
        };
    }

    public static Aggregate min(String column) {
        return createMinAggregate(column, column + "Min");
    }

    public static Aggregate minimum(String column) {
        return createMinAggregate(column, column + "Minimum");
    }

    private static Aggregate createMinAggregate(String column, String defaultOutputName) {
        return new BaseAggregate(column, defaultOutputName) {
            @Override
            public boolean supportsVectorizedExecution() {
                return true;
            }

            @Override
            public boolean supportsSinglePass() {
                return true;
            }

            @Override
            public GroupAccumulator createAccumulator(int numGroups, ColumnVector vector, List<Column> sourceHeaders) {
                return GroupAccumulators.createMinAccumulator(numGroups, vector, getOutputColumnName(), getResultType(sourceHeaders));
            }

            @Override
            public Object computeVector(
                    ColumnVector vector,
                    int totalRows,
                    List<Column> sourceHeaders,
                    boolean parallel,
                    ExecutorService executor,
                    int parallelThreshold) {
                if (vector == null || vector.size() == 0) {
                    return null;
                }
                return parallel
                        ? vector.min(executor, parallelThreshold)
                        : vector.min(0, vector.size());
            }

            @Override
            public Object computeIndexedVector(
                    ColumnVector vector,
                    int[] rowIndices,
                    int count,
                    List<Column> sourceHeaders) {
                if (vector == null || count <= 0) {
                    return null;
                }
                return vector.minIndexed(rowIndices, count);
            }

            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                List<Object> values = new ArrayList<>();
                for (HashMap<String, Object> row : rows) {
                    Object val = row.get(column);
                    if (val != null && !NA.isNA(val)) {
                        values.add(val);
                    }
                }
                if (values.isEmpty()) return null;
                return computeMin(values);
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                Class<?> colType = findColumnType(column, sourceHeaders);
                return colType != null ? colType : Object.class;
            }
        };
    }

    public static Aggregate max(String column) {
        return createMaxAggregate(column, column + "Max");
    }

    public static Aggregate maximum(String column) {
        return createMaxAggregate(column, column + "Maximum");
    }

    private static Aggregate createMaxAggregate(String column, String defaultOutputName) {
        return new BaseAggregate(column, defaultOutputName) {
            @Override
            public boolean supportsVectorizedExecution() {
                return true;
            }

            @Override
            public boolean supportsSinglePass() {
                return true;
            }

            @Override
            public GroupAccumulator createAccumulator(int numGroups, ColumnVector vector, List<Column> sourceHeaders) {
                return GroupAccumulators.createMaxAccumulator(numGroups, vector, getOutputColumnName(), getResultType(sourceHeaders));
            }

            @Override
            public Object computeVector(
                    ColumnVector vector,
                    int totalRows,
                    List<Column> sourceHeaders,
                    boolean parallel,
                    ExecutorService executor,
                    int parallelThreshold) {
                if (vector == null || vector.size() == 0) {
                    return null;
                }
                return parallel
                        ? vector.max(executor, parallelThreshold)
                        : vector.max(0, vector.size());
            }

            @Override
            public Object computeIndexedVector(
                    ColumnVector vector,
                    int[] rowIndices,
                    int count,
                    List<Column> sourceHeaders) {
                if (vector == null || count <= 0) {
                    return null;
                }
                return vector.maxIndexed(rowIndices, count);
            }

            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                List<Object> values = new ArrayList<>();
                for (HashMap<String, Object> row : rows) {
                    Object val = row.get(column);
                    if (val != null && !NA.isNA(val)) {
                        values.add(val);
                    }
                }
                if (values.isEmpty()) return null;
                return computeMax(values);
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                Class<?> colType = findColumnType(column, sourceHeaders);
                return colType != null ? colType : Object.class;
            }
        };
    }

    public static Aggregate aggregate(String column, String separator) {
        return createListAgg(column, column + "Aggregate", separator);
    }

    public static Aggregate listAgg(String column, String separator) {
        return createListAgg(column, column + "ListAgg", separator);
    }

    private static Aggregate createListAgg(String column, String defaultOutputName, String separator) {
        return new BaseAggregate(column, defaultOutputName) {
            @Override
            public boolean supportsVectorizedExecution() {
                return true;
            }

            @Override
            public boolean supportsSinglePass() {
                return true;
            }

            @Override
            public GroupAccumulator createAccumulator(int numGroups, ColumnVector vector, List<Column> sourceHeaders) {
                return GroupAccumulators.createListAggAccumulator(numGroups, vector, separator, getOutputColumnName());
            }

            @Override
            public Object computeVector(
                    ColumnVector vector,
                    int totalRows,
                    List<Column> sourceHeaders,
                    boolean parallel,
                    ExecutorService executor,
                    int parallelThreshold) {
                if (vector == null || vector.size() == 0) {
                    return "";
                }
                if (!parallel || vector.size() < parallelThreshold || vector.size() <= 1) {
                    StringBuilder sb = new StringBuilder();
                    boolean first = true;
                    for (int i = 0; i < vector.size(); i++) {
                        if (!vector.isNull(i)) {
                            Object val = vector.getObject(i);
                            if (val != null && !NA.isNA(val)) {
                                if (!first) sb.append(separator);
                                sb.append(val);
                                first = false;
                            }
                        }
                    }
                    return sb.toString();
                }

                List<String> chunkStrings = ParallelVectorEngine.executeChunks(vector.size(), executor, (chunkIdx, start, end) -> {
                    StringBuilder sb = new StringBuilder();
                    boolean first = true;
                    for (int i = start; i < end; i++) {
                        if (!vector.isNull(i)) {
                            Object val = vector.getObject(i);
                            if (val != null && !NA.isNA(val)) {
                                if (!first) sb.append(separator);
                                sb.append(val);
                                first = false;
                            }
                        }
                    }
                    return first ? null : sb.toString();
                });

                StringBuilder result = new StringBuilder();
                boolean first = true;
                for (String cs : chunkStrings) {
                    if (cs != null) {
                        if (!first) result.append(separator);
                        result.append(cs);
                        first = false;
                    }
                }
                return result.toString();
            }

            @Override
            public Object computeIndexedVector(
                    ColumnVector vector,
                    int[] rowIndices,
                    int count,
                    List<Column> sourceHeaders) {
                if (vector == null || count <= 0) {
                    return "";
                }
                StringBuilder sb = new StringBuilder();
                boolean first = true;
                for (int i = 0; i < count; i++) {
                    int row = rowIndices[i];
                    if (!vector.isNull(row)) {
                        Object val = vector.getObject(row);
                        if (val != null && !NA.isNA(val)) {
                            if (!first) sb.append(separator);
                            sb.append(val);
                            first = false;
                        }
                    }
                }
                return sb.toString();
            }

            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                return rows.stream()
                        .map(r -> r.get(column))
                        .filter(val -> val != null && !NA.isNA(val))
                        .map(Object::toString)
                        .collect(Collectors.joining(separator));
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                return String.class;
            }
        };
    }

    public static Aggregate first(String column) {
        return new BaseAggregate(column, column + "First") {
            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                for (HashMap<String, Object> row : rows) {
                    Object val = row.get(column);
                    if (val != null && !NA.isNA(val)) {
                        return val;
                    }
                }
                return rows.isEmpty() ? null : rows.get(0).get(column);
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                Class<?> colType = findColumnType(column, sourceHeaders);
                return colType != null ? colType : Object.class;
            }
        };
    }

    public static Aggregate last(String column) {
        return new BaseAggregate(column, column + "Last") {
            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                for (int i = rows.size() - 1; i >= 0; i--) {
                    Object val = rows.get(i).get(column);
                    if (val != null && !NA.isNA(val)) {
                        return val;
                    }
                }
                return rows.isEmpty() ? null : rows.get(rows.size() - 1).get(column);
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                Class<?> colType = findColumnType(column, sourceHeaders);
                return colType != null ? colType : Object.class;
            }
        };
    }

    public static Aggregate median(String column) {
        return createMedianAggregate(column, column + "Median");
    }

    private static Aggregate createMedianAggregate(String column, String defaultOutputName) {
        return new BaseAggregate(column, defaultOutputName) {
            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                double[] vals = extractDoubleArray(rows, column);
                if (vals.length == 0) {
                    return null;
                }
                return Introselect.quantile(vals, 0, vals.length, 0.5);
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                return Double.class;
            }
        };
    }

    public static Aggregate quantile(String column, double q) {
        String tag = (q == (long) q) ? String.valueOf((long) q) : String.valueOf(q).replace('.', '_');
        return createQuantileAggregate(column, column + "Quantile_" + tag, q);
    }

    private static Aggregate createQuantileAggregate(String column, String defaultOutputName, double q) {
        return new BaseAggregate(column, defaultOutputName) {
            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                double[] vals = extractDoubleArray(rows, column);
                if (vals.length == 0) {
                    return null;
                }
                return Introselect.quantile(vals, 0, vals.length, q);
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                return Double.class;
            }
        };
    }

    public static Aggregate variance(String column) {
        return createVarAggregate(column, column + "Variance", true);
    }

    public static Aggregate var(String column) {
        return createVarAggregate(column, column + "Var", true);
    }

    private static Aggregate createVarAggregate(String column, String defaultOutputName, boolean isSample) {
        return new BaseAggregate(column, defaultOutputName) {
            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                double[] vals = extractDoubleArray(rows, column);
                if (vals.length < (isSample ? 2 : 1)) {
                    return null;
                }
                double mean = computeMean(vals);
                double sumSq = 0.0;
                for (double v : vals) {
                    double d = v - mean;
                    sumSq += d * d;
                }
                return sumSq / (isSample ? (vals.length - 1) : vals.length);
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                return Double.class;
            }
        };
    }

    public static Aggregate standardDeviation(String column) {
        return createStdAggregate(column, column + "StandardDeviation", true);
    }

    public static Aggregate std(String column) {
        return createStdAggregate(column, column + "Std", true);
    }

    private static Aggregate createStdAggregate(String column, String defaultOutputName, boolean isSample) {
        return new BaseAggregate(column, defaultOutputName) {
            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                double[] vals = extractDoubleArray(rows, column);
                if (vals.length < (isSample ? 2 : 1)) {
                    return null;
                }
                double mean = computeMean(vals);
                double sumSq = 0.0;
                for (double v : vals) {
                    double d = v - mean;
                    sumSq += d * d;
                }
                double variance = sumSq / (isSample ? (vals.length - 1) : vals.length);
                return Math.sqrt(variance);
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                return Double.class;
            }
        };
    }

    public static Aggregate skewness(String column) {
        return new BaseAggregate(column, column + "Skewness") {
            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                double[] vals = extractDoubleArray(rows, column);
                int n = vals.length;
                if (n < 3) {
                    return null;
                }
                double mean = computeMean(vals);
                double sum2 = 0.0;
                double sum3 = 0.0;
                for (double v : vals) {
                    double d = v - mean;
                    sum2 += d * d;
                    sum3 += d * d * d;
                }
                double m2 = sum2 / n;
                if (m2 == 0.0) {
                    return null;
                }
                double m3 = sum3 / n;
                double g1 = m3 / Math.pow(m2, 1.5);
                return (Math.sqrt((double) n * (n - 1)) / (n - 2.0)) * g1;
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                return Double.class;
            }
        };
    }

    public static Aggregate kurtosis(String column) {
        return new BaseAggregate(column, column + "Kurtosis") {
            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                double[] vals = extractDoubleArray(rows, column);
                int n = vals.length;
                if (n < 4) {
                    return null;
                }
                double mean = computeMean(vals);
                double sum2 = 0.0;
                double sum4 = 0.0;
                for (double v : vals) {
                    double d = v - mean;
                    double d2 = d * d;
                    sum2 += d2;
                    sum4 += d2 * d2;
                }
                double m2 = sum2 / n;
                if (m2 == 0.0) {
                    return null;
                }
                double m4 = sum4 / n;
                double term1 = ((double) (n - 1)) / ((n - 2.0) * (n - 3.0));
                double term2 = (n + 1.0) * (m4 / (m2 * m2)) - 3.0 * (n - 1.0);
                return term1 * term2;
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                return Double.class;
            }
        };
    }

    private static double[] extractDoubleArray(List<HashMap<String, Object>> rows, String column) {
        if (rows == null || column == null) {
            return new double[0];
        }
        int nonNullCount = 0;
        for (HashMap<String, Object> row : rows) {
            Object val = row.get(column);
            if (val != null && !NA.isNA(val)) {
                nonNullCount++;
            }
        }
        if (nonNullCount == 0) {
            return new double[0];
        }
        double[] result = new double[nonNullCount];
        int idx = 0;
        for (HashMap<String, Object> row : rows) {
            Object val = row.get(column);
            if (val != null && !NA.isNA(val)) {
                BigDecimal bd = toBigDecimal(val);
                if (bd != null) {
                    result[idx++] = bd.doubleValue();
                }
            }
        }
        if (idx < nonNullCount) {
            return Arrays.copyOf(result, idx);
        }
        return result;
    }

    private static double computeMean(double[] vals) {
        double s = 0.0;
        for (double v : vals) {
            s += v;
        }
        return s / vals.length;
    }

    public static abstract class BaseAggregate implements Aggregate {
        protected final String column;
        protected String outputColumnName;

        public BaseAggregate(String column, String defaultOutputName) {
            this.column = column;
            this.outputColumnName = defaultOutputName;
        }

        @Override
        public String getColumn() {
            return column;
        }

        @Override
        public String getOutputColumnName() {
            return outputColumnName;
        }

        @Override
        public Aggregate as(String alias) {
            if (alias == null || alias.trim().isEmpty()) {
                throw new IllegalArgumentException("Alias cannot be null or empty");
            }
            this.outputColumnName = alias;
            return this;
        }
    }

    public static Class<?> findColumnType(String column, List<Column> sourceHeaders) {
        if (column == null || sourceHeaders == null) return null;
        for (Column col : sourceHeaders) {
            if (column.equals(col.columnName())) {
                return col.columnType();
            }
        }
        return null;
    }

    public static BigDecimal toBigDecimal(Object val) {
        if (val == null || NA.isNA(val)) return null;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof BigInteger) return new BigDecimal((BigInteger) val);
        if (val instanceof Double || val instanceof Float) {
            return BigDecimal.valueOf(((Number) val).doubleValue());
        }
        if (val instanceof Number) {
            return BigDecimal.valueOf(((Number) val).longValue());
        }
        try {
            return new BigDecimal(val.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Object coerceSumType(Object raw, Class<?> targetType) {
        if (raw == null || targetType == null) return raw;
        if (targetType.isInstance(raw)) return raw;
        if (raw instanceof Number n) {
            if (targetType == Integer.class || targetType == int.class) return n.intValue();
            if (targetType == Long.class || targetType == long.class) return n.longValue();
            if (targetType == Float.class || targetType == float.class) return n.floatValue();
            if (targetType == Double.class || targetType == double.class) return n.doubleValue();
            if (targetType == BigDecimal.class) return toBigDecimal(n);
        }
        return raw;
    }

    private static Object convertFromBigDecimal(BigDecimal val, Class<?> targetType) {
        if (val == null) return null;
        if (targetType == null) return val.doubleValue();
        if (targetType == Integer.class || targetType == int.class) {
            return val.intValue();
        }
        if (targetType == Long.class || targetType == long.class) {
            return val.longValue();
        }
        if (targetType == Float.class || targetType == float.class) {
            return val.floatValue();
        }
        if (targetType == BigDecimal.class) {
            return val;
        }
        return val.doubleValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object computeMin(List<Object> values) {
        if (values.isEmpty()) return null;
        Object first = values.get(0);
        if (first instanceof Number) {
            Object min = first;
            BigDecimal minBd = toBigDecimal(first);
            for (int i = 1; i < values.size(); i++) {
                Object v = values.get(i);
                BigDecimal vBd = toBigDecimal(v);
                if (vBd != null && (minBd == null || vBd.compareTo(minBd) < 0)) {
                    minBd = vBd;
                    min = v;
                }
            }
            return min;
        } else if (first instanceof Comparable) {
            Object min = first;
            for (int i = 1; i < values.size(); i++) {
                Object v = values.get(i);
                if (v instanceof Comparable && ((Comparable) v).compareTo(min) < 0) {
                    min = v;
                }
            }
            return min;
        }
        return first;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object computeMax(List<Object> values) {
        if (values.isEmpty()) return null;
        Object first = values.get(0);
        if (first instanceof Number) {
            Object max = first;
            BigDecimal maxBd = toBigDecimal(first);
            for (int i = 1; i < values.size(); i++) {
                Object v = values.get(i);
                BigDecimal vBd = toBigDecimal(v);
                if (vBd != null && (maxBd == null || vBd.compareTo(maxBd) > 0)) {
                    maxBd = vBd;
                    max = v;
                }
            }
            return max;
        } else if (first instanceof Comparable) {
            Object max = first;
            for (int i = 1; i < values.size(); i++) {
                Object v = values.get(i);
                if (v instanceof Comparable && ((Comparable) v).compareTo(max) > 0) {
                    max = v;
                }
            }
            return max;
        }
        return first;
    }
}
