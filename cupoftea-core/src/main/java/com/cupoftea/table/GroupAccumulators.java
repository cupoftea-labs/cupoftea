package com.cupoftea.table;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.ObjectColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * Factory and concrete implementations of single-pass group accumulators.
 */
public final class GroupAccumulators {

    private GroupAccumulators() {}

    public static GroupAccumulator createCountAccumulator(int numGroups, String outputName) {
        return new CountAccumulator(numGroups, outputName);
    }

    public static GroupAccumulator createNonNullCountAccumulator(int numGroups, ColumnVector vector, String outputName) {
        return new NonNullCountAccumulator(numGroups, vector, outputName);
    }

    public static GroupAccumulator createSumAccumulator(
            int numGroups, ColumnVector vector, String outputName, Class<?> resultType) {
        if (vector == null) {
            return new NullAccumulator(outputName, resultType != null ? resultType : Double.class);
        }
        if (resultType == BigDecimal.class || vector.getType() == BigDecimal.class) {
            return new BigDecimalSumAccumulator(numGroups, vector, outputName, resultType);
        }
        if (vector instanceof DoubleColumnVector dcv) {
            return new DoubleSumAccumulator(numGroups, dcv, outputName);
        }
        if (vector instanceof IntColumnVector icv) {
            return new IntSumAccumulator(numGroups, icv, outputName, resultType);
        }
        if (vector instanceof LongColumnVector lcv) {
            return new LongSumAccumulator(numGroups, lcv, outputName);
        }
        if (vector instanceof FloatColumnVector fcv) {
            return new FloatSumAccumulator(numGroups, fcv, outputName);
        }
        return new BigDecimalSumAccumulator(numGroups, vector, outputName, resultType);
    }

    public static GroupAccumulator createMeanAccumulator(
            int numGroups, ColumnVector vector, String outputName, Class<?> resultType) {
        if (vector == null) {
            return new NullAccumulator(outputName, resultType != null ? resultType : Double.class);
        }
        if (resultType == BigDecimal.class || vector.getType() == BigDecimal.class) {
            return new BigDecimalMeanAccumulator(numGroups, vector, outputName, resultType);
        }
        return new DoubleMeanAccumulator(numGroups, vector, outputName);
    }

    public static GroupAccumulator createMinAccumulator(
            int numGroups, ColumnVector vector, String outputName, Class<?> resultType) {
        if (vector == null) {
            return new NullAccumulator(outputName, resultType != null ? resultType : Object.class);
        }
        if (vector instanceof DoubleColumnVector dcv) {
            return new DoubleMinAccumulator(numGroups, dcv, outputName);
        }
        if (vector instanceof IntColumnVector icv) {
            return new IntMinAccumulator(numGroups, icv, outputName);
        }
        if (vector instanceof LongColumnVector lcv) {
            return new LongMinAccumulator(numGroups, lcv, outputName);
        }
        if (vector instanceof FloatColumnVector fcv) {
            return new FloatMinAccumulator(numGroups, fcv, outputName);
        }
        return new ComparableMinAccumulator(numGroups, vector, outputName, resultType);
    }

    public static GroupAccumulator createMaxAccumulator(
            int numGroups, ColumnVector vector, String outputName, Class<?> resultType) {
        if (vector == null) {
            return new NullAccumulator(outputName, resultType != null ? resultType : Object.class);
        }
        if (vector instanceof DoubleColumnVector dcv) {
            return new DoubleMaxAccumulator(numGroups, dcv, outputName);
        }
        if (vector instanceof IntColumnVector icv) {
            return new IntMaxAccumulator(numGroups, icv, outputName);
        }
        if (vector instanceof LongColumnVector lcv) {
            return new LongMaxAccumulator(numGroups, lcv, outputName);
        }
        if (vector instanceof FloatColumnVector fcv) {
            return new FloatMaxAccumulator(numGroups, fcv, outputName);
        }
        return new ComparableMaxAccumulator(numGroups, vector, outputName, resultType);
    }

    public static GroupAccumulator createListAggAccumulator(
            int numGroups, ColumnVector vector, String separator, String outputName) {
        return new ListAggAccumulator(numGroups, vector, separator, outputName);
    }

    // --- Concrete Accumulator Classes ---

