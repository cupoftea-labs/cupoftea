package com.cupoftea.lazy;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * Radix-partitioned aggregation cluster for high-cardinality single-string key GroupBy queries.
 *
 * <p>Splits keys across {@code 2^K} mini {@link FusedGroupAggTable} partitions based on the top
 * bits of the key's 32-bit hash ({@code (hash >>> (32 - K)) & (NUM_PARTITIONS - 1)}).
 * Each partition table remains compact (e.g. 1,000–2,000 keys), fitting entirely inside the CPU's
 * L2 cache and eliminating cache thrashing during ultra-high cardinality workloads (40,000+ distinct keys).</p>
 */
public final class RadixPartitionedAggTable implements CompactRunningGroupBy.GroupKeyTable {

    public static final int RADIX_BITS = 5; // 32 partitions
    public static final int NUM_PARTITIONS = 1 << RADIX_BITS;
    public static final int RADIX_SHIFT = 32 - RADIX_BITS;
    public static final int RADIX_MASK = NUM_PARTITIONS - 1;

    private static final int DEFAULT_PARTITION_CAP = 128;

    private final FusedGroupAggTable[] partitions;
    private int[] partitionOffsets;
    private int cachedTotalGroups = -1;

    public RadixPartitionedAggTable() {
        this(DEFAULT_PARTITION_CAP);
    }

    public RadixPartitionedAggTable(int initialPartitionCapacity) {
        this.partitions = new FusedGroupAggTable[NUM_PARTITIONS];
        for (int i = 0; i < NUM_PARTITIONS; i++) {
            this.partitions[i] = new FusedGroupAggTable(initialPartitionCapacity);
        }
    }

    /**
     * Extracts the radix partition index using the top bits of a 32-bit hash.
     */
    public static int getPartitionIndex(int hash) {
        return (hash >>> RADIX_SHIFT) & RADIX_MASK;
    }

    public FusedGroupAggTable[] getPartitions() {
        return partitions;
    }

    public FusedGroupAggTable getPartition(int partitionIdx) {
        return partitions[partitionIdx];
    }

    /**
     * Accumulates a floating-point metric value directly into the appropriate radix partition.
     */
    public void accumulate(byte[] buf, int offset, int len, double val) {
        cachedTotalGroups = -1;
        int h = FusedGroupAggTable.hashKey(buf, offset, len);
        int p = (h >>> RADIX_SHIFT) & RADIX_MASK;
        partitions[p].accumulateWithHash(buf, offset, len, val, h);
    }

    /**
     * Accumulates a fixed-point scaled metric value directly into the appropriate radix partition.
     */
    public void accumulateScaled(byte[] buf, int offset, int len, long scaledVal, int scale) {
        cachedTotalGroups = -1;
        int h = FusedGroupAggTable.hashKey(buf, offset, len);
        int p = (h >>> RADIX_SHIFT) & RADIX_MASK;
        partitions[p].accumulateScaledWithHash(buf, offset, len, scaledVal, scale, h);
    }

    public int findOrInsertBytes(byte[] buf, int offset, int len) {
        cachedTotalGroups = -1;
        int h = FusedGroupAggTable.hashKey(buf, offset, len);
        int p = (h >>> RADIX_SHIFT) & RADIX_MASK;
        return partitions[p].findOrInsertBytesWithHash(buf, offset, len, h);
    }

    public int findOrInsertString(String s) {
        cachedTotalGroups = -1;
        if (s == null) {
            return findOrInsertNull();
        }
        byte[] kb = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return findOrInsertBytes(kb, 0, kb.length);
    }

    @Override
    public int findOrInsertNull() {
        cachedTotalGroups = -1;
        // Null keys routed to partition 0
        return partitions[0].findOrInsertNull();
    }

    @Override
    public int findOrInsert(ColumnVector[] vectors, int row) {
        ColumnVector vec = vectors[0];
        if (vec == null || vec.isNull(row)) {
            return findOrInsertNull();
        }
        if (vec instanceof StringColumnVector scv) {
            int[] vOffsets = scv.getOffsets();
            int rStart = vOffsets[row];
            return findOrInsertBytes(scv.getValues(), rStart, vOffsets[row + 1] - rStart);
        }
        return findOrInsertString(vec.getString(row));
    }

    @Override
    public int insertFromOther(CompactRunningGroupBy.GroupKeyTable other, int otherGid) {
        throw new UnsupportedOperationException("insertFromOther should not be invoked directly on RadixPartitionedAggTable");
    }

    @Override
    public int numGroups() {
        if (cachedTotalGroups >= 0) {
            return cachedTotalGroups;
        }
        int total = 0;
        for (int i = 0; i < NUM_PARTITIONS; i++) {
            total += partitions[i].numGroups();
        }
        cachedTotalGroups = total;
        return total;
    }

