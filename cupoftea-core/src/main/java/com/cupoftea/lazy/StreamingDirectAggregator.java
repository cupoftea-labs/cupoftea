package com.cupoftea.lazy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.cupoftea.table.Aggregate;
import com.cupoftea.table.Aggregation;
import com.cupoftea.table.Column;

/**
 * Fused Scan-Aggregate execution node for {@link LazyCupOfTea}.
 * Streams parsed raw field values directly into open-addressing group hash tables
 * and primitive running accumulators on the fly without intermediate ColumnVector,
 * ChunkBuffer, or transient DataFrame batch materialization.
 */
public class StreamingDirectAggregator extends CompactRunningGroupBy {

    private int[] groupColIndices;
    private int[] aggColIndices;
    private int[][] colToAggIndices;
    private int[] countAllAggIndices;
    private boolean[] colIsNumericAgg;

    private boolean isSingleStringKey;
    private CompactStringTable singleStringTable;

    private boolean isSingleIntKey;
    private CompactIntTable singleIntTable;

    private boolean isSingleLongKey;
    private CompactLongTable singleLongTable;

    private boolean isSingleDoubleKey;
    private CompactDoubleTable singleDoubleTable;

    private boolean isMultiKey;
    private MultiColumnTable multiTable;

    private boolean isGlobal;
    private boolean bound = false;

    // Cache-line interleaved numeric accumulators
    private boolean isInterleaved1Brc;
    private InterleavedNumericAccumulator rootInterleavedAcc;
    private InterleavedNumericAccumulator[] colInterleavedAccs; // per column root interleaved accumulator if applicable

    public StreamingDirectAggregator(String[] groupColumns, Aggregate[] aggregates, List<Column> sourceHeaders) {
        this(groupColumns, aggregates, sourceHeaders, buildAccumulators(aggregates, sourceHeaders));
    }

    private StreamingDirectAggregator(String[] groupColumns, Aggregate[] aggregates, List<Column> sourceHeaders,
                                      RunningAccumulator[] builtAccumulators) {
        super(groupColumns, aggregates, sourceHeaders, builtAccumulators);
        initFastTables();
        initInterleavedOptimization();
        if (sourceHeaders != null && !sourceHeaders.isEmpty()) {
            String[] names = new String[sourceHeaders.size()];
            Class<?>[] types = new Class<?>[sourceHeaders.size()];
            for (int i = 0; i < sourceHeaders.size(); i++) {
                names[i] = sourceHeaders.get(i).columnName();
                types[i] = sourceHeaders.get(i).columnType();
            }
            bindSource(names, types);
        }
    }