    private static class NullAccumulator implements GroupAccumulator {
        private final String outputName;
        private final Class<?> type;

        NullAccumulator(String outputName, Class<?> type) {
            this.outputName = outputName;
            this.type = type;
        }

        @Override public void accumulate(int groupId, int row) {}
        @Override public void merge(GroupAccumulator other) {}

        @Override
        public ColumnVector finish(int numGroups) {
            ColumnVector out = ColumnVector.create(outputName, type, Math.max(16, numGroups), true);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                out.setNull(i);
            }
            return out;
        }
    }

    public static class CountAccumulator implements GroupAccumulator {
        private final int[] counts;
        private final String outputColumnName;

        public CountAccumulator(int numGroups, String outputColumnName) {
            this.counts = new int[numGroups];
            this.outputColumnName = outputColumnName;
        }

        @Override
        public void accumulate(int groupId, int row) {
            counts[groupId]++;
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof CountAccumulator o) {
                for (int i = 0; i < counts.length; i++) {
                    counts[i] += o.counts[i];
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            IntColumnVector out = new IntColumnVector(outputColumnName, Math.max(16, numGroups), false);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                out.setInt(i, counts[i]);
            }
            return out;
        }
    }

    public static class NonNullCountAccumulator implements GroupAccumulator {
        private final int[] counts;
        private final ColumnVector vec;
        private final String outputColumnName;

        public NonNullCountAccumulator(int numGroups, ColumnVector vec, String outputColumnName) {
            this.counts = new int[numGroups];
            this.vec = vec;
            this.outputColumnName = outputColumnName;
        }

        @Override
        public void accumulate(int groupId, int row) {
            if (vec != null && !vec.isNull(row)) {
                if (vec instanceof IntColumnVector || vec instanceof LongColumnVector
                        || vec instanceof DoubleColumnVector || vec instanceof FloatColumnVector
                        || vec instanceof com.cupoftea.table.vector.BooleanColumnVector) {
                    counts[groupId]++;
                } else {
                    Object val = vec.getObject(row);
                    if (val != null && !NA.isNA(val)) {
                        counts[groupId]++;
                    }
                }
            }
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof NonNullCountAccumulator o) {
                for (int i = 0; i < counts.length; i++) {
                    counts[i] += o.counts[i];
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            IntColumnVector out = new IntColumnVector(outputColumnName, Math.max(16, numGroups), false);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                out.setInt(i, counts[i]);
            }
            return out;
        }
    }

    public static class DoubleSumAccumulator implements GroupAccumulator {
        private final double[] sums;
        private final int[] counts;
        private final DoubleColumnVector vec;
        private final String outputName;

        public DoubleSumAccumulator(int numGroups, DoubleColumnVector vec, String outputName) {
            this.sums = new double[numGroups];
            this.counts = new int[numGroups];
            this.vec = vec;
            this.outputName = outputName;
        }

        @Override
        public void accumulate(int groupId, int row) {
            if (!vec.isNull(row)) {
                double val = vec.getDouble(row);
                if (!Double.isNaN(val)) {
                    sums[groupId] += val;
                    counts[groupId]++;
                }
            }
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof DoubleSumAccumulator o) {
                for (int i = 0; i < sums.length; i++) {
                    sums[i] += o.sums[i];
                    counts[i] += o.counts[i];
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            DoubleColumnVector out = new DoubleColumnVector(outputName, Math.max(16, numGroups), true);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                if (counts[i] == 0) {
                    out.setDouble(i, Double.NaN);
                    out.setNull(i);
                } else {
                    out.setDouble(i, sums[i]);
                }
            }
            return out;
        }
    }

    public static class IntSumAccumulator implements GroupAccumulator {
        private final long[] sums;
        private final int[] counts;
        private final IntColumnVector vec;
        private final String outputName;
        private final Class<?> targetType;

        public IntSumAccumulator(int numGroups, IntColumnVector vec, String outputName, Class<?> targetType) {
            this.sums = new long[numGroups];
            this.counts = new int[numGroups];
            this.vec = vec;
            this.outputName = outputName;
            this.targetType = targetType;
        }

        @Override
        public void accumulate(int groupId, int row) {
            if (!vec.isNull(row)) {
                sums[groupId] += vec.getInt(row);
                counts[groupId]++;
            }
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof IntSumAccumulator o) {
                for (int i = 0; i < sums.length; i++) {
                    sums[i] += o.sums[i];
                    counts[i] += o.counts[i];
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            if (targetType == Long.class || targetType == long.class) {
                LongColumnVector out = new LongColumnVector(outputName, Math.max(16, numGroups), true);
                out.setSizeUnchecked(numGroups);
                for (int i = 0; i < numGroups; i++) {
                    if (counts[i] == 0) out.setNull(i);
                    else out.setLong(i, sums[i]);
                }
                return out;
            } else if (targetType == Double.class || targetType == double.class) {
                DoubleColumnVector out = new DoubleColumnVector(outputName, Math.max(16, numGroups), true);
                out.setSizeUnchecked(numGroups);
                for (int i = 0; i < numGroups; i++) {
                    if (counts[i] == 0) {
                        out.setDouble(i, Double.NaN);
                        out.setNull(i);
                    } else {
                        out.setDouble(i, sums[i]);
                    }
                }
                return out;
            } else {
                IntColumnVector out = new IntColumnVector(outputName, Math.max(16, numGroups), true);
                out.setSizeUnchecked(numGroups);
                for (int i = 0; i < numGroups; i++) {
                    if (counts[i] == 0) out.setNull(i);
                    else out.setInt(i, (int) sums[i]);
                }
                return out;
            }
        }
    }

    public static class LongSumAccumulator implements GroupAccumulator {
        private final long[] sums;
        private final int[] counts;
        private final LongColumnVector vec;
        private final String outputName;

        public LongSumAccumulator(int numGroups, LongColumnVector vec, String outputName) {
            this.sums = new long[numGroups];
            this.counts = new int[numGroups];
            this.vec = vec;
            this.outputName = outputName;
        }

        @Override
        public void accumulate(int groupId, int row) {
            if (!vec.isNull(row)) {
                sums[groupId] += vec.getLong(row);
                counts[groupId]++;
            }
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof LongSumAccumulator o) {
                for (int i = 0; i < sums.length; i++) {
                    sums[i] += o.sums[i];
                    counts[i] += o.counts[i];
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            LongColumnVector out = new LongColumnVector(outputName, Math.max(16, numGroups), true);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                if (counts[i] == 0) out.setNull(i);
                else out.setLong(i, sums[i]);
            }
            return out;
        }
    }

    public static class FloatSumAccumulator implements GroupAccumulator {
        private final double[] sums;
        private final int[] counts;
        private final FloatColumnVector vec;
        private final String outputName;

        public FloatSumAccumulator(int numGroups, FloatColumnVector vec, String outputName) {
            this.sums = new double[numGroups];
            this.counts = new int[numGroups];
            this.vec = vec;
            this.outputName = outputName;
        }

        @Override
        public void accumulate(int groupId, int row) {
            if (!vec.isNull(row)) {
                sums[groupId] += vec.getFloat(row);
                counts[groupId]++;
            }
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof FloatSumAccumulator o) {
                for (int i = 0; i < sums.length; i++) {
                    sums[i] += o.sums[i];
                    counts[i] += o.counts[i];
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            FloatColumnVector out = new FloatColumnVector(outputName, Math.max(16, numGroups), true);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                if (counts[i] == 0) out.setNull(i);
                else out.setFloat(i, (float) sums[i]);
            }
            return out;
        }
    }

    public static class BigDecimalSumAccumulator implements GroupAccumulator {
        private final BigDecimal[] sums;
        private final boolean[] hasVal;
        private final ColumnVector vec;
        private final String outputName;
        private final Class<?> targetType;

        public BigDecimalSumAccumulator(int numGroups, ColumnVector vec, String outputName, Class<?> targetType) {
            this.sums = new BigDecimal[numGroups];
            this.hasVal = new boolean[numGroups];
            this.vec = vec;
            this.outputName = outputName;
            this.targetType = targetType;
        }

        @Override
        public void accumulate(int groupId, int row) {
            if (vec == null || vec.isNull(row)) return;
            Object obj = vec.getObject(row);
            BigDecimal bd = Aggregations.toBigDecimal(obj);
            if (bd != null) {
                sums[groupId] = (sums[groupId] != null) ? sums[groupId].add(bd) : bd;
                hasVal[groupId] = true;
            }
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof BigDecimalSumAccumulator o) {
                for (int i = 0; i < sums.length; i++) {
                    if (o.hasVal[i] && o.sums[i] != null) {
                        sums[i] = (sums[i] != null) ? sums[i].add(o.sums[i]) : o.sums[i];
                        hasVal[i] = true;
                    }
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            Class<?> resType = targetType != null ? targetType : BigDecimal.class;
            ColumnVector out = ColumnVector.create(outputName, resType, Math.max(16, numGroups), true);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                if (!hasVal[i] || sums[i] == null) {
                    out.setNull(i);
                } else {
                    if (resType == BigDecimal.class) {
                        out.setObject(i, sums[i]);
                    } else if (resType == Double.class || resType == double.class) {
                        out.setDouble(i, sums[i].doubleValue());
                    } else if (resType == Integer.class || resType == int.class) {
                        out.setInt(i, sums[i].intValue());
                    } else if (resType == Long.class || resType == long.class) {
                        out.setLong(i, sums[i].longValue());
                    } else if (resType == Float.class || resType == float.class) {
                        out.setFloat(i, sums[i].floatValue());
                    } else {
                        out.setObject(i, sums[i]);
                    }
                }
            }
            return out;
        }
    }

    public static class DoubleMeanAccumulator implements GroupAccumulator {
        private final double[] sums;
        private final long[] counts;
        private final ColumnVector vec;
        private final String outputName;

        public DoubleMeanAccumulator(int numGroups, ColumnVector vec, String outputName) {
            this.sums = new double[numGroups];
            this.counts = new long[numGroups];
            this.vec = vec;
            this.outputName = outputName;
        }

        @Override
        public void accumulate(int groupId, int row) {
            if (vec == null || vec.isNull(row)) return;
            if (vec instanceof DoubleColumnVector dcv) {
                double val = dcv.getDouble(row);
                if (!Double.isNaN(val)) {
                    sums[groupId] += val;
                    counts[groupId]++;
                }
            } else if (vec instanceof IntColumnVector icv) {
                sums[groupId] += icv.getInt(row);
                counts[groupId]++;
            } else if (vec instanceof LongColumnVector lcv) {
                sums[groupId] += lcv.getLong(row);
                counts[groupId]++;
            } else if (vec instanceof FloatColumnVector fcv) {
                sums[groupId] += fcv.getFloat(row);
                counts[groupId]++;
            } else {
                Object obj = vec.getObject(row);
                if (obj != null && !NA.isNA(obj)) {
                    BigDecimal bd = Aggregations.toBigDecimal(obj);
                    if (bd != null) {
                        sums[groupId] += bd.doubleValue();
                        counts[groupId]++;
                    }
                }
            }
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof DoubleMeanAccumulator o) {
                for (int i = 0; i < sums.length; i++) {
                    sums[i] += o.sums[i];
                    counts[i] += o.counts[i];
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            DoubleColumnVector out = new DoubleColumnVector(outputName, Math.max(16, numGroups), true);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                if (counts[i] == 0) {
                    out.setDouble(i, Double.NaN);
                    out.setNull(i);
                } else {
                    out.setDouble(i, sums[i] / (double) counts[i]);
                }
            }
            return out;
        }
    }

    public static class BigDecimalMeanAccumulator implements GroupAccumulator {
        private final BigDecimal[] sums;
        private final long[] counts;
        private final ColumnVector vec;
        private final String outputName;
        private final Class<?> targetType;

        public BigDecimalMeanAccumulator(int numGroups, ColumnVector vec, String outputName, Class<?> targetType) {
            this.sums = new BigDecimal[numGroups];
            this.counts = new long[numGroups];
            this.vec = vec;
            this.outputName = outputName;
            this.targetType = targetType;
        }

        @Override
        public void accumulate(int groupId, int row) {
            if (vec == null || vec.isNull(row)) return;
            Object obj = vec.getObject(row);
            BigDecimal bd = Aggregations.toBigDecimal(obj);
            if (bd != null) {
                sums[groupId] = (sums[groupId] != null) ? sums[groupId].add(bd) : bd;
                counts[groupId]++;
            }
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof BigDecimalMeanAccumulator o) {
                for (int i = 0; i < sums.length; i++) {
                    if (o.counts[i] > 0 && o.sums[i] != null) {
                        sums[i] = (sums[i] != null) ? sums[i].add(o.sums[i]) : o.sums[i];
                        counts[i] += o.counts[i];
                    }
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            Class<?> resType = targetType != null ? targetType : BigDecimal.class;
            ColumnVector out = ColumnVector.create(outputName, resType, Math.max(16, numGroups), true);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                if (counts[i] == 0 || sums[i] == null) {
                    out.setNull(i);
                } else {
                    BigDecimal avg = sums[i].divide(BigDecimal.valueOf(counts[i]), MathContext.DECIMAL128);
                    if (resType == BigDecimal.class) {
                        out.setObject(i, avg);
                    } else {
                        out.setDouble(i, avg.doubleValue());
                    }
                }
            }
            return out;
        }
    }

    public static class DoubleMinAccumulator implements GroupAccumulator {
        private final double[] mins;
        private final boolean[] hasVal;
        private final DoubleColumnVector vec;
        private final String outputName;

        public DoubleMinAccumulator(int numGroups, DoubleColumnVector vec, String outputName) {
            this.mins = new double[numGroups];
            this.hasVal = new boolean[numGroups];
            this.vec = vec;
            this.outputName = outputName;
        }

        @Override
        public void accumulate(int groupId, int row) {
            if (!vec.isNull(row)) {
                double val = vec.getDouble(row);
                if (!Double.isNaN(val)) {
                    if (!hasVal[groupId] || val < mins[groupId]) {
                        mins[groupId] = val;
                        hasVal[groupId] = true;
                    }
                }
            }
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof DoubleMinAccumulator o) {
                for (int i = 0; i < mins.length; i++) {
                    if (o.hasVal[i]) {
                        if (!hasVal[i] || o.mins[i] < mins[i]) {
                            mins[i] = o.mins[i];
                            hasVal[i] = true;
                        }
                    }
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            DoubleColumnVector out = new DoubleColumnVector(outputName, Math.max(16, numGroups), true);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                if (!hasVal[i]) {
                    out.setDouble(i, Double.NaN);
                    out.setNull(i);
                } else {
                    out.setDouble(i, mins[i]);
                }
            }
            return out;
        }
    }

    public static class IntMinAccumulator implements GroupAccumulator {
        private final int[] mins;
        private final boolean[] hasVal;
        private final IntColumnVector vec;
        private final String outputName;

        public IntMinAccumulator(int numGroups, IntColumnVector vec, String outputName) {
            this.mins = new int[numGroups];
            this.hasVal = new boolean[numGroups];
            this.vec = vec;
            this.outputName = outputName;
        }

        @Override
        public void accumulate(int groupId, int row) {
            if (!vec.isNull(row)) {
                int val = vec.getInt(row);
                if (!hasVal[groupId] || val < mins[groupId]) {
                    mins[groupId] = val;
                    hasVal[groupId] = true;
                }
            }
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof IntMinAccumulator o) {
                for (int i = 0; i < mins.length; i++) {
                    if (o.hasVal[i]) {
                        if (!hasVal[i] || o.mins[i] < mins[i]) {
                            mins[i] = o.mins[i];
                            hasVal[i] = true;
                        }
                    }
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            IntColumnVector out = new IntColumnVector(outputName, Math.max(16, numGroups), true);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                if (!hasVal[i]) {
                    out.setNull(i);
                } else {
                    out.setInt(i, mins[i]);
                }
            }
            return out;
        }
    }

    public static class LongMinAccumulator implements GroupAccumulator {
        private final long[] mins;
        private final boolean[] hasVal;
        private final LongColumnVector vec;
        private final String outputName;

        public LongMinAccumulator(int numGroups, LongColumnVector vec, String outputName) {
            this.mins = new long[numGroups];
            this.hasVal = new boolean[numGroups];
            this.vec = vec;
            this.outputName = outputName;
        }

        @Override
        public void accumulate(int groupId, int row) {
            if (!vec.isNull(row)) {
                long val = vec.getLong(row);
                if (!hasVal[groupId] || val < mins[groupId]) {
                    mins[groupId] = val;
                    hasVal[groupId] = true;
                }
            }
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof LongMinAccumulator o) {
                for (int i = 0; i < mins.length; i++) {
                    if (o.hasVal[i]) {
                        if (!hasVal[i] || o.mins[i] < mins[i]) {
                            mins[i] = o.mins[i];
                            hasVal[i] = true;
                        }
                    }
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            LongColumnVector out = new LongColumnVector(outputName, Math.max(16, numGroups), true);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                if (!hasVal[i]) {
                    out.setNull(i);
                } else {
                    out.setLong(i, mins[i]);
                }
            }
            return out;
        }
    }

    public static class FloatMinAccumulator implements GroupAccumulator {
        private final float[] mins;
        private final boolean[] hasVal;
        private final FloatColumnVector vec;
        private final String outputName;

        public FloatMinAccumulator(int numGroups, FloatColumnVector vec, String outputName) {
            this.mins = new float[numGroups];
            this.hasVal = new boolean[numGroups];
            this.vec = vec;
            this.outputName = outputName;
        }

        @Override
        public void accumulate(int groupId, int row) {
            if (!vec.isNull(row)) {
                float val = vec.getFloat(row);
                if (!Float.isNaN(val)) {
                    if (!hasVal[groupId] || val < mins[groupId]) {
                        mins[groupId] = val;
                        hasVal[groupId] = true;
                    }
                }
            }
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof FloatMinAccumulator o) {
                for (int i = 0; i < mins.length; i++) {
                    if (o.hasVal[i]) {
                        if (!hasVal[i] || o.mins[i] < mins[i]) {
                            mins[i] = o.mins[i];
                            hasVal[i] = true;
                        }
                    }
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            FloatColumnVector out = new FloatColumnVector(outputName, Math.max(16, numGroups), true);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                if (!hasVal[i]) {
                    out.setNull(i);
                } else {
                    out.setFloat(i, mins[i]);
                }
            }
            return out;
        }
    }

    public static class ComparableMinAccumulator implements GroupAccumulator {
        private final Object[] mins;
        private final boolean[] hasVal;
        private final ColumnVector vec;
        private final String outputName;
        private final Class<?> resultType;

        public ComparableMinAccumulator(int numGroups, ColumnVector vec, String outputName, Class<?> resultType) {
            this.mins = new Object[numGroups];
            this.hasVal = new boolean[numGroups];
            this.vec = vec;
            this.outputName = outputName;
            this.resultType = resultType;
        }

        @Override
        public void accumulate(int groupId, int row) {
            if (vec == null || vec.isNull(row)) return;
            Object val = vec.getObject(row);
            if (val == null || NA.isNA(val)) return;

            if (!hasVal[groupId]) {
                mins[groupId] = val;
                hasVal[groupId] = true;
            } else {
                Object current = mins[groupId];
                if (compare(val, current) < 0) {
                    mins[groupId] = val;
                }
            }
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof ComparableMinAccumulator o) {
                for (int i = 0; i < mins.length; i++) {
                    if (o.hasVal[i]) {
                        if (!hasVal[i] || compare(o.mins[i], mins[i]) < 0) {
                            mins[i] = o.mins[i];
                            hasVal[i] = true;
                        }
                    }
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            ColumnVector out = ColumnVector.create(outputName, resultType != null ? resultType : Object.class, Math.max(16, numGroups), true);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                if (!hasVal[i]) {
                    out.setNull(i);
                } else {
                    out.setObject(i, mins[i]);
                }
            }
            return out;
        }
    }

    public static class DoubleMaxAccumulator implements GroupAccumulator {
        private final double[] maxs;
        private final boolean[] hasVal;
        private final DoubleColumnVector vec;
        private final String outputName;

        public DoubleMaxAccumulator(int numGroups, DoubleColumnVector vec, String outputName) {
            this.maxs = new double[numGroups];
            this.hasVal = new boolean[numGroups];
            this.vec = vec;
            this.outputName = outputName;
        }

        @Override
        public void accumulate(int groupId, int row) {
            if (!vec.isNull(row)) {
                double val = vec.getDouble(row);
                if (!Double.isNaN(val)) {
                    if (!hasVal[groupId] || val > maxs[groupId]) {
                        maxs[groupId] = val;
                        hasVal[groupId] = true;
                    }
                }
            }
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof DoubleMaxAccumulator o) {
                for (int i = 0; i < maxs.length; i++) {
                    if (o.hasVal[i]) {
                        if (!hasVal[i] || o.maxs[i] > maxs[i]) {
                            maxs[i] = o.maxs[i];
                            hasVal[i] = true;
                        }
                    }
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            DoubleColumnVector out = new DoubleColumnVector(outputName, Math.max(16, numGroups), true);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                if (!hasVal[i]) {
                    out.setDouble(i, Double.NaN);
                    out.setNull(i);
                } else {
                    out.setDouble(i, maxs[i]);
                }
            }
            return out;
        }
    }

    public static class IntMaxAccumulator implements GroupAccumulator {
        private final int[] maxs;
        private final boolean[] hasVal;
        private final IntColumnVector vec;
        private final String outputName;

        public IntMaxAccumulator(int numGroups, IntColumnVector vec, String outputName) {
            this.maxs = new int[numGroups];
            this.hasVal = new boolean[numGroups];
            this.vec = vec;
            this.outputName = outputName;
        }

        @Override
        public void accumulate(int groupId, int row) {
            if (!vec.isNull(row)) {
                int val = vec.getInt(row);
                if (!hasVal[groupId] || val > maxs[groupId]) {
                    maxs[groupId] = val;
                    hasVal[groupId] = true;
                }
            }
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof IntMaxAccumulator o) {
                for (int i = 0; i < maxs.length; i++) {
                    if (o.hasVal[i]) {
                        if (!hasVal[i] || o.maxs[i] > maxs[i]) {
                            maxs[i] = o.maxs[i];
                            hasVal[i] = true;
                        }
                    }
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            IntColumnVector out = new IntColumnVector(outputName, Math.max(16, numGroups), true);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                if (!hasVal[i]) {
                    out.setNull(i);
                } else {
                    out.setInt(i, maxs[i]);
                }
            }
            return out;
        }
    }

    public static class LongMaxAccumulator implements GroupAccumulator {
        private final long[] maxs;
        private final boolean[] hasVal;
        private final LongColumnVector vec;
        private final String outputName;

        public LongMaxAccumulator(int numGroups, LongColumnVector vec, String outputName) {
            this.maxs = new long[numGroups];
            this.hasVal = new boolean[numGroups];
            this.vec = vec;
            this.outputName = outputName;
        }

        @Override
        public void accumulate(int groupId, int row) {
            if (!vec.isNull(row)) {
                long val = vec.getLong(row);
                if (!hasVal[groupId] || val > maxs[groupId]) {
                    maxs[groupId] = val;
                    hasVal[groupId] = true;
                }
            }
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof LongMaxAccumulator o) {
                for (int i = 0; i < maxs.length; i++) {
                    if (o.hasVal[i]) {
                        if (!hasVal[i] || o.maxs[i] > maxs[i]) {
                            maxs[i] = o.maxs[i];
                            hasVal[i] = true;
                        }
                    }
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            LongColumnVector out = new LongColumnVector(outputName, Math.max(16, numGroups), true);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                if (!hasVal[i]) {
                    out.setNull(i);
                } else {
                    out.setLong(i, maxs[i]);
                }
            }
            return out;
        }
    }

    public static class FloatMaxAccumulator implements GroupAccumulator {
        private final float[] maxs;
        private final boolean[] hasVal;
        private final FloatColumnVector vec;
        private final String outputName;

        public FloatMaxAccumulator(int numGroups, FloatColumnVector vec, String outputName) {
            this.maxs = new float[numGroups];
            this.hasVal = new boolean[numGroups];
            this.vec = vec;
            this.outputName = outputName;
        }

        @Override
        public void accumulate(int groupId, int row) {
            if (!vec.isNull(row)) {
                float val = vec.getFloat(row);
                if (!Float.isNaN(val)) {
                    if (!hasVal[groupId] || val > maxs[groupId]) {
                        maxs[groupId] = val;
                        hasVal[groupId] = true;
                    }
                }
            }
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof FloatMaxAccumulator o) {
                for (int i = 0; i < maxs.length; i++) {
                    if (o.hasVal[i]) {
                        if (!hasVal[i] || o.maxs[i] > maxs[i]) {
                            maxs[i] = o.maxs[i];
                            hasVal[i] = true;
                        }
                    }
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            FloatColumnVector out = new FloatColumnVector(outputName, Math.max(16, numGroups), true);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                if (!hasVal[i]) {
                    out.setNull(i);
                } else {
                    out.setFloat(i, maxs[i]);
                }
            }
            return out;
        }
    }

    public static class ComparableMaxAccumulator implements GroupAccumulator {
        private final Object[] maxs;
        private final boolean[] hasVal;
        private final ColumnVector vec;
        private final String outputName;
        private final Class<?> resultType;

        public ComparableMaxAccumulator(int numGroups, ColumnVector vec, String outputName, Class<?> resultType) {
            this.maxs = new Object[numGroups];
            this.hasVal = new boolean[numGroups];
            this.vec = vec;
            this.outputName = outputName;
            this.resultType = resultType;
        }

        @Override
        public void accumulate(int groupId, int row) {
            if (vec == null || vec.isNull(row)) return;
            Object val = vec.getObject(row);
            if (val == null || NA.isNA(val)) return;

            if (!hasVal[groupId]) {
                maxs[groupId] = val;
                hasVal[groupId] = true;
            } else {
                Object current = maxs[groupId];
                if (compare(val, current) > 0) {
                    maxs[groupId] = val;
                }
            }
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof ComparableMaxAccumulator o) {
                for (int i = 0; i < maxs.length; i++) {
                    if (o.hasVal[i]) {
                        if (!hasVal[i] || compare(o.maxs[i], maxs[i]) > 0) {
                            maxs[i] = o.maxs[i];
                            hasVal[i] = true;
                        }
                    }
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            ColumnVector out = ColumnVector.create(outputName, resultType != null ? resultType : Object.class, Math.max(16, numGroups), true);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                if (!hasVal[i]) {
                    out.setNull(i);
                } else {
                    out.setObject(i, maxs[i]);
                }
            }
            return out;
        }
    }

    public static class ListAggAccumulator implements GroupAccumulator {
        private final StringBuilder[] builders;
        private final boolean[] hasVal;
        private final ColumnVector vec;
        private final String separator;
        private final String outputName;

        public ListAggAccumulator(int numGroups, ColumnVector vec, String separator, String outputName) {
            this.builders = new StringBuilder[numGroups];
            this.hasVal = new boolean[numGroups];
            this.vec = vec;
            this.separator = separator;
            this.outputName = outputName;
        }

        @Override
        public void accumulate(int groupId, int row) {
            if (vec != null && !vec.isNull(row)) {
                Object obj = vec.getObject(row);
                if (obj != null && !NA.isNA(obj)) {
                    if (builders[groupId] == null) {
                        builders[groupId] = new StringBuilder();
                    }
                    if (hasVal[groupId]) {
                        builders[groupId].append(separator);
                    }
                    builders[groupId].append(obj);
                    hasVal[groupId] = true;
                }
            }
        }

        @Override
        public void merge(GroupAccumulator other) {
            if (other instanceof ListAggAccumulator o) {
                for (int i = 0; i < builders.length; i++) {
                    if (o.hasVal[i] && o.builders[i] != null) {
                        if (builders[i] == null) {
                            builders[i] = new StringBuilder();
                        }
                        if (hasVal[i]) {
                            builders[i].append(separator);
                        }
                        builders[i].append(o.builders[i]);
                        hasVal[i] = true;
                    }
                }
            }
        }

        @Override
        public ColumnVector finish(int numGroups) {
            StringColumnVector out = new StringColumnVector(outputName, Math.max(16, numGroups), false);
            out.setSizeUnchecked(numGroups);
            for (int i = 0; i < numGroups; i++) {
                out.setString(i, builders[i] != null ? builders[i].toString() : "");
            }
            return out;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            BigDecimal bda = Aggregations.toBigDecimal(a);
            BigDecimal bdb = Aggregations.toBigDecimal(b);
            if (bda != null && bdb != null) {
                return bda.compareTo(bdb);
            }
        }
        if (a instanceof Comparable && b instanceof Comparable) {
            return ((Comparable) a).compareTo(b);
        }
        return 0;
    }
}