    @Override
    public int capacityLimit() {
        int cap = 0;
        for (int i = 0; i < NUM_PARTITIONS; i++) {
            cap += partitions[i].capacityLimit();
        }
        return cap;
    }

    /**
     * Merges metrics and keys from another {@link RadixPartitionedAggTable} partition by partition.
     */
    public void merge(RadixPartitionedAggTable other) {
        if (other == null) return;
        cachedTotalGroups = -1;
        for (int i = 0; i < NUM_PARTITIONS; i++) {
            this.partitions[i].merge(other.partitions[i]);
        }
    }

    /**
     * Merges an incoming single {@link FusedGroupAggTable} into these radix partitions.
     */
    public void merge(FusedGroupAggTable other) {
        if (other == null || other.numGroups() == 0) return;
        cachedTotalGroups = -1;
        for (int i = 0; i < other.getCapacity(); i++) {
            int oBase = i * FusedGroupAggTable.SLOT_LONGS;
            long oMeta = other.getSlots()[oBase + 2]; // OFF_META
            if (oMeta != 0x8000_0000L) { // !EMPTY_SLOT_TAG
                int otherGid = (int) oMeta;
                double oCnt = Double.longBitsToDouble(other.getSlots()[oBase + 7]); // OFF_COUNT
                byte[] kb = other.getKeyBytes(otherGid);
                int len = (int) other.getSlots()[oBase + 3]; // OFF_LEN
                int h = (int) (oMeta >>> 32);
                int p = (len == -1) ? 0 : ((h >>> RADIX_SHIFT) & RADIX_MASK);

                if (oCnt > 0.0) {
                    double oMin = Double.longBitsToDouble(other.getSlots()[oBase + 4]); // OFF_MIN
                    double oMax = Double.longBitsToDouble(other.getSlots()[oBase + 5]); // OFF_MAX
                    double oSum = Double.longBitsToDouble(other.getSlots()[oBase + 6]); // OFF_SUM
                    partitions[p].mergeSlotDirect(kb, other.getKey(otherGid), len, h, oMin, oMax, oSum, oCnt);
                } else {
                    if (len == -1) {
                        partitions[p].findOrInsertNull();
                    } else if (kb != null) {
                        partitions[p].findOrInsertBytesWithHash(kb, 0, len, h);
                    } else {
                        partitions[p].findOrInsertString(other.getKey(otherGid));
                    }
                }
            }
        }
    }

    /**
     * Populates interleaved stats arrays for all groups sequentially across all partitions.
     */
    public void populateStats(double[] destStats, boolean[] destHasValues) {
        int gidOffset = 0;
        partitionOffsets = new int[NUM_PARTITIONS];
        for (int p = 0; p < NUM_PARTITIONS; p++) {
            partitionOffsets[p] = gidOffset;
            partitions[p].populateStatsWithOffset(destStats, destHasValues, gidOffset);
            gidOffset += partitions[p].numGroups();
        }
        cachedTotalGroups = gidOffset;
    }

    @Override
    public List<ColumnVector> finishKeyVectors(String[] columnNames) {
        String name = columnNames[0];
        int totalGroups = numGroups();
        BitSet nulls = new BitSet(totalGroups);
        int totalBytes = 0;
        int[] offsets = new int[totalGroups + 1];
        offsets[0] = 0;

        int globalGid = 0;
        for (int p = 0; p < NUM_PARTITIONS; p++) {
            FusedGroupAggTable part = partitions[p];
            int pCount = part.numGroups();
            for (int i = 0; i < pCount; i++) {
                if (part.getKey(i) == null) {
                    nulls.set(globalGid);
                    offsets[globalGid + 1] = totalBytes;
                } else {
                    byte[] kb = part.getKeyBytes(i);
                    totalBytes += kb.length;
                    offsets[globalGid + 1] = totalBytes;
                }
                globalGid++;
            }
        }

        byte[] flatValues = new byte[totalBytes];
        globalGid = 0;
        for (int p = 0; p < NUM_PARTITIONS; p++) {
            FusedGroupAggTable part = partitions[p];
            int pCount = part.numGroups();
            for (int i = 0; i < pCount; i++) {
                if (part.getKey(i) != null) {
                    byte[] kb = part.getKeyBytes(i);
                    System.arraycopy(kb, 0, flatValues, offsets[globalGid], kb.length);
                }
                globalGid++;
            }
        }

        List<ColumnVector> res = new ArrayList<>(1);
        res.add(new StringColumnVector(name, true, flatValues, totalBytes, offsets, nulls, totalGroups));
        return res;
    }
}
