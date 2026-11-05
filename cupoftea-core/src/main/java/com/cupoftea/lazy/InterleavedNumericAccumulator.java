package com.cupoftea.lazy;

import java.util.Arrays;
import java.util.BitSet;

import com.cupoftea.table.Aggregation;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;

/**
 * Contiguous cache-line interleaved numeric accumulator for grouped aggregations.
 *
 * <p>Instead of scattering metrics across independent accumulator objects and separate heap arrays
 * (e.g. {@code mins[gid]}, {@code maxs[gid]}, {@code sums[gid]}, {@code counts[gid]}), this structure
 * lays out statistics for each group contiguously in an interleaved primitive {@code double[]} array
 * (Array-of-Structs pattern):</p>
 *
 * <pre>
 * Group 0: [min, max, sum, count] (32 bytes - 1/2 of a 64-byte L1 cache line)
 * Group 1: [min, max, sum, count]
 * ...
 * </pre>
 *
 * <p>In hot aggregation loops (such as {@code accumulate1Brc}, {@code accumulate1BrcScaled}, and
 * {@code accumulateDouble}), accessing and updating all metrics for a group touches a single
 * contiguous memory block, eliminating multi-array cache misses and virtual method dispatch.</p>
 */
public final class InterleavedNumericAccumulator implements CompactRunningGroupBy.RunningAccumulator {

    public static final int OFF_MIN = 0;
    public static final int OFF_MAX = 1;
    public static final int OFF_SUM = 2;
    public static final int OFF_COUNT = 3;
    public static final int STATS_SIZE = 4; // 4 doubles = 32 bytes

    private final Aggregation aggType;
    private final Class<?> resultType;
    private final InterleavedNumericAccumulator sharedParent;
    private boolean hasSeparateSum = false;
    private boolean hasSeparateCount = false;

    private double[] stats;
    private boolean[] hasValues;

    /**
     * Creates a root/primary interleaved stats accumulator for a group of metrics.
     */
    public InterleavedNumericAccumulator(Aggregation aggType, Class<?> resultType, int initialCapacity) {
        this(aggType, resultType, initialCapacity, null);
    }

    private InterleavedNumericAccumulator(Aggregation aggType, Class<?> resultType, int initialCapacity,
                                         InterleavedNumericAccumulator sharedParent) {
        this.aggType = aggType;
        this.resultType = resultType;
        this.sharedParent = sharedParent;

        if (sharedParent == null) {
            int cap = Math.max(initialCapacity, 16);
            this.stats = new double[cap * STATS_SIZE];
            this.hasValues = new boolean[cap];
            initStats(0, cap);
        } else {
            this.stats = null;
            this.hasValues = null;
        }
    }

    private void initStats(int startGroup, int endGroup) {
        for (int g = startGroup; g < endGroup; g++) {
            int base = g * STATS_SIZE;
            stats[base + OFF_MIN] = Double.POSITIVE_INFINITY;
            stats[base + OFF_MAX] = Double.NEGATIVE_INFINITY;
            stats[base + OFF_SUM] = 0.0;
            stats[base + OFF_COUNT] = 0.0;
        }
    }

    /**
     * Creates a child view sharing the same underlying contiguous storage.
     */
    public InterleavedNumericAccumulator createSharedView(Aggregation aggType, Class<?> resultType) {
        InterleavedNumericAccumulator root = (this.sharedParent == null) ? this : this.sharedParent;
        return new InterleavedNumericAccumulator(aggType, resultType, 0, root);
    }

    public void setHasSeparateSum(boolean hasSeparateSum) {
        this.hasSeparateSum = hasSeparateSum;
    }

    public void setHasSeparateCount(boolean hasSeparateCount) {
        this.hasSeparateCount = hasSeparateCount;
    }

    public double[] getStatsArray() {
        return (sharedParent != null) ? sharedParent.stats : this.stats;
    }

    public boolean[] getHasValuesArray() {
        return (sharedParent != null) ? sharedParent.hasValues : this.hasValues;
    }

    public Aggregation getAggregationType() {
        return aggType;
    }

    @Override
    public void ensureCapacity(int minCapacity) {
        if (sharedParent != null) {
            sharedParent.ensureCapacity(minCapacity);
            return;
        }
        int curCap = hasValues.length;
        if (minCapacity > curCap) {
            int newCap = Math.max(curCap * 2, minCapacity);
            stats = Arrays.copyOf(stats, newCap * STATS_SIZE);
            initStats(curCap, newCap);
            hasValues = Arrays.copyOf(hasValues, newCap);
        }
    }