    private static RunningAccumulator[] buildAccumulators(Aggregate[] aggregates, List<Column> sourceHeaders) {
        if (aggregates == null || aggregates.length == 0) {
            return new RunningAccumulator[0];
        }

        // Group numeric aggregates by target column to share interleaved storage
        java.util.Map<String, InterleavedNumericAccumulator> columnToInterleaved = new java.util.HashMap<>();
        RunningAccumulator[] result = new RunningAccumulator[aggregates.length];

        for (int a = 0; a < aggregates.length; a++) {
            Aggregate agg = aggregates[a];
            Aggregation type = agg.getAggregationType();
            if (type == null) {
                String lower = agg.getOutputColumnName().toLowerCase();
                if (lower.endsWith("sum")) type = Aggregation.SUM;
                else if (lower.endsWith("mean") || lower.endsWith("avg") || lower.endsWith("average")) type = Aggregation.MEAN;
                else if (lower.endsWith("min") || lower.endsWith("minimum")) type = Aggregation.MIN;
                else if (lower.endsWith("max") || lower.endsWith("maximum")) type = Aggregation.MAX;
                else if (lower.endsWith("count")) type = Aggregation.COUNT;
                else if (lower.endsWith("first")) type = Aggregation.FIRST;
                else if (lower.endsWith("last")) type = Aggregation.LAST;
                else if (lower.endsWith("std") || lower.endsWith("deviation")) type = Aggregation.STD;
                else if (lower.endsWith("var") || lower.endsWith("variance")) type = Aggregation.VAR;
                else type = Aggregation.COUNT;
            }

            String col = agg.getColumn() != null ? agg.getColumn() : "__cupoftea_all__";
            boolean isInterleavedEligible = (type == Aggregation.MIN || type == Aggregation.MINIMUM
                    || type == Aggregation.MAX || type == Aggregation.MAXIMUM
                    || type == Aggregation.SUM
                    || type == Aggregation.MEAN || type == Aggregation.AVG || type == Aggregation.AVERAGE
                    || type == Aggregation.COUNT);

            if (isInterleavedEligible) {
                Class<?> resType = agg.getResultType(sourceHeaders);
                InterleavedNumericAccumulator root = columnToInterleaved.get(col);
                if (root == null) {
                    root = new InterleavedNumericAccumulator(type, resType, 16);
                    columnToInterleaved.put(col, root);
                    result[a] = root;
                } else {
                    result[a] = root.createSharedView(type, resType);
                }
            } else {
                result[a] = createAccumulator(agg, sourceHeaders);
            }
        }

        // Configure hasSeparateSum and hasSeparateCount on MEAN accumulators within each column group
        for (int a = 0; a < aggregates.length; a++) {
            if (result[a] instanceof InterleavedNumericAccumulator ina) {
                Aggregation t = ina.getAggregationType();
                if (t == Aggregation.MEAN || t == Aggregation.AVG || t == Aggregation.AVERAGE) {
                    double[] targetStats = ina.getStatsArray();
                    boolean hasSum = false;
                    boolean hasCount = false;
                    for (int b = 0; b < aggregates.length; b++) {
                        if (a != b && result[b] instanceof InterleavedNumericAccumulator other
                                && other.getStatsArray() == targetStats) {
                            Aggregation ot = other.getAggregationType();
                            if (ot == Aggregation.SUM) hasSum = true;
                            if (ot == Aggregation.COUNT) hasCount = true;
                        }
                    }
                    ina.setHasSeparateSum(hasSum);
                    ina.setHasSeparateCount(hasCount);
                }
            }
        }

        return result;
    }

    private void initInterleavedOptimization() {
        if (accumulators.length > 0 && accumulators[0] instanceof InterleavedNumericAccumulator ina) {
            // Check if all accumulators share the exact same root interleaved backing array
            boolean allSameRoot = true;
            for (int a = 1; a < accumulators.length; a++) {
                if (!(accumulators[a] instanceof InterleavedNumericAccumulator cur)
                        || cur.getStatsArray() != ina.getStatsArray()) {
                    allSameRoot = false;
                    break;
                }
            }
            if (allSameRoot) {
                this.isInterleaved1Brc = true;
                this.rootInterleavedAcc = ina;
            }
        }
    }

    private void initFastTables() {
        if (groupColumns == null || groupColumns.length == 0) {
            isGlobal = true;
        } else if (groupColumns.length == 1) {
            if (keyTable instanceof CompactStringTable cst) {
                isSingleStringKey = true;
                singleStringTable = cst;
            } else if (keyTable instanceof CompactIntTable cit) {
                isSingleIntKey = true;
                singleIntTable = cit;
            } else if (keyTable instanceof CompactLongTable clt) {
                isSingleLongKey = true;
                singleLongTable = clt;
            } else if (keyTable instanceof CompactDoubleTable cdt) {
                isSingleDoubleKey = true;
                singleDoubleTable = cdt;
            }
        } else if (keyTable instanceof MultiColumnTable mct) {
            isMultiKey = true;
            multiTable = mct;
        }
    }

