package com.cupoftea.lazy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.cupoftea.table.Aggregate;
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

    public StreamingDirectAggregator(String[] groupColumns, Aggregate[] aggregates, List<Column> sourceHeaders) {
        super(groupColumns, aggregates, sourceHeaders);
        initFastTables();
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
        int[] aggs = (colToAggIndices != null && colIdx < colToAggIndices.length) ? colToAggIndices[colIdx] : null;
        if (aggs != null) {
            for (int a : aggs) {
                accumulators[a].accumulateDouble(gid, val);
            }
        }
    }

    public void accumulateFieldLong(int gid, int colIdx, long val) {
        int[] aggs = (colToAggIndices != null && colIdx < colToAggIndices.length) ? colToAggIndices[colIdx] : null;
        if (aggs != null) {
            for (int a : aggs) {
                accumulators[a].accumulateLong(gid, val);
            }
        }
    }

    public void accumulateFieldInt(int gid, int colIdx, int val) {
        int[] aggs = (colToAggIndices != null && colIdx < colToAggIndices.length) ? colToAggIndices[colIdx] : null;
        if (aggs != null) {
            for (int a : aggs) {
                accumulators[a].accumulateInt(gid, val);
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
        for (int a = 0; a < accumulators.length; a++) {
            accumulators[a].accumulateDouble(gid, val);
        }
    }

    public String[] getGroupColumns() {
        return groupColumns;
    }

    public int getGroupColumnCount() {
        return groupColumns != null ? groupColumns.length : 0;
    }
}