    /**
     * Fast in-register contiguous update for all active numeric statistics of a group.
     */
    public void updateAll(int gid, double val) {
        double[] s = (sharedParent != null) ? sharedParent.stats : this.stats;
        boolean[] hv = (sharedParent != null) ? sharedParent.hasValues : this.hasValues;

        int base = gid * STATS_SIZE;
        double curMin = s[base + OFF_MIN];
        double curMax = s[base + OFF_MAX];

        if (val < curMin) s[base + OFF_MIN] = val;
        if (val > curMax) s[base + OFF_MAX] = val;
        s[base + OFF_SUM] += val;
        s[base + OFF_COUNT] += 1.0;
        hv[gid] = true;
    }

    /**
     * Fast in-register contiguous update with fixed-point decimal values.
     */
    public void updateAllScaled(int gid, long scaledVal, int scale) {
        double divisor = switch (scale) {
            case 1 -> 10.0;
            case 2 -> 100.0;
            case 3 -> 1000.0;
            case 4 -> 10000.0;
            default -> Math.pow(10, scale);
        };
        updateAll(gid, (double) scaledVal / divisor);
    }

    @Override
    public void accumulate(int gid, ColumnVector vector, int row) {
        if (vector != null && !vector.isNull(row)) {
            accumulateDouble(gid, vector.getDouble(row));
        }
    }

    @Override
    public void accumulateDouble(int gid, double value) {
        double[] s = (sharedParent != null) ? sharedParent.stats : this.stats;
        boolean[] hv = (sharedParent != null) ? sharedParent.hasValues : this.hasValues;

        int base = gid * STATS_SIZE;
        switch (aggType) {
            case MIN, MINIMUM -> {
                if (value < s[base + OFF_MIN]) s[base + OFF_MIN] = value;
                hv[gid] = true;
            }
            case MAX, MAXIMUM -> {
                if (value > s[base + OFF_MAX]) s[base + OFF_MAX] = value;
                hv[gid] = true;
            }
            case SUM -> {
                s[base + OFF_SUM] += value;
                hv[gid] = true;
            }
            case MEAN, AVG, AVERAGE -> {
                // If this shared interleaved accumulator also tracks COUNT or SUM as separate metrics in the query,
                // do not double-increment sum or count here when accumulators are iterated individually.
                // However, if neither COUNT nor SUM is present, MEAN must maintain sum and count.
                if (!hasSeparateSum) {
                    s[base + OFF_SUM] += value;
                }
                if (!hasSeparateCount) {
                    s[base + OFF_COUNT] += 1.0;
                }
                hv[gid] = true;
            }
            case COUNT -> {
                s[base + OFF_COUNT] += 1.0;
            }
            default -> {
                s[base + OFF_SUM] += value;
                hv[gid] = true;
            }
        }
    }

    @Override
    public void accumulateLong(int gid, long value) {
        accumulateDouble(gid, (double) value);
    }

    @Override
    public void accumulateInt(int gid, int value) {
        accumulateDouble(gid, (double) value);
    }

    @Override
    public void accumulateScaled(int gid, long scaledValue, int scale) {
        double divisor = switch (scale) {
            case 1 -> 10.0;
            case 2 -> 100.0;
            case 3 -> 1000.0;
            case 4 -> 10000.0;
            default -> Math.pow(10, scale);
        };
        accumulateDouble(gid, (double) scaledValue / divisor);
    }

    @Override
    public void merge(CompactRunningGroupBy.RunningAccumulator other, int thisGid, int otherGid) {
        if (!(other instanceof InterleavedNumericAccumulator o)) {
            return;
        }

        // Only merge once per root structure
        if (this.sharedParent != null) {
            return;
        }

        double[] thisStats = this.stats;
        boolean[] thisHv = this.hasValues;

        double[] oStats = (o.sharedParent != null) ? o.sharedParent.stats : o.stats;
        boolean[] oHv = (o.sharedParent != null) ? o.sharedParent.hasValues : o.hasValues;

        if (oHv[otherGid]) {
            int thisBase = thisGid * STATS_SIZE;
            int oBase = otherGid * STATS_SIZE;

            if (!thisHv[thisGid] || oStats[oBase + OFF_MIN] < thisStats[thisBase + OFF_MIN]) {
                thisStats[thisBase + OFF_MIN] = oStats[oBase + OFF_MIN];
            }
            if (!thisHv[thisGid] || oStats[oBase + OFF_MAX] > thisStats[thisBase + OFF_MAX]) {
                thisStats[thisBase + OFF_MAX] = oStats[oBase + OFF_MAX];
            }
            thisStats[thisBase + OFF_SUM] += oStats[oBase + OFF_SUM];
            thisStats[thisBase + OFF_COUNT] += oStats[oBase + OFF_COUNT];
            thisHv[thisGid] = true;
        }
    }