    /**
     * Binds source column names and types to compute fast direct lookup indices.
     */
    public void bindSource(String[] colNames, Class<?>[] colTypes) {
        if (colNames == null) return;
        int numCols = colNames.length;

        // 1. Map group columns
        this.groupColIndices = new int[groupColumns.length];
        for (int i = 0; i < groupColumns.length; i++) {
            String gc = groupColumns[i];
            int idx = -1;
            for (int c = 0; c < numCols; c++) {
                if (gc.equals(colNames[c])) {
                    idx = c;
                    break;
                }
            }
            this.groupColIndices[i] = idx;
        }

        // 2. Map aggregate columns & invert into colToAggIndices
        this.aggColIndices = new int[aggregates.length];
        List<Integer> countAll = new ArrayList<>();
        List<Integer>[] perCol = new List[numCols];
        for (int c = 0; c < numCols; c++) {
            perCol[c] = new ArrayList<>();
        }

        for (int a = 0; a < aggregates.length; a++) {
            String col = aggregates[a].getColumn();
            if (col == null) {
                aggColIndices[a] = -1;
                countAll.add(a);
            } else {
                int found = -1;
                for (int c = 0; c < numCols; c++) {
                    if (col.equals(colNames[c])) {
                        found = c;
                        break;
                    }
                }
                aggColIndices[a] = found;
                if (found >= 0) {
                    perCol[found].add(a);
                } else {
                    countAll.add(a);
                }
            }
        }

        this.colToAggIndices = new int[numCols][];
        this.colIsNumericAgg = new boolean[numCols];
        for (int c = 0; c < numCols; c++) {
            if (!perCol[c].isEmpty()) {
                int[] arr = new int[perCol[c].size()];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = perCol[c].get(i);
                }
                this.colToAggIndices[c] = arr;

                boolean numeric = true;
                for (int a : arr) {
                    com.cupoftea.table.Aggregation type = aggregates[a].getAggregationType();
                    if (type != null && (type == com.cupoftea.table.Aggregation.FIRST
                            || type == com.cupoftea.table.Aggregation.LAST)) {
                        numeric = false;
                        break;
                    }
                }
                this.colIsNumericAgg[c] = numeric;
            }
        }

        if (!countAll.isEmpty()) {
            this.countAllAggIndices = new int[countAll.size()];
            for (int i = 0; i < countAll.size(); i++) {
                countAllAggIndices[i] = countAll.get(i);
            }
        } else {
            this.countAllAggIndices = null;
        }

        // Map column to root interleaved accumulator if all aggregates for that column share one
        this.colInterleavedAccs = new InterleavedNumericAccumulator[numCols];
        for (int c = 0; c < numCols; c++) {
            int[] aggs = this.colToAggIndices[c];
            if (aggs != null && aggs.length > 0 && accumulators[aggs[0]] instanceof InterleavedNumericAccumulator firstIna) {
                boolean allSame = true;
                for (int i = 1; i < aggs.length; i++) {
                    if (!(accumulators[aggs[i]] instanceof InterleavedNumericAccumulator otherIna)
                            || otherIna.getStatsArray() != firstIna.getStatsArray()) {
                        allSame = false;
                        break;
                    }
                }
                if (allSame) {
                    this.colInterleavedAccs[c] = firstIna;
                }
            }
        }

