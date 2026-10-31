package com.cupoftea.lazy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.Aggregate;
import com.cupoftea.table.Aggregation;
import com.cupoftea.table.Column;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DictionaryColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.ObjectColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * Enterprise compact running GroupBy engine for streaming / out-of-core aggregations.
 * Maintains compact primitive running aggregates per batch/chunk without materializing
 * the full dataset into memory.
 */
public class CompactRunningGroupBy {

    protected final String[] groupColumns;
    protected final Aggregate[] aggregates;
    protected final List<Column> sourceHeaders;
    protected final GroupKeyTable keyTable;
    protected final RunningAccumulator[] accumulators;

    public CompactRunningGroupBy(String[] groupColumns, Aggregate[] aggregates, List<Column> sourceHeaders) {
        this.groupColumns = groupColumns != null ? groupColumns : new String[0];
        this.aggregates = aggregates != null ? aggregates : new Aggregate[0];
        this.sourceHeaders = sourceHeaders != null ? sourceHeaders : new ArrayList<>();

        this.keyTable = createGroupTable(this.groupColumns, this.sourceHeaders);
        this.accumulators = new RunningAccumulator[this.aggregates.length];
        for (int a = 0; a < this.aggregates.length; a++) {
            this.accumulators[a] = createAccumulator(this.aggregates[a], this.sourceHeaders);
        }
    }

    public int numGroups() {
        return keyTable.numGroups();
    }

    public void accumulateBatch(CupOfTea batch) {
        if (batch == null || batch.size() == 0) {
            return;
        }

        IDataFrame df = batch.getDataFrame();
        int rowCount = batch.size();

        // 1. Resolve key vectors
        ColumnVector[] keyVectors = new ColumnVector[groupColumns.length];
        for (int i = 0; i < groupColumns.length; i++) {
            keyVectors[i] = df.getColumnVector(groupColumns[i]);
        }

        // 2. Resolve value vectors for aggregates
        ColumnVector[] valVectors = new ColumnVector[aggregates.length];
        for (int a = 0; a < aggregates.length; a++) {
            String col = aggregates[a].getColumn();
            valVectors[a] = col != null ? df.getColumnVector(col) : null;
        }

        // Fast path for single string key (e.g., 1BRC measurements)
        if (keyTable instanceof CompactStringTable cst && keyVectors.length == 1 && keyVectors[0] instanceof StringColumnVector strVec) {
            accumulateSingleStringBatch(cst, strVec, valVectors, rowCount);
            return;
        }

        // Standard path
        for (int r = 0; r < rowCount; r++) {
            int gid = keyTable.findOrInsert(keyVectors, r);
            ensureAccumulatorCapacity(keyTable.capacityLimit());
            for (int a = 0; a < accumulators.length; a++) {
                accumulators[a].accumulate(gid, valVectors[a], r);
            }
        }
    }

    private void accumulateSingleStringBatch(CompactStringTable cst, StringColumnVector strVec,
                                            ColumnVector[] valVectors, int rowCount) {
        for (int r = 0; r < rowCount; r++) {
            int gid = cst.findOrInsert(strVec, r);
            ensureAccumulatorCapacity(cst.capacityLimit());
            for (int a = 0; a < accumulators.length; a++) {
                accumulators[a].accumulate(gid, valVectors[a], r);
            }
        }
    }

    protected void ensureAccumulatorCapacity(int capacity) {
        for (RunningAccumulator acc : accumulators) {
            acc.ensureCapacity(capacity);
        }
    }

    public void merge(CompactRunningGroupBy other) {
        if (other == null || other.numGroups() == 0) {
            return;
        }

        int otherGroups = other.numGroups();
        for (int otherGid = 0; otherGid < otherGroups; otherGid++) {
            int thisGid = this.keyTable.insertFromOther(other.keyTable, otherGid);
            ensureAccumulatorCapacity(this.keyTable.capacityLimit());
            for (int a = 0; a < accumulators.length; a++) {
                accumulators[a].merge(other.accumulators[a], thisGid, otherGid);
            }
        }
    }

    public CupOfTea finish() {
        int numGroups = keyTable.numGroups();
        List<ColumnVector> outputVectors = new ArrayList<>(groupColumns.length + aggregates.length);

        // 1. Output key vectors
        List<ColumnVector> keyVecs = keyTable.finishKeyVectors(groupColumns);
        outputVectors.addAll(keyVecs);

        // 2. Output aggregate vectors
        for (int a = 0; a < aggregates.length; a++) {
            outputVectors.add(accumulators[a].finish(aggregates[a].getOutputColumnName(), numGroups));
        }

        DataFrame resultDf = DataFrame.fromVectors(outputVectors);
        return new CupOfTea(resultDf);
    }

    // --- Key Table Factory ---

    private static GroupKeyTable createGroupTable(String[] groupColumns, List<Column> sourceHeaders) {
        if (groupColumns == null || groupColumns.length == 0) {
            return new SingleGroupTable();
        }
        if (groupColumns.length == 1) {
            String col = groupColumns[0];
            Class<?> type = findColumnType(col, sourceHeaders);
            if (type == int.class || type == Integer.class) {
                return new CompactIntTable();
            }
            if (type == long.class || type == Long.class) {
                return new CompactLongTable();
            }
            if (type == double.class || type == Double.class) {
                return new CompactDoubleTable();
            }
            return new CompactStringTable(64);
        }
        return new MultiColumnTable(groupColumns.length);
    }