    @Override
    public ColumnVector finish(String outputName, int numGroups) {
        double[] s = (sharedParent != null) ? sharedParent.stats : this.stats;
        boolean[] hv = (sharedParent != null) ? sharedParent.hasValues : this.hasValues;

        BitSet nulls = new BitSet(numGroups);

        if (aggType == Aggregation.COUNT) {
            long[] counts = new long[numGroups];
            for (int g = 0; g < numGroups; g++) {
                counts[g] = (long) s[g * STATS_SIZE + OFF_COUNT];
            }
            return new LongColumnVector(outputName, true, counts, nulls, numGroups);
        }

        for (int g = 0; g < numGroups; g++) {
            if (!hv[g]) {
                nulls.set(g);
            }
        }

        if (aggType == Aggregation.MIN || aggType == Aggregation.MINIMUM) {
            if (resultType == Integer.class || resultType == int.class) {
                int[] data = new int[numGroups];
                for (int g = 0; g < numGroups; g++) data[g] = (int) s[g * STATS_SIZE + OFF_MIN];
                return new IntColumnVector(outputName, true, data, nulls, numGroups);
            }
            if (resultType == Long.class || resultType == long.class) {
                long[] data = new long[numGroups];
                for (int g = 0; g < numGroups; g++) data[g] = (long) s[g * STATS_SIZE + OFF_MIN];
                return new LongColumnVector(outputName, true, data, nulls, numGroups);
            }
            double[] data = new double[numGroups];
            for (int g = 0; g < numGroups; g++) data[g] = s[g * STATS_SIZE + OFF_MIN];
            return new DoubleColumnVector(outputName, true, data, nulls, numGroups);
        }

        if (aggType == Aggregation.MAX || aggType == Aggregation.MAXIMUM) {
            if (resultType == Integer.class || resultType == int.class) {
                int[] data = new int[numGroups];
                for (int g = 0; g < numGroups; g++) data[g] = (int) s[g * STATS_SIZE + OFF_MAX];
                return new IntColumnVector(outputName, true, data, nulls, numGroups);
            }
            if (resultType == Long.class || resultType == long.class) {
                long[] data = new long[numGroups];
                for (int g = 0; g < numGroups; g++) data[g] = (long) s[g * STATS_SIZE + OFF_MAX];
                return new LongColumnVector(outputName, true, data, nulls, numGroups);
            }
            double[] data = new double[numGroups];
            for (int g = 0; g < numGroups; g++) data[g] = s[g * STATS_SIZE + OFF_MAX];
            return new DoubleColumnVector(outputName, true, data, nulls, numGroups);
        }

        if (aggType == Aggregation.SUM) {
            if (resultType == Integer.class || resultType == int.class) {
                int[] data = new int[numGroups];
                for (int g = 0; g < numGroups; g++) data[g] = (int) s[g * STATS_SIZE + OFF_SUM];
                return new IntColumnVector(outputName, true, data, nulls, numGroups);
            }
            if (resultType == Long.class || resultType == long.class) {
                long[] data = new long[numGroups];
                for (int g = 0; g < numGroups; g++) data[g] = (long) s[g * STATS_SIZE + OFF_SUM];
                return new LongColumnVector(outputName, true, data, nulls, numGroups);
            }
            double[] data = new double[numGroups];
            for (int g = 0; g < numGroups; g++) data[g] = s[g * STATS_SIZE + OFF_SUM];
            return new DoubleColumnVector(outputName, true, data, nulls, numGroups);
        }

        if (aggType == Aggregation.MEAN || aggType == Aggregation.AVG || aggType == Aggregation.AVERAGE) {
            double[] data = new double[numGroups];
            for (int g = 0; g < numGroups; g++) {
                int base = g * STATS_SIZE;
                double cnt = s[base + OFF_COUNT];
                if (cnt > 0) {
                    data[g] = s[base + OFF_SUM] / cnt;
                } else {
                    nulls.set(g);
                }
            }
            return new DoubleColumnVector(outputName, true, data, nulls, numGroups);
        }

        // Default fallback to sum
        double[] data = new double[numGroups];
        for (int g = 0; g < numGroups; g++) data[g] = s[g * STATS_SIZE + OFF_SUM];
        return new DoubleColumnVector(outputName, true, data, nulls, numGroups);
    }
}