        this.bound = true;
    }

    public boolean isBound() {
        return bound;
    }

    public boolean isSingleStringKey() {
        return isSingleStringKey;
    }

    public boolean isSingleIntKey() {
        return isSingleIntKey;
    }

    public boolean isSingleLongKey() {
        return isSingleLongKey;
    }

    public boolean isSingleDoubleKey() {
        return isSingleDoubleKey;
    }

    public boolean isMultiKey() {
        return isMultiKey;
    }

    public boolean isGlobal() {
        return isGlobal;
    }

    public int getGroupColIndex(int index) {
        return (groupColIndices != null && index >= 0 && index < groupColIndices.length)
                ? groupColIndices[index] : -1;
    }

    public int[] getGroupColIndices() {
        return groupColIndices;
    }

    public boolean hasAggregates(int colIdx) {
        return colToAggIndices != null && colIdx >= 0 && colIdx < colToAggIndices.length && colToAggIndices[colIdx] != null;
    }

    public boolean isColNumericAgg(int colIdx) {
        return colIsNumericAgg != null && colIdx >= 0 && colIdx < colIsNumericAgg.length && colIsNumericAgg[colIdx];
    }

    public boolean hasCountAll() {
        return countAllAggIndices != null;
    }

    public boolean isGroupOrAggColumn(int colIdx) {
        if (groupColIndices != null) {
            for (int g : groupColIndices) {
                if (g == colIdx) return true;
            }
        }
        return hasAggregates(colIdx);
    }

    // --- Direct Key Table Probing ---

    public int findOrInsertStringBytes(byte[] buf, int offset, int len) {
        if (singleStringTable != null) {
            int gid = singleStringTable.findOrInsertBytes(buf, offset, len);
            ensureAccumulatorCapacity(singleStringTable.capacityLimit());
            return gid;
        }
        String s = new String(buf, offset, len, StandardCharsets.UTF_8);
        return findOrInsertSingleString(s);
    }

    public int findOrInsertSingleString(String s) {
        if (singleStringTable != null) {
            int gid = singleStringTable.findOrInsertString(s);
            ensureAccumulatorCapacity(singleStringTable.capacityLimit());
            return gid;
        }
        return 0;
    }

    public int findOrInsertInt(int val) {
        if (singleIntTable != null) {
            int gid = singleIntTable.findOrInsertValue(val);
            ensureAccumulatorCapacity(singleIntTable.capacityLimit());
            return gid;
        }
        return 0;
    }

    public int findOrInsertLong(long val) {
        if (singleLongTable != null) {
            int gid = singleLongTable.findOrInsertValue(val);
            ensureAccumulatorCapacity(singleLongTable.capacityLimit());
            return gid;
        }
        return 0;
    }

    public int findOrInsertDouble(double val) {
        if (singleDoubleTable != null) {
            int gid = singleDoubleTable.findOrInsertValue(val);
            ensureAccumulatorCapacity(singleDoubleTable.capacityLimit());
            return gid;
        }
        return 0;
    }

    public int findOrInsertMulti(Object[] vals) {
        if (multiTable != null) {
            int gid = multiTable.findOrInsertRow(vals);
            ensureAccumulatorCapacity(multiTable.capacityLimit());
            return gid;
        }
        return 0;
    }

    public int findOrInsertNullKey() {
        int gid = keyTable.findOrInsertNull();
        ensureAccumulatorCapacity(keyTable.capacityLimit());
        return gid;
    }

    // --- Direct Field Accumulation ---

    public void accumulateDouble(int gid, int aggIdx, double val) {
        accumulators[aggIdx].accumulateDouble(gid, val);
    }

    public void accumulateLong(int gid, int aggIdx, long val) {
        accumulators[aggIdx].accumulateLong(gid, val);
    }

    public void accumulateInt(int gid, int aggIdx, int val) {
        accumulators[aggIdx].accumulateInt(gid, val);
    }

    public void accumulateObject(int gid, int aggIdx, Object val) {
        accumulators[aggIdx].accumulateObject(gid, val);
    }

    public void accumulateNull(int gid, int aggIdx) {
        accumulators[aggIdx].accumulateNull(gid);
    }

    public void accumulateFieldDouble(int gid, int colIdx, double val) {
        if (colInterleavedAccs != null && colIdx >= 0 && colIdx < colInterleavedAccs.length && colInterleavedAccs[colIdx] != null) {
            colInterleavedAccs[colIdx].updateAll(gid, val);
            return;
        }
        int[] aggs = (colToAggIndices != null && colIdx < colToAggIndices.length) ? colToAggIndices[colIdx] : null;
        if (aggs != null) {
            for (int a : aggs) {
                accumulators[a].accumulateDouble(gid, val);
            }
        }
    }

    public void accumulateFieldLong(int gid, int colIdx, long val) {
        if (colInterleavedAccs != null && colIdx >= 0 && colIdx < colInterleavedAccs.length && colInterleavedAccs[colIdx] != null) {
            colInterleavedAccs[colIdx].updateAll(gid, (double) val);
            return;
        }
        int[] aggs = (colToAggIndices != null && colIdx < colToAggIndices.length) ? colToAggIndices[colIdx] : null;
        if (aggs != null) {
            for (int a : aggs) {
                accumulators[a].accumulateLong(gid, val);
            }
        }
    }

    public void accumulateFieldInt(int gid, int colIdx, int val) {
        if (colInterleavedAccs != null && colIdx >= 0 && colIdx < colInterleavedAccs.length && colInterleavedAccs[colIdx] != null) {
            colInterleavedAccs[colIdx].updateAll(gid, (double) val);
            return;
        }
        int[] aggs = (colToAggIndices != null && colIdx < colToAggIndices.length) ? colToAggIndices[colIdx] : null;
        if (aggs != null) {
            for (int a : aggs) {
                accumulators[a].accumulateInt(gid, val);
            }
        }
    }

    public void accumulateFieldScaled(int gid, int colIdx, long scaledVal, int scale) {
        if (colInterleavedAccs != null && colIdx >= 0 && colIdx < colInterleavedAccs.length && colInterleavedAccs[colIdx] != null) {
            colInterleavedAccs[colIdx].updateAllScaled(gid, scaledVal, scale);
            return;
        }
        int[] aggs = (colToAggIndices != null && colIdx < colToAggIndices.length) ? colToAggIndices[colIdx] : null;
        if (aggs != null) {
            for (int a : aggs) {
                accumulators[a].accumulateScaled(gid, scaledVal, scale);
            }
        }
    }

    public void accumulateFieldObject(int gid, int colIdx, Object val) {
        int[] aggs = (colToAggIndices != null && colIdx < colToAggIndices.length) ? colToAggIndices[colIdx] : null;
        if (aggs != null) {
            for (int a : aggs) {
                accumulators[a].accumulateObject(gid, val);
            }
        }
    }

    public void accumulateFieldNull(int gid, int colIdx) {
        int[] aggs = (colToAggIndices != null && colIdx < colToAggIndices.length) ? colToAggIndices[colIdx] : null;
        if (aggs != null) {
            for (int a : aggs) {
                accumulators[a].accumulateNull(gid);
            }
        }
    }

    public void accumulateCountAll(int gid) {
        if (countAllAggIndices != null) {
            for (int a : countAllAggIndices) {
                accumulators[a].accumulateDouble(gid, 1.0);
            }
        }
    }

    /**
     * Ultra-fast path for 1BRC style queries (single string key + double aggregations).
     * Bypasses all intermediate buffers and updates running accumulators directly.
     */
    public void accumulate1Brc(byte[] buf, int keyStart, int keyLen, double val) {
        int gid = singleStringTable.findOrInsertBytes(buf, keyStart, keyLen);
        ensureAccumulatorCapacity(singleStringTable.capacityLimit());
        if (isInterleaved1Brc) {
            rootInterleavedAcc.updateAll(gid, val);
            return;
        }
        for (int a = 0; a < accumulators.length; a++) {
            accumulators[a].accumulateDouble(gid, val);
        }
    }

    /**
     * Ultra-fast path for 1BRC style queries with fixed-point decimal parsing.
     * Passes scaled long values directly into accumulators to avoid branch mispredictions and float division.
     */
    public void accumulate1BrcScaled(byte[] buf, int keyStart, int keyLen, long scaledVal, int scale) {
        int gid = singleStringTable.findOrInsertBytes(buf, keyStart, keyLen);
        ensureAccumulatorCapacity(singleStringTable.capacityLimit());
        if (isInterleaved1Brc) {
            rootInterleavedAcc.updateAllScaled(gid, scaledVal, scale);
            return;
        }
        for (int a = 0; a < accumulators.length; a++) {
            accumulators[a].accumulateScaled(gid, scaledVal, scale);
        }
    }

    public String[] getGroupColumns() {
        return groupColumns;
    }

    public int getGroupColumnCount() {
        return groupColumns != null ? groupColumns.length : 0;
    }
}