    private static Class<?> findColumnType(String column, List<Column> sourceHeaders) {
        if (column == null || sourceHeaders == null) return null;
        for (Column c : sourceHeaders) {
            if (column.equals(c.columnName())) {
                return c.columnType();
            }
        }
        return null;
    }

    // --- Accumulator Factory ---

    private static RunningAccumulator createAccumulator(Aggregate agg, List<Column> sourceHeaders) {
        Aggregation type = agg.getAggregationType();
        String outName = agg.getOutputColumnName();

        if (type == null) {
            // Infer from output name pattern if type not explicitly set
            String lower = outName.toLowerCase();
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

        Class<?> resType = agg.getResultType(sourceHeaders);

        return switch (type) {
            case SUM -> new SumAccumulator(resType);
            case MEAN, AVG, AVERAGE -> new MeanAccumulator(resType);
            case MIN, MINIMUM -> new MinAccumulator(resType);
            case MAX, MAXIMUM -> new MaxAccumulator(resType);
            case COUNT -> new CountAccumulator();
            case FIRST -> new FirstAccumulator(resType);
            case LAST -> new LastAccumulator(resType);
            case STD, STANDARD_DEVIATION -> new StdAccumulator(true);
            case VAR, VARIANCE -> new StdAccumulator(false);
            default -> new SumAccumulator(resType);
        };
    }

    // --- Interfaces for Group Table & Running Accumulators ---

    interface GroupKeyTable {
        int numGroups();
        int capacityLimit();
        int findOrInsert(ColumnVector[] vectors, int row);
        int insertFromOther(GroupKeyTable other, int otherGid);
        List<ColumnVector> finishKeyVectors(String[] columnNames);
        default int findOrInsertNull() {
            return 0;
        }
    }

    interface RunningAccumulator {
        void ensureCapacity(int minCapacity);
        void accumulate(int gid, ColumnVector vector, int row);
        void merge(RunningAccumulator other, int thisGid, int otherGid);
        ColumnVector finish(String outputName, int numGroups);

        default void accumulateDouble(int gid, double value) {}
        default void accumulateLong(int gid, long value) { accumulateDouble(gid, (double) value); }
        default void accumulateInt(int gid, int value) { accumulateDouble(gid, (double) value); }
        default void accumulateObject(int gid, Object value) {
            if (value instanceof Number n) {
                accumulateDouble(gid, n.doubleValue());
            } else if (value != null) {
                try {
                    accumulateDouble(gid, Double.parseDouble(value.toString()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        default void accumulateNull(int gid) {}
    }

    // =========================================================================
    // Group Key Tables
    // =========================================================================

    /**
     * Group table for global aggregations without group columns (single group 0).
     */
    static class SingleGroupTable implements GroupKeyTable {
        @Override
        public int numGroups() {
            return 1;
        }

        @Override
        public int capacityLimit() {
            return 1;
        }

        @Override
        public int findOrInsert(ColumnVector[] vectors, int row) {
            return 0;
        }

        @Override
        public int insertFromOther(GroupKeyTable other, int otherGid) {
            return 0;
        }

        @Override
        public List<ColumnVector> finishKeyVectors(String[] columnNames) {
            return new ArrayList<>();
        }
    }

    /**
     * Compact open-addressing hash table for single string columns.
     * Materializes String objects and byte arrays ONLY ONCE when a group is first discovered.
     */
    static class CompactStringTable implements GroupKeyTable {
        private static final float LOAD_FACTOR = 0.70f;
        private static final int SSO_MAX_LEN = 16;
        private static final java.lang.invoke.VarHandle LONG_LE = java.lang.invoke.MethodHandles
                .byteArrayViewVarHandle(long[].class, java.nio.ByteOrder.LITTLE_ENDIAN);
        private int capacity;
        private int mask;
        private int threshold;
        private int numGroups;

        private int[] table;
        private int[] hashes;
        private int[] lengths;
        private long[] prefix1;
        private long[] prefix2;
        private String[] keys;
        private byte[][] keyBytes;

        CompactStringTable(int initialCapacity) {
            this.capacity = Math.max(16, Integer.highestOneBit(initialCapacity) << 1);
            this.mask = capacity - 1;
            this.threshold = (int) (capacity * LOAD_FACTOR);
            this.numGroups = 0;
            this.table = new int[capacity];
            Arrays.fill(this.table, -1);
            this.hashes = new int[capacity];
            this.lengths = new int[capacity];
            this.prefix1 = new long[capacity];
            this.prefix2 = new long[capacity];
            this.keys = new String[Math.max(16, capacity / 4)];
            this.keyBytes = new byte[Math.max(16, capacity / 4)][];
        }

        @Override
        public int numGroups() {
            return numGroups;
        }

        @Override
        public int capacityLimit() {
            return keys.length;
        }

        public int findOrInsert(StringColumnVector vec, int row) {
            if (vec == null || vec.isNull(row)) {
                return findOrInsertNull();
            }
            int[] vOffsets = vec.getOffsets();
            int rStart = vOffsets[row];
            return findOrInsertSlice(vec.getValues(), rStart, vOffsets[row + 1] - rStart, null);
        }

        public int findOrInsertBytes(byte[] buf, int offset, int len) {
            return findOrInsertSlice(buf, offset, len, null);
        }

        /**
         * Short String Optimization (SSO): keys of up to {@link #SSO_MAX_LEN} bytes are compared
         * using the stored length and two packed 64-bit prefixes (register comparisons), falling
         * back to a full byte comparison only for longer keys.
         */
        private int findOrInsertSlice(byte[] buf, int offset, int len, String knownKey) {
            final boolean shortKey = len <= SSO_MAX_LEN;
            final long p1 = shortKey ? loadWord(buf, offset, len, 0) : 0L;
            final long p2 = shortKey ? loadWord(buf, offset, len, 1) : 0L;
            final int h = shortKey
                    ? StringColumnVector.hashShortWords(p1, p2, len)
                    : StringColumnVector.hashBytes(buf, offset, len);
            int pos = h & mask;
            while (true) {
                int gid = table[pos];
                if (gid == -1) {
                    gid = numGroups++;
                    if (gid >= keys.length) {
                        grow();
                    }
                    byte[] kb = new byte[len];
                    System.arraycopy(buf, offset, kb, 0, len);
                    keyBytes[gid] = kb;
                    keys[gid] = knownKey != null ? knownKey : new String(kb, StandardCharsets.UTF_8);

                    table[pos] = gid;
                    hashes[pos] = h;
                    lengths[pos] = len;
                    if (shortKey) {
                        prefix1[pos] = p1;
                        prefix2[pos] = p2;
                    }
                    if (numGroups >= threshold) {
                        resize();
                    }
                    return gid;
                }
                if (hashes[pos] == h && lengths[pos] == len) {
                    if (shortKey) {
                        if (prefix1[pos] == p1 && prefix2[pos] == p2) {
                            return gid;
                        }
                    } else {
                        byte[] kb = keyBytes[gid];
                        if (kb != null && Arrays.equals(buf, offset, offset + len, kb, 0, len)) {
                            return gid;
                        }
                    }
                }
                pos = (pos + 1) & mask;
            }
        }

        /** Packs bytes [wordIdx*8, min(len, wordIdx*8+8)) of the slice into a little-endian long. */
        private static long loadWord(byte[] b, int off, int len, int wordIdx) {
            int start = wordIdx << 3;
            int n = len - start;
            if (n <= 0) {
                return 0L;
            }
            int idx = off + start;
            if (n >= 8) {
                return (long) LONG_LE.get(b, idx);
            }
            if (idx + 8 <= b.length) {
                return ((long) LONG_LE.get(b, idx)) & (~0L >>> ((8 - n) << 3));
            }
            long v = 0L;
            for (int i = n - 1; i >= 0; i--) {
                v = (v << 8) | (b[idx + i] & 0xFFL);
            }
            return v;
        }

        @Override
        public int findOrInsertNull() {
            int h = 0;
            int pos = 0;
            while (true) {
                int gid = table[pos];
                if (gid == -1) {
                    gid = numGroups++;
                    if (gid >= keys.length) grow();
                    keys[gid] = null;
                    keyBytes[gid] = null;
                    table[pos] = gid;
                    hashes[pos] = 0;
                    lengths[pos] = -1;
                    if (numGroups >= threshold) resize();
                    return gid;
                }
                if (hashes[pos] == 0 && keys[gid] == null) {
                    return gid;
                }
                pos = (pos + 1) & mask;
            }
        }

        @Override
        public int findOrInsert(ColumnVector[] vectors, int row) {
            ColumnVector vec = vectors[0];
            if (vec instanceof StringColumnVector scv) {
                return findOrInsert(scv, row);
            }
            String s = vec != null && !vec.isNull(row) ? vec.getString(row) : null;
            return findOrInsertString(s);
        }

        public int findOrInsertString(String s) {
            if (s == null) {
                return findOrInsertNull();
            }
            byte[] kb = s.getBytes(StandardCharsets.UTF_8);
            return findOrInsertSlice(kb, 0, kb.length, s);
        }

        @Override
        public int insertFromOther(GroupKeyTable other, int otherGid) {
            if (other instanceof CompactStringTable ost) {
                return findOrInsertString(ost.keys[otherGid]);
            }
            return 0;
        }

        @Override
        public List<ColumnVector> finishKeyVectors(String[] columnNames) {
            String name = columnNames[0];
            BitSet nulls = new BitSet(numGroups);
            int totalBytes = 0;
            int[] offsets = new int[numGroups + 1];
            offsets[0] = 0;

            for (int i = 0; i < numGroups; i++) {
                if (keys[i] == null) {
                    nulls.set(i);
                    offsets[i + 1] = totalBytes;
                } else {
                    byte[] kb = keyBytes[i];
                    totalBytes += kb.length;
                    offsets[i + 1] = totalBytes;
                }
            }

            byte[] flatValues = new byte[totalBytes];
            for (int i = 0; i < numGroups; i++) {
                if (keys[i] != null) {
                    byte[] kb = keyBytes[i];
                    System.arraycopy(kb, 0, flatValues, offsets[i], kb.length);
                }
            }

            List<ColumnVector> res = new ArrayList<>(1);
            res.add(new StringColumnVector(name, true, flatValues, totalBytes, offsets, nulls, numGroups));
            return res;
        }

        private void grow() {
            int newCap = Math.max(16, keys.length * 2);
            keys = Arrays.copyOf(keys, newCap);
            keyBytes = Arrays.copyOf(keyBytes, newCap);
        }

        private void resize() {
            int newCap = capacity * 2;
            int newMask = newCap - 1;
            int[] newTable = new int[newCap];
            Arrays.fill(newTable, -1);
            int[] newHashes = new int[newCap];
            int[] newLengths = new int[newCap];
            long[] newP1 = new long[newCap];
            long[] newP2 = new long[newCap];

            for (int i = 0; i < capacity; i++) {
                int gid = table[i];
                if (gid != -1) {
                    int h = hashes[i];
                    int pos = h & newMask;
                    while (newTable[pos] != -1) {
                        pos = (pos + 1) & newMask;
                    }
                    newTable[pos] = gid;
                    newHashes[pos] = h;
                    newLengths[pos] = lengths[i];
                    newP1[pos] = prefix1[i];
                    newP2[pos] = prefix2[i];
                }
            }
            this.table = newTable;
            this.hashes = newHashes;
            this.lengths = newLengths;
            this.prefix1 = newP1;
            this.prefix2 = newP2;
            this.capacity = newCap;
            this.mask = newMask;
            this.threshold = (int) (newCap * LOAD_FACTOR);
        }
    }

    /**
     * Compact open-addressing hash table for single primitive int columns.
     */
    static class CompactIntTable implements GroupKeyTable {
        private static final float LOAD_FACTOR = 0.70f;
        private int capacity = 32;
        private int mask = 31;
        private int threshold = (int) (32 * LOAD_FACTOR);
        private int numGroups = 0;
        private int[] table = new int[32];
        private int[] keys = new int[16];
        private BitSet nulls = new BitSet();
        private int nullGid = -1;

        CompactIntTable() {
            Arrays.fill(table, -1);
        }

        @Override
        public int numGroups() {
            return numGroups;
        }

        @Override
        public int capacityLimit() {
            return keys.length;
        }

        @Override
        public int findOrInsert(ColumnVector[] vectors, int row) {
            ColumnVector vec = vectors[0];
            if (vec == null || vec.isNull(row)) {
                return findOrInsertNull();
            }
            int val = ((IntColumnVector) vec).getInt(row);
            return findOrInsertValue(val);
        }

        @Override
        public int findOrInsertNull() {
            if (nullGid == -1) {
                nullGid = numGroups++;
                if (nullGid >= keys.length) keys = Arrays.copyOf(keys, keys.length * 2);
                nulls.set(nullGid);
            }
            return nullGid;
        }

        int findOrInsertValue(int val) {
            int h = Integer.hashCode(val);
            int pos = h & mask;
            while (true) {
                int gid = table[pos];
                if (gid == -1) {
                    gid = numGroups++;
                    if (gid >= keys.length) keys = Arrays.copyOf(keys, keys.length * 2);
                    keys[gid] = val;
                    table[pos] = gid;
                    if (numGroups >= threshold) resize();
                    return gid;
                }
                if (!nulls.get(gid) && keys[gid] == val) {
                    return gid;
                }
                pos = (pos + 1) & mask;
            }
        }

        @Override
        public int insertFromOther(GroupKeyTable other, int otherGid) {
            CompactIntTable o = (CompactIntTable) other;
            if (o.nulls.get(otherGid)) {
                if (nullGid == -1) {
                    nullGid = numGroups++;
                    if (nullGid >= keys.length) keys = Arrays.copyOf(keys, keys.length * 2);
                    nulls.set(nullGid);
                }
                return nullGid;
            }
            return findOrInsertValue(o.keys[otherGid]);
        }

        @Override
        public List<ColumnVector> finishKeyVectors(String[] columnNames) {
            List<ColumnVector> res = new ArrayList<>(1);
            res.add(new IntColumnVector(columnNames[0], true, Arrays.copyOf(keys, numGroups), nulls, numGroups));
            return res;
        }

        private void resize() {
            int newCap = capacity * 2;
            int newMask = newCap - 1;
            int[] newTable = new int[newCap];
            Arrays.fill(newTable, -1);
            for (int i = 0; i < capacity; i++) {
                int gid = table[i];
                if (gid != -1) {
                    int val = keys[gid];
                    int pos = Integer.hashCode(val) & newMask;
                    while (newTable[pos] != -1) {
                        pos = (pos + 1) & newMask;
                    }
                    newTable[pos] = gid;
                }
            }
            this.table = newTable;
            this.capacity = newCap;
            this.mask = newMask;
            this.threshold = (int) (newCap * LOAD_FACTOR);
        }
    }

    /**
     * Compact table for single long columns.
     */
    static class CompactLongTable implements GroupKeyTable {
        private int numGroups = 0;
        private long[] keys = new long[16];
        private BitSet nulls = new BitSet();
        private final java.util.Map<Long, Integer> map = new java.util.HashMap<>();
        private int nullGid = -1;

        @Override
        public int numGroups() {
            return numGroups;
        }

        @Override
        public int capacityLimit() {
            return keys.length;
        }

        @Override
        public int findOrInsert(ColumnVector[] vectors, int row) {
            ColumnVector vec = vectors[0];
            if (vec == null || vec.isNull(row)) {
                return findOrInsertNull();
            }
            long val = ((LongColumnVector) vec).getLong(row);
            return findOrInsertValue(val);
        }

        @Override
        public int findOrInsertNull() {
            if (nullGid == -1) {
                nullGid = numGroups++;
                if (nullGid >= keys.length) keys = Arrays.copyOf(keys, keys.length * 2);
                nulls.set(nullGid);
            }
            return nullGid;
        }

        int findOrInsertValue(long val) {
            Integer gid = map.get(val);
            if (gid == null) {
                int g = numGroups++;
                if (g >= keys.length) keys = Arrays.copyOf(keys, keys.length * 2);
                keys[g] = val;
                map.put(val, g);
                return g;
            }
            return gid;
        }

        @Override
        public int insertFromOther(GroupKeyTable other, int otherGid) {
            CompactLongTable o = (CompactLongTable) other;
            if (o.nulls.get(otherGid)) {
                return findOrInsertNull();
            }
            return findOrInsertValue(o.keys[otherGid]);
        }

        @Override
        public List<ColumnVector> finishKeyVectors(String[] columnNames) {
            List<ColumnVector> res = new ArrayList<>(1);
            res.add(new LongColumnVector(columnNames[0], true, Arrays.copyOf(keys, numGroups), nulls, numGroups));
            return res;
        }
    }

    /**
     * Compact table for single double columns.
     */
    static class CompactDoubleTable implements GroupKeyTable {
        private int numGroups = 0;
        private double[] keys = new double[16];
        private BitSet nulls = new BitSet();
        private final java.util.Map<Double, Integer> map = new java.util.HashMap<>();
        private int nullGid = -1;

        @Override
        public int numGroups() {
            return numGroups;
        }

        @Override
        public int capacityLimit() {
            return keys.length;
        }

        @Override
        public int findOrInsert(ColumnVector[] vectors, int row) {
            ColumnVector vec = vectors[0];
            if (vec == null || vec.isNull(row)) {
                return findOrInsertNull();
            }
            double val = vec.getDouble(row);
            return findOrInsertValue(val);
        }

        @Override
        public int findOrInsertNull() {
            if (nullGid == -1) {
                nullGid = numGroups++;
                if (nullGid >= keys.length) keys = Arrays.copyOf(keys, keys.length * 2);
                nulls.set(nullGid);
            }
            return nullGid;
        }

        int findOrInsertValue(double val) {
            Integer gid = map.get(val);
            if (gid == null) {
                int g = numGroups++;
                if (g >= keys.length) keys = Arrays.copyOf(keys, keys.length * 2);
                keys[g] = val;
                map.put(val, g);
                return g;
            }
            return gid;
        }

        @Override
        public int insertFromOther(GroupKeyTable other, int otherGid) {
            CompactDoubleTable o = (CompactDoubleTable) other;
            if (o.nulls.get(otherGid)) {
                return findOrInsertNull();
            }
            return findOrInsertValue(o.keys[otherGid]);
        }

        @Override
        public List<ColumnVector> finishKeyVectors(String[] columnNames) {
            List<ColumnVector> res = new ArrayList<>(1);
            res.add(new DoubleColumnVector(columnNames[0], true, Arrays.copyOf(keys, numGroups), nulls, numGroups));
            return res;
        }
    }

    /**
     * Group table for compound / multi-column keys.
     */
    static class MultiColumnTable implements GroupKeyTable {
        private final int colCount;
        private int numGroups = 0;
        private final java.util.Map<MultiKey, Integer> map = new java.util.HashMap<>();
        private Object[][] groupValues = new Object[16][];

        MultiColumnTable(int colCount) {
            this.colCount = colCount;
        }

        @Override
        public int numGroups() {
            return numGroups;
        }

        @Override
        public int capacityLimit() {
            return groupValues.length;
        }

        @Override
        public int findOrInsert(ColumnVector[] vectors, int row) {
            Object[] vals = new Object[colCount];
            for (int i = 0; i < colCount; i++) {
                vals[i] = vectors[i] != null && !vectors[i].isNull(row) ? vectors[i].getObject(row) : null;
            }
            return findOrInsertRow(vals);
        }

        int findOrInsertRow(Object[] vals) {
            MultiKey key = new MultiKey(vals);
            Integer gid = map.get(key);
            if (gid == null) {
                int g = numGroups++;
                if (g >= groupValues.length) {
                    groupValues = Arrays.copyOf(groupValues, groupValues.length * 2);
                }
                groupValues[g] = vals;
                map.put(key, g);
                return g;
            }
            return gid;
        }

        @Override
        public int insertFromOther(GroupKeyTable other, int otherGid) {
            MultiColumnTable o = (MultiColumnTable) other;
            Object[] vals = o.groupValues[otherGid];
            MultiKey key = new MultiKey(vals);
            Integer gid = map.get(key);
            if (gid == null) {
                gid = numGroups++;
                if (gid >= groupValues.length) {
                    groupValues = Arrays.copyOf(groupValues, groupValues.length * 2);
                }
                groupValues[gid] = vals;
                map.put(key, gid);
            }
            return gid;
        }

        @Override
        public List<ColumnVector> finishKeyVectors(String[] columnNames) {
            List<ColumnVector> res = new ArrayList<>(colCount);
            for (int c = 0; c < colCount; c++) {
                Object[] colData = new Object[numGroups];
                BitSet nulls = new BitSet(numGroups);
                for (int g = 0; g < numGroups; g++) {
                    Object v = groupValues[g][c];
                    colData[g] = v;
                    if (v == null) nulls.set(g);
                }
                res.add(new ObjectColumnVector(columnNames[c], Object.class, true, colData, nulls, numGroups));
            }
            return res;
        }

        static final class MultiKey {
            final Object[] values;
            final int hash;

            MultiKey(Object[] values) {
                this.values = values;
                this.hash = Arrays.hashCode(values);
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o instanceof MultiKey other) {
                    return Arrays.equals(values, other.values);
                }
                return false;
            }

            @Override
            public int hashCode() {
                return hash;
            }
        }
    }

    // =========================================================================
    // Accumulators
    // =========================================================================

    static class CountAccumulator implements RunningAccumulator {
        private long[] counts = new long[16];

        @Override
        public void ensureCapacity(int minCapacity) {
            if (minCapacity > counts.length) {
                counts = Arrays.copyOf(counts, Math.max(counts.length * 2, minCapacity));
            }
        }

        @Override
        public void accumulate(int gid, ColumnVector vector, int row) {
            if (vector == null || !vector.isNull(row)) {
                counts[gid]++;
            }
        }

        @Override
        public void accumulateDouble(int gid, double value) {
            counts[gid]++;
        }

        @Override
        public void accumulateLong(int gid, long value) {
            counts[gid]++;
        }

        @Override
        public void accumulateInt(int gid, int value) {
            counts[gid]++;
        }

        @Override
        public void accumulateObject(int gid, Object value) {
            if (value != null) {
                counts[gid]++;
            }
        }

        @Override
        public void merge(RunningAccumulator other, int thisGid, int otherGid) {
            this.counts[thisGid] += ((CountAccumulator) other).counts[otherGid];
        }

        @Override
        public ColumnVector finish(String outputName, int numGroups) {
            return new LongColumnVector(outputName, true, Arrays.copyOf(counts, numGroups), new BitSet(numGroups), numGroups);
        }
    }

    static class SumAccumulator implements RunningAccumulator {
        private final Class<?> resultType;
        private double[] sums = new double[16];
        private boolean[] hasValues = new boolean[16];

        SumAccumulator(Class<?> resultType) {
            this.resultType = resultType != null ? resultType : Double.class;
        }

        @Override
        public void ensureCapacity(int minCapacity) {
            if (minCapacity > sums.length) {
                int newCap = Math.max(sums.length * 2, minCapacity);
                sums = Arrays.copyOf(sums, newCap);
                hasValues = Arrays.copyOf(hasValues, newCap);
            }
        }

        @Override
        public void accumulate(int gid, ColumnVector vector, int row) {
            if (vector != null && !vector.isNull(row)) {
                sums[gid] += vector.getDouble(row);
                hasValues[gid] = true;
            }
        }

        @Override
        public void accumulateDouble(int gid, double value) {
            sums[gid] += value;
            hasValues[gid] = true;
        }

        @Override
        public void accumulateLong(int gid, long value) {
            sums[gid] += value;
            hasValues[gid] = true;
        }

        @Override
        public void accumulateInt(int gid, int value) {
            sums[gid] += value;
            hasValues[gid] = true;
        }

        @Override
        public void merge(RunningAccumulator other, int thisGid, int otherGid) {
            SumAccumulator o = (SumAccumulator) other;
            if (o.hasValues[otherGid]) {
                sums[thisGid] += o.sums[otherGid];
                hasValues[thisGid] = true;
            }
        }

        @Override
        public ColumnVector finish(String outputName, int numGroups) {
            BitSet nulls = new BitSet(numGroups);
            for (int g = 0; g < numGroups; g++) {
                if (!hasValues[g]) nulls.set(g);
            }
            if (resultType == Long.class || resultType == long.class) {
                long[] ldata = new long[numGroups];
                for (int g = 0; g < numGroups; g++) ldata[g] = (long) sums[g];
                return new LongColumnVector(outputName, true, ldata, nulls, numGroups);
            }
            if (resultType == Integer.class || resultType == int.class) {
                int[] idata = new int[numGroups];
                for (int g = 0; g < numGroups; g++) idata[g] = (int) sums[g];
                return new IntColumnVector(outputName, true, idata, nulls, numGroups);
            }
            return new DoubleColumnVector(outputName, true, Arrays.copyOf(sums, numGroups), nulls, numGroups);
        }
    }

    static class MeanAccumulator implements RunningAccumulator {
        private final Class<?> resultType;
        private double[] sums = new double[16];
        private long[] counts = new long[16];

        MeanAccumulator(Class<?> resultType) {
            this.resultType = resultType != null ? resultType : Double.class;
        }

        @Override
        public void ensureCapacity(int minCapacity) {
            if (minCapacity > sums.length) {
                int newCap = Math.max(sums.length * 2, minCapacity);
                sums = Arrays.copyOf(sums, newCap);
                counts = Arrays.copyOf(counts, newCap);
            }
        }

        @Override
        public void accumulate(int gid, ColumnVector vector, int row) {
            if (vector != null && !vector.isNull(row)) {
                sums[gid] += vector.getDouble(row);
                counts[gid]++;
            }
        }

        @Override
        public void accumulateDouble(int gid, double value) {
            sums[gid] += value;
            counts[gid]++;
        }

        @Override
        public void accumulateLong(int gid, long value) {
            sums[gid] += value;
            counts[gid]++;
        }

        @Override
        public void accumulateInt(int gid, int value) {
            sums[gid] += value;
            counts[gid]++;
        }

        @Override
        public void merge(RunningAccumulator other, int thisGid, int otherGid) {
            MeanAccumulator o = (MeanAccumulator) other;
            this.sums[thisGid] += o.sums[otherGid];
            this.counts[thisGid] += o.counts[otherGid];
        }

        @Override
        public ColumnVector finish(String outputName, int numGroups) {
            double[] means = new double[numGroups];
            BitSet nulls = new BitSet(numGroups);
            for (int g = 0; g < numGroups; g++) {
                if (counts[g] > 0) {
                    means[g] = sums[g] / counts[g];
                } else {
                    nulls.set(g);
                }
            }
            return new DoubleColumnVector(outputName, true, means, nulls, numGroups);
        }
    }

    static class MinAccumulator implements RunningAccumulator {
        private final Class<?> resultType;
        private double[] mins = new double[16];
        private boolean[] hasValues = new boolean[16];

        MinAccumulator(Class<?> resultType) {
            this.resultType = resultType != null ? resultType : Double.class;
            Arrays.fill(mins, Double.POSITIVE_INFINITY);
        }

        @Override
        public void ensureCapacity(int minCapacity) {
            if (minCapacity > mins.length) {
                int oldCap = mins.length;
                int newCap = Math.max(oldCap * 2, minCapacity);
                mins = Arrays.copyOf(mins, newCap);
                Arrays.fill(mins, oldCap, newCap, Double.POSITIVE_INFINITY);
                hasValues = Arrays.copyOf(hasValues, newCap);
            }
        }

        @Override
        public void accumulate(int gid, ColumnVector vector, int row) {
            if (vector != null && !vector.isNull(row)) {
                double v = vector.getDouble(row);
                if (v < mins[gid]) {
                    mins[gid] = v;
                }
                hasValues[gid] = true;
            }
        }

        @Override
        public void accumulateDouble(int gid, double value) {
            if (value < mins[gid]) {
                mins[gid] = value;
            }
            hasValues[gid] = true;
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
        public void merge(RunningAccumulator other, int thisGid, int otherGid) {
            MinAccumulator o = (MinAccumulator) other;
            if (o.hasValues[otherGid]) {
                if (!hasValues[thisGid] || o.mins[otherGid] < mins[thisGid]) {
                    mins[thisGid] = o.mins[otherGid];
                }
                hasValues[thisGid] = true;
            }
        }

        @Override
        public ColumnVector finish(String outputName, int numGroups) {
            BitSet nulls = new BitSet(numGroups);
            for (int g = 0; g < numGroups; g++) {
                if (!hasValues[g]) nulls.set(g);
            }
            if (resultType == Integer.class || resultType == int.class) {
                int[] data = new int[numGroups];
                for (int g = 0; g < numGroups; g++) data[g] = (int) mins[g];
                return new IntColumnVector(outputName, true, data, nulls, numGroups);
            }
            if (resultType == Long.class || resultType == long.class) {
                long[] data = new long[numGroups];
                for (int g = 0; g < numGroups; g++) data[g] = (long) mins[g];
                return new LongColumnVector(outputName, true, data, nulls, numGroups);
            }
            return new DoubleColumnVector(outputName, true, Arrays.copyOf(mins, numGroups), nulls, numGroups);
        }
    }

    static class MaxAccumulator implements RunningAccumulator {
        private final Class<?> resultType;
        private double[] maxs = new double[16];
        private boolean[] hasValues = new boolean[16];

        MaxAccumulator(Class<?> resultType) {
            this.resultType = resultType != null ? resultType : Double.class;
            Arrays.fill(maxs, Double.NEGATIVE_INFINITY);
        }

        @Override
        public void ensureCapacity(int minCapacity) {
            if (minCapacity > maxs.length) {
                int oldCap = maxs.length;
                int newCap = Math.max(oldCap * 2, minCapacity);
                maxs = Arrays.copyOf(maxs, newCap);
                Arrays.fill(maxs, oldCap, newCap, Double.NEGATIVE_INFINITY);
                hasValues = Arrays.copyOf(hasValues, newCap);
            }
        }

        @Override
        public void accumulate(int gid, ColumnVector vector, int row) {
            if (vector != null && !vector.isNull(row)) {
                double v = vector.getDouble(row);
                if (v > maxs[gid]) {
                    maxs[gid] = v;
                }
                hasValues[gid] = true;
            }
        }

        @Override
        public void accumulateDouble(int gid, double value) {
            if (value > maxs[gid]) {
                maxs[gid] = value;
            }
            hasValues[gid] = true;
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
        public void merge(RunningAccumulator other, int thisGid, int otherGid) {
            MaxAccumulator o = (MaxAccumulator) other;
            if (o.hasValues[otherGid]) {
                if (!hasValues[thisGid] || o.maxs[otherGid] > maxs[thisGid]) {
                    maxs[thisGid] = o.maxs[otherGid];
                }
                hasValues[thisGid] = true;
            }
        }

        @Override
        public ColumnVector finish(String outputName, int numGroups) {
            BitSet nulls = new BitSet(numGroups);
            for (int g = 0; g < numGroups; g++) {
                if (!hasValues[g]) nulls.set(g);
            }
            if (resultType == Integer.class || resultType == int.class) {
                int[] data = new int[numGroups];
                for (int g = 0; g < numGroups; g++) data[g] = (int) maxs[g];
                return new IntColumnVector(outputName, true, data, nulls, numGroups);
            }
            if (resultType == Long.class || resultType == long.class) {
                long[] data = new long[numGroups];
                for (int g = 0; g < numGroups; g++) data[g] = (long) maxs[g];
                return new LongColumnVector(outputName, true, data, nulls, numGroups);
            }
            return new DoubleColumnVector(outputName, true, Arrays.copyOf(maxs, numGroups), nulls, numGroups);
        }
    }

    static class FirstAccumulator implements RunningAccumulator {
        private final Class<?> resultType;
        private Object[] values = new Object[16];
        private boolean[] hasValues = new boolean[16];

        FirstAccumulator(Class<?> resultType) {
            this.resultType = resultType != null ? resultType : Object.class;
        }

        @Override
        public void ensureCapacity(int minCapacity) {
            if (minCapacity > values.length) {
                int newCap = Math.max(values.length * 2, minCapacity);
                values = Arrays.copyOf(values, newCap);
                hasValues = Arrays.copyOf(hasValues, newCap);
            }
        }

        @Override
        public void accumulate(int gid, ColumnVector vector, int row) {
            if (!hasValues[gid] && vector != null && !vector.isNull(row)) {
                values[gid] = vector.getObject(row);
                hasValues[gid] = true;
            }
        }

        @Override
        public void accumulateDouble(int gid, double value) {
            if (!hasValues[gid]) {
                values[gid] = value;
                hasValues[gid] = true;
            }
        }

        @Override
        public void accumulateObject(int gid, Object value) {
            if (!hasValues[gid] && value != null) {
                values[gid] = value;
                hasValues[gid] = true;
            }
        }

        @Override
        public void merge(RunningAccumulator other, int thisGid, int otherGid) {
            FirstAccumulator o = (FirstAccumulator) other;
            if (!this.hasValues[thisGid] && o.hasValues[otherGid]) {
                this.values[thisGid] = o.values[otherGid];
                this.hasValues[thisGid] = true;
            }
        }

        @Override
        public ColumnVector finish(String outputName, int numGroups) {
            BitSet nulls = new BitSet(numGroups);
            for (int g = 0; g < numGroups; g++) {
                if (!hasValues[g]) nulls.set(g);
            }
            return new ObjectColumnVector(outputName, resultType != null ? resultType : Object.class, true, Arrays.copyOf(values, numGroups), nulls, numGroups);
        }
    }

    static class LastAccumulator implements RunningAccumulator {
        private final Class<?> resultType;
        private Object[] values = new Object[16];
        private boolean[] hasValues = new boolean[16];

        LastAccumulator(Class<?> resultType) {
            this.resultType = resultType != null ? resultType : Object.class;
        }

        @Override
        public void ensureCapacity(int minCapacity) {
            if (minCapacity > values.length) {
                int newCap = Math.max(values.length * 2, minCapacity);
                values = Arrays.copyOf(values, newCap);
                hasValues = Arrays.copyOf(hasValues, newCap);
            }
        }

        @Override
        public void accumulate(int gid, ColumnVector vector, int row) {
            if (vector != null && !vector.isNull(row)) {
                values[gid] = vector.getObject(row);
                hasValues[gid] = true;
            }
        }

        @Override
        public void accumulateDouble(int gid, double value) {
            values[gid] = value;
            hasValues[gid] = true;
        }

        @Override
        public void accumulateObject(int gid, Object value) {
            if (value != null) {
                values[gid] = value;
                hasValues[gid] = true;
            }
        }

        @Override
        public void merge(RunningAccumulator other, int thisGid, int otherGid) {
            LastAccumulator o = (LastAccumulator) other;
            if (o.hasValues[otherGid]) {
                this.values[thisGid] = o.values[otherGid];
                this.hasValues[thisGid] = true;
            }
        }

        @Override
        public ColumnVector finish(String outputName, int numGroups) {
            BitSet nulls = new BitSet(numGroups);
            for (int g = 0; g < numGroups; g++) {
                if (!hasValues[g]) nulls.set(g);
            }
            return new ObjectColumnVector(outputName, resultType != null ? resultType : Object.class, true, Arrays.copyOf(values, numGroups), nulls, numGroups);
        }
    }

    static class StdAccumulator implements RunningAccumulator {
        private final boolean isStd;
        private long[] counts = new long[16];
        private double[] sums = new double[16];
        private double[] sumSqs = new double[16];

        StdAccumulator(boolean isStd) {
            this.isStd = isStd;
        }

        @Override
        public void ensureCapacity(int minCapacity) {
            if (minCapacity > counts.length) {
                int newCap = Math.max(counts.length * 2, minCapacity);
                counts = Arrays.copyOf(counts, newCap);
                sums = Arrays.copyOf(sums, newCap);
                sumSqs = Arrays.copyOf(sumSqs, newCap);
            }
        }

        @Override
        public void accumulate(int gid, ColumnVector vector, int row) {
            if (vector != null && !vector.isNull(row)) {
                double v = vector.getDouble(row);
                counts[gid]++;
                sums[gid] += v;
                sumSqs[gid] += v * v;
            }
        }

        @Override
        public void accumulateDouble(int gid, double value) {
            counts[gid]++;
            sums[gid] += value;
            sumSqs[gid] += value * value;
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
        public void merge(RunningAccumulator other, int thisGid, int otherGid) {
            StdAccumulator o = (StdAccumulator) other;
            this.counts[thisGid] += o.counts[otherGid];
            this.sums[thisGid] += o.sums[otherGid];
            this.sumSqs[thisGid] += o.sumSqs[otherGid];
        }

        @Override
        public ColumnVector finish(String outputName, int numGroups) {
            double[] data = new double[numGroups];
            BitSet nulls = new BitSet(numGroups);
            for (int g = 0; g < numGroups; g++) {
                long n = counts[g];
                if (n <= 1) {
                    nulls.set(g);
                } else {
                    double s = sums[g];
                    double sq = sumSqs[g];
                    double variance = (sq - (s * s) / n) / (n - 1);
                    if (variance < 0) variance = 0;
                    data[g] = isStd ? Math.sqrt(variance) : variance;
                }
            }
            return new DoubleColumnVector(outputName, true, data, nulls, numGroups);
        }
    }
}
