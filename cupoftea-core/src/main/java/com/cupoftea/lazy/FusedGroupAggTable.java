package com.cupoftea.lazy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import com.cupoftea.table.Aggregation;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * Fused single-table key and aggregation hash table for single string key analytical workloads.
 *
 * <p>Embeds both the group key identifier / SSO prefixes and running statistics
 * ({@code min}, {@code max}, {@code sum}, {@code count}) directly in the same contiguous 64-byte
 * slot (Array-of-Structs layout fitting exactly within a single CPU L1 cache line):</p>
 *
 * <pre>
 * Slot layout (8 longs = 64 bytes):
 *   slot[0]: prefix1 (bytes 0..7)
 *   slot[1]: prefix2 (bytes 8..15)
 *   slot[2]: (hash << 32) | (gid & 0xFFFFFFFFL) (or EMPTY_SLOT_TAG)
 *   slot[3]: length (int len cast to long, -1 for null key)
 *   slot[4]: doubleToRawLongBits(min)
 *   slot[5]: doubleToRawLongBits(max)
 *   slot[6]: doubleToRawLongBits(sum)
 *   slot[7]: doubleToRawLongBits(count)
 * </pre>
 *
 * <p>Key resolution immediately yields direct access to the metrics in the same cache line,
 * completely eliminating secondary array dereferencing during hot streaming ingestion.</p>
 */
public final class FusedGroupAggTable implements CompactRunningGroupBy.GroupKeyTable {

    private static final float LOAD_FACTOR = 0.70f;
    private static final int DEFAULT_INITIAL_CAPACITY = 2048;
    private static final int SSO_MAX_LEN = 16;
    public static final int SLOT_LONGS = 8; // 64 bytes per slot = 1 CPU cache line
    private static final long EMPTY_SLOT_TAG = 0x8000_0000L;
    private static final long FAST_HASH_PRIME = 0x9E3779B97F4A7C15L;

    private static final int OFF_PREFIX1 = 0;
    private static final int OFF_PREFIX2 = 1;
    private static final int OFF_META = 2;
    private static final int OFF_LEN = 3;
    private static final int OFF_MIN = 4;
    private static final int OFF_MAX = 5;
    private static final int OFF_SUM = 6;
    private static final int OFF_COUNT = 7;

    private static final long POS_INF_BITS = Double.doubleToRawLongBits(Double.POSITIVE_INFINITY);
    private static final long NEG_INF_BITS = Double.doubleToRawLongBits(Double.NEGATIVE_INFINITY);
    private static final long ZERO_DOUBLE_BITS = Double.doubleToRawLongBits(0.0);

    private static final java.lang.invoke.VarHandle LONG_LE = java.lang.invoke.MethodHandles
            .byteArrayViewVarHandle(long[].class, java.nio.ByteOrder.LITTLE_ENDIAN);

    private int capacity;
    private int mask;
    private int threshold;
    private int numGroups;

    // Contiguous table slots: capacity * SLOT_LONGS
    private long[] slots;
    private int[] gidToSlotBase;
    private String[] keys;
    private byte[][] keyBytes;

    public FusedGroupAggTable() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    public FusedGroupAggTable(int initialCapacity) {
        this.capacity = Math.max(16, Integer.highestOneBit(Math.max(16, initialCapacity)) << 1);
        this.mask = capacity - 1;
        this.threshold = (int) (capacity * LOAD_FACTOR);
        this.numGroups = 0;
        this.slots = new long[capacity * SLOT_LONGS];
        initSlots(this.slots, capacity);
        int keyCap = Math.max(16, capacity / 4);
        this.keys = new String[keyCap];
        this.keyBytes = new byte[keyCap][];
        this.gidToSlotBase = new int[keyCap];
    }

    private static void initSlots(long[] s, int cap) {
        for (int i = 0; i < cap; i++) {
            int base = i * SLOT_LONGS;
            s[base + OFF_META] = EMPTY_SLOT_TAG;
            s[base + OFF_MIN] = POS_INF_BITS;
            s[base + OFF_MAX] = NEG_INF_BITS;
            s[base + OFF_SUM] = ZERO_DOUBLE_BITS;
            s[base + OFF_COUNT] = ZERO_DOUBLE_BITS;
        }
    }

    @Override
    public int numGroups() {
        return numGroups;
    }

    @Override
    public int capacityLimit() {
        return Math.max(keys.length, capacity);
    }

    public int getCapacity() {
        return capacity;
    }

    public long[] getSlots() {
        return slots;
    }

    private static int fastHash8(long p1, int len) {
        long h = (p1 ^ (len * FAST_HASH_PRIME)) * FAST_HASH_PRIME;
        return (int) (h ^ (h >>> 32));
    }

    public static long loadWord(byte[] b, int off, int len, int wordIdx) {
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

    public static int hashKey(byte[] buf, int offset, int len) {
        if (len <= SSO_MAX_LEN) {
            long p1 = loadWord(buf, offset, len, 0);
            if (len <= 8) {
                return fastHash8(p1, len);
            } else {
                long p2 = loadWord(buf, offset, len, 1);
                return StringColumnVector.hashShortWords(p1, p2, len);
            }
        }
        return StringColumnVector.hashBytes(buf, offset, len);
    }

    /**
     * Resolves the string key and updates running statistics in a single contiguous probe.
     *
     * @param buf      source byte array
     * @param offset   start offset of the key
     * @param len      length in bytes of the key
     * @param val      numeric metric value to accumulate
     * @return group ID
     */
    public int accumulate(byte[] buf, int offset, int len, double val) {
        final boolean shortKey = len <= SSO_MAX_LEN;
        final long p1;
        final long p2;
        final int h;
        if (shortKey) {
            p1 = loadWord(buf, offset, len, 0);
            if (len <= 8) {
                p2 = 0L;
                h = fastHash8(p1, len);
            } else {
                p2 = loadWord(buf, offset, len, 1);
                h = StringColumnVector.hashShortWords(p1, p2, len);
            }
        } else {
            p1 = 0L;
            p2 = 0L;
            h = StringColumnVector.hashBytes(buf, offset, len);
        }

        return accumulateWithParsedKey(buf, offset, len, val, shortKey, p1, p2, h);
    }

    public int accumulateWithHash(byte[] buf, int offset, int len, double val, int h) {
        final boolean shortKey = len <= SSO_MAX_LEN;
        final long p1;
        final long p2;
        if (shortKey) {
            p1 = loadWord(buf, offset, len, 0);
            p2 = (len <= 8) ? 0L : loadWord(buf, offset, len, 1);
        } else {
            p1 = 0L;
            p2 = 0L;
        }
        return accumulateWithParsedKey(buf, offset, len, val, shortKey, p1, p2, h);
    }

    private int accumulateWithParsedKey(byte[] buf, int offset, int len, double val,
                                        boolean shortKey, long p1, long p2, int h) {
        int pos = h & mask;
        final long targetTag = ((long) h << 32);

        while (true) {
            int base = pos * SLOT_LONGS;
            long meta = slots[base + OFF_META];

            if (meta == EMPTY_SLOT_TAG) {
                int gid = numGroups++;
                if (gid >= keys.length) {
                    grow();
                }
                byte[] kb = new byte[len];
                System.arraycopy(buf, offset, kb, 0, len);
                keyBytes[gid] = kb;
                keys[gid] = new String(kb, StandardCharsets.UTF_8);

                slots[base + OFF_PREFIX1] = p1;
                slots[base + OFF_PREFIX2] = p2;
                slots[base + OFF_META] = targetTag | (gid & 0xFFFFFFFFL);
                slots[base + OFF_LEN] = len;

                // Update metrics directly
                slots[base + OFF_MIN] = Double.doubleToRawLongBits(val);
                slots[base + OFF_MAX] = Double.doubleToRawLongBits(val);
                slots[base + OFF_SUM] = Double.doubleToRawLongBits(val);
                slots[base + OFF_COUNT] = Double.doubleToRawLongBits(1.0);
                gidToSlotBase[gid] = base;

                if (numGroups >= threshold) {
                    resize();
                }
                return gid;
            }

            if (meta == (targetTag | (meta & 0xFFFFFFFFL)) && slots[base + OFF_LEN] == len) {
                if (shortKey) {
                    if (slots[base + OFF_PREFIX1] == p1 && slots[base + OFF_PREFIX2] == p2) {
                        updateSlotMetrics(base, val);
                        return (int) meta;
                    }
                } else {
                    int gid = (int) meta;
                    byte[] kb = keyBytes[gid];
                    if (kb != null && Arrays.equals(buf, offset, offset + len, kb, 0, len)) {
                        updateSlotMetrics(base, val);
                        return gid;
                    }
                }
            }
            pos = (pos + 1) & mask;
        }
    }

    /**
     * Resolves the string key and updates running statistics in a single contiguous probe
     * using fixed-point scaled decimal values.
     */
    public int accumulateScaled(byte[] buf, int offset, int len, long scaledVal, int scale) {
        double divisor = switch (scale) {
            case 1 -> 10.0;
            case 2 -> 100.0;
            case 3 -> 1000.0;
            case 4 -> 10000.0;
            default -> Math.pow(10, scale);
        };
        return accumulate(buf, offset, len, (double) scaledVal / divisor);
    }

    public int accumulateScaledWithHash(byte[] buf, int offset, int len, long scaledVal, int scale, int h) {
        double divisor = switch (scale) {
            case 1 -> 10.0;
            case 2 -> 100.0;
            case 3 -> 1000.0;
            case 4 -> 10000.0;
            default -> Math.pow(10, scale);
        };
        return accumulateWithHash(buf, offset, len, (double) scaledVal / divisor, h);
    }

    private void updateSlotMetrics(int base, double val) {
        double curMin = Double.longBitsToDouble(slots[base + OFF_MIN]);
        double curMax = Double.longBitsToDouble(slots[base + OFF_MAX]);
        double curSum = Double.longBitsToDouble(slots[base + OFF_SUM]);
        double curCnt = Double.longBitsToDouble(slots[base + OFF_COUNT]);

        if (val < curMin) {
            slots[base + OFF_MIN] = Double.doubleToRawLongBits(val);
        }
        if (val > curMax) {
            slots[base + OFF_MAX] = Double.doubleToRawLongBits(val);
        }
        slots[base + OFF_SUM] = Double.doubleToRawLongBits(curSum + val);
        slots[base + OFF_COUNT] = Double.doubleToRawLongBits(curCnt + 1.0);
    }

    public int findOrInsertBytes(byte[] buf, int offset, int len) {
        int base = findOrInsertSlotBytes(buf, offset, len);
        return (int) this.slots[base + OFF_META];
    }

    public int findOrInsertSlotBytes(byte[] buf, int offset, int len) {
        final boolean shortKey = len <= SSO_MAX_LEN;
        final long p1;
        final long p2;
        final int h;
        if (shortKey) {
            p1 = loadWord(buf, offset, len, 0);
            if (len <= 8) {
                p2 = 0L;
                h = fastHash8(p1, len);
            } else {
                p2 = loadWord(buf, offset, len, 1);
                h = StringColumnVector.hashShortWords(p1, p2, len);
            }
        } else {
            p1 = 0L;
            p2 = 0L;
            h = StringColumnVector.hashBytes(buf, offset, len);
        }

        return findOrInsertBytesWithParsedKey(buf, offset, len, shortKey, p1, p2, h);
    }

    public int findOrInsertBytesWithHash(byte[] buf, int offset, int len, int h) {
        final boolean shortKey = len <= SSO_MAX_LEN;
        final long p1;
        final long p2;
        if (shortKey) {
            p1 = loadWord(buf, offset, len, 0);
            p2 = (len <= 8) ? 0L : loadWord(buf, offset, len, 1);
        } else {
            p1 = 0L;
            p2 = 0L;
        }
        return findOrInsertBytesWithParsedKey(buf, offset, len, shortKey, p1, p2, h);
    }

    private int findOrInsertBytesWithParsedKey(byte[] buf, int offset, int len,
                                               boolean shortKey, long p1, long p2, int h) {
        int slotBase = findOrInsertSlotBase(buf, offset, len, shortKey, p1, p2, h);
        return (int) slots[slotBase + OFF_META];
    }

    public int findOrInsertSlotBase(byte[] buf, int offset, int len,
                                    boolean shortKey, long p1, long p2, int h) {
        int pos = h & mask;
        final long targetTag = ((long) h << 32);

        while (true) {
            int base = pos * SLOT_LONGS;
            long meta = slots[base + OFF_META];

            if (meta == EMPTY_SLOT_TAG) {
                int gid = numGroups++;
                if (gid >= keys.length) {
                    grow();
                }
                byte[] kb = new byte[len];
                System.arraycopy(buf, offset, kb, 0, len);
                keyBytes[gid] = kb;
                keys[gid] = new String(kb, StandardCharsets.UTF_8);

                slots[base + OFF_PREFIX1] = p1;
                slots[base + OFF_PREFIX2] = p2;
                slots[base + OFF_META] = targetTag | (gid & 0xFFFFFFFFL);
                slots[base + OFF_LEN] = len;
                gidToSlotBase[gid] = base;

                if (numGroups >= threshold) {
                    resize();
                    // After resize, locate new base
                    return gidToSlotBase[gid];
                }
                return base;
            }

            if (meta == (targetTag | (meta & 0xFFFFFFFFL)) && slots[base + OFF_LEN] == len) {
                if (shortKey) {
                    if (slots[base + OFF_PREFIX1] == p1 && slots[base + OFF_PREFIX2] == p2) {
                        return base;
                    }
                } else {
                    int gid = (int) meta;
                    byte[] kb = keyBytes[gid];
                    if (kb != null && Arrays.equals(buf, offset, offset + len, kb, 0, len)) {
                        return base;
                    }
                }
            }
            pos = (pos + 1) & mask;
        }
    }

    public int findOrInsertString(String s) {
        if (s == null) {
            return findOrInsertNull();
        }
        byte[] kb = s.getBytes(StandardCharsets.UTF_8);
        return findOrInsertBytes(kb, 0, kb.length);
    }

    public int findOrInsertSlotString(String s) {
        if (s == null) {
            return findOrInsertSlotNull();
        }
        byte[] kb = s.getBytes(StandardCharsets.UTF_8);
        return findOrInsertSlotBytes(kb, 0, kb.length);
    }

    @Override
    public int findOrInsertNull() {
        int base = findOrInsertSlotNull();
        return (int) this.slots[base + OFF_META];
    }

    public int findOrInsertSlotNull() {
        int pos = 0;
        final long nullTag = 0L;
        while (true) {
            int base = pos * SLOT_LONGS;
            long meta = slots[base + OFF_META];
            if (meta == EMPTY_SLOT_TAG) {
                int gid = numGroups++;
                if (gid >= keys.length) grow();
                keys[gid] = null;
                keyBytes[gid] = null;
                slots[base + OFF_PREFIX1] = 0L;
                slots[base + OFF_PREFIX2] = 0L;
                slots[base + OFF_META] = nullTag | (gid & 0xFFFFFFFFL);
                slots[base + OFF_LEN] = -1;
                gidToSlotBase[gid] = base;
                if (numGroups >= threshold) {
                    resize();
                    return gidToSlotBase[gid];
                }
                return base;
            }
            if (meta == (nullTag | (meta & 0xFFFFFFFFL)) && slots[base + OFF_LEN] == -1) {
                int gid = (int) meta;
                if (keys[gid] == null) {
                    return base;
                }
            }
            pos = (pos + 1) & mask;
        }
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
        if (other instanceof FusedGroupAggTable fot) {
            return findOrInsertString(fot.keys[otherGid]);
        }
        if (other instanceof CompactRunningGroupBy.CompactStringTable cst) {
            return findOrInsertString(cst.getStringKey(otherGid));
        }
        return 0;
    }

    public String getKey(int gid) {
        return (gid >= 0 && gid < numGroups) ? keys[gid] : null;
    }

    public byte[] getKeyBytes(int gid) {
        return (gid >= 0 && gid < numGroups) ? keyBytes[gid] : null;
    }

    /**
     * Populates destination interleaved stats array with metrics from slots indexed by gid.
     *
     * @param destStats     stats array of size (numGroups * InterleavedNumericAccumulator.STATS_SIZE)
     * @param destHasValues hasValues array of size (numGroups)
     */
    public void populateStats(double[] destStats, boolean[] destHasValues) {
        populateStatsWithOffset(destStats, destHasValues, 0);
    }

    /**
     * Populates destination interleaved stats array with metrics offset by a given gidOffset.
     */
    public void populateStatsWithOffset(double[] destStats, boolean[] destHasValues, int gidOffset) {
        for (int i = 0; i < capacity; i++) {
            int base = i * SLOT_LONGS;
            long meta = slots[base + OFF_META];
            if (meta != EMPTY_SLOT_TAG) {
                int localGid = (int) meta;
                if (localGid >= 0 && localGid < numGroups) {
                    double cnt = Double.longBitsToDouble(slots[base + OFF_COUNT]);
                    if (cnt > 0.0) {
                        int destGid = localGid + gidOffset;
                        int destBase = destGid * InterleavedNumericAccumulator.STATS_SIZE;
                        double slotMin = Double.longBitsToDouble(slots[base + OFF_MIN]);
                        double slotMax = Double.longBitsToDouble(slots[base + OFF_MAX]);
                        double slotSum = Double.longBitsToDouble(slots[base + OFF_SUM]);

                        if (!destHasValues[destGid] || slotMin < destStats[destBase + InterleavedNumericAccumulator.OFF_MIN]) {
                            destStats[destBase + InterleavedNumericAccumulator.OFF_MIN] = slotMin;
                        }
                        if (!destHasValues[destGid] || slotMax > destStats[destBase + InterleavedNumericAccumulator.OFF_MAX]) {
                            destStats[destBase + InterleavedNumericAccumulator.OFF_MAX] = slotMax;
                        }
                        destStats[destBase + InterleavedNumericAccumulator.OFF_SUM] += slotSum;
                        destStats[destBase + InterleavedNumericAccumulator.OFF_COUNT] += cnt;
                        destHasValues[destGid] = true;
                    }
                }
            }
        }
    }

    /**
     * Merges metrics directly into this table's slot for a known key.
     */
    public void mergeSlotDirect(byte[] buf, String knownKey, int len, int h,
                                double oMin, double oMax, double oSum, double oCnt) {
        final int slotBase;
        if (buf != null) {
            final boolean shortKey = len <= SSO_MAX_LEN;
            final long p1;
            final long p2;
            if (shortKey) {
                p1 = loadWord(buf, 0, len, 0);
                p2 = (len <= 8) ? 0L : loadWord(buf, 0, len, 1);
            } else {
                p1 = 0L;
                p2 = 0L;
            }
            slotBase = findOrInsertSlotBase(buf, 0, len, shortKey, p1, p2, h);
        } else if (knownKey == null) {
            int gid = findOrInsertNull();
            slotBase = findSlotByGid(gid);
        } else {
            byte[] kb = knownKey.getBytes(StandardCharsets.UTF_8);
            final boolean shortKey = kb.length <= SSO_MAX_LEN;
            final long p1;
            final long p2;
            if (shortKey) {
                p1 = loadWord(kb, 0, kb.length, 0);
                p2 = (kb.length <= 8) ? 0L : loadWord(kb, 0, kb.length, 1);
            } else {
                p1 = 0L;
                p2 = 0L;
            }
            slotBase = findOrInsertSlotBase(kb, 0, kb.length, shortKey, p1, p2, h);
        }

        double curMin = Double.longBitsToDouble(slots[slotBase + OFF_MIN]);
        double curMax = Double.longBitsToDouble(slots[slotBase + OFF_MAX]);
        double curSum = Double.longBitsToDouble(slots[slotBase + OFF_SUM]);
        double curCnt = Double.longBitsToDouble(slots[slotBase + OFF_COUNT]);

        if (curCnt == 0.0 || oMin < curMin) {
            slots[slotBase + OFF_MIN] = Double.doubleToRawLongBits(oMin);
        }
        if (curCnt == 0.0 || oMax > curMax) {
            slots[slotBase + OFF_MAX] = Double.doubleToRawLongBits(oMax);
        }
        slots[slotBase + OFF_SUM] = Double.doubleToRawLongBits(curSum + oSum);
        slots[slotBase + OFF_COUNT] = Double.doubleToRawLongBits(curCnt + oCnt);
    }

    /**
     * Merges metrics and keys from another {@link FusedGroupAggTable}.
     */
    public void merge(FusedGroupAggTable other) {
        if (other == null || other.numGroups == 0) return;
        for (int i = 0; i < other.capacity; i++) {
            int oBase = i * SLOT_LONGS;
            long oMeta = other.slots[oBase + OFF_META];
            if (oMeta != EMPTY_SLOT_TAG) {
                int otherGid = (int) oMeta;
                double oCnt = Double.longBitsToDouble(other.slots[oBase + OFF_COUNT]);
                if (oCnt > 0.0) {
                    double oMin = Double.longBitsToDouble(other.slots[oBase + OFF_MIN]);
                    double oMax = Double.longBitsToDouble(other.slots[oBase + OFF_MAX]);
                    double oSum = Double.longBitsToDouble(other.slots[oBase + OFF_SUM]);

                    byte[] kb = other.keyBytes[otherGid];
                    int slotBase = findSlotForMerge(kb, other.keys[otherGid], (int) other.slots[oBase + OFF_LEN]);

                    double curMin = Double.longBitsToDouble(slots[slotBase + OFF_MIN]);
                    double curMax = Double.longBitsToDouble(slots[slotBase + OFF_MAX]);
                    double curSum = Double.longBitsToDouble(slots[slotBase + OFF_SUM]);
                    double curCnt = Double.longBitsToDouble(slots[slotBase + OFF_COUNT]);

                    if (curCnt == 0.0 || oMin < curMin) {
                        slots[slotBase + OFF_MIN] = Double.doubleToRawLongBits(oMin);
                    }
                    if (curCnt == 0.0 || oMax > curMax) {
                        slots[slotBase + OFF_MAX] = Double.doubleToRawLongBits(oMax);
                    }
                    slots[slotBase + OFF_SUM] = Double.doubleToRawLongBits(curSum + oSum);
                    slots[slotBase + OFF_COUNT] = Double.doubleToRawLongBits(curCnt + oCnt);
                } else {
                    // Empty metrics group
                    byte[] kb = other.keyBytes[otherGid];
                    findSlotForMerge(kb, other.keys[otherGid], (int) other.slots[oBase + OFF_LEN]);
                }
            }
        }
    }

    private int findSlotForMerge(byte[] buf, String knownKey, int len) {
        if (buf == null && knownKey == null) {
            return findOrInsertSlotNull();
        }
        if (buf != null) {
            return findOrInsertSlotBytes(buf, 0, len);
        }
        return findOrInsertSlotString(knownKey);
    }

    public void updateMetricsForGid(int gid, double val) {
        if (gid >= 0 && gid < numGroups) {
            updateSlotMetrics(gidToSlotBase[gid], val);
        }
    }

    public void updateMetricsScaledForGid(int gid, long scaledVal, int scale) {
        if (gid >= 0 && gid < numGroups) {
            double divisor = switch (scale) {
                case 1 -> 10.0;
                case 2 -> 100.0;
                case 3 -> 1000.0;
                case 4 -> 10000.0;
                default -> Math.pow(10, scale);
            };
            updateSlotMetrics(gidToSlotBase[gid], (double) scaledVal / divisor);
        }
    }

    private int findSlotByGid(int targetGid) {
        if (targetGid >= 0 && targetGid < numGroups) {
            return gidToSlotBase[targetGid];
        }
        for (int i = 0; i < capacity; i++) {
            int base = i * SLOT_LONGS;
            long meta = slots[base + OFF_META];
            if (meta != EMPTY_SLOT_TAG && ((int) meta) == targetGid) {
                return base;
            }
        }
        throw new IllegalStateException("Slot not found for gid " + targetGid);
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
        gidToSlotBase = Arrays.copyOf(gidToSlotBase, newCap);
    }

    private void resize() {
        int newCap = capacity * 2;
        int newMask = newCap - 1;
        long[] newSlots = new long[newCap * SLOT_LONGS];
        initSlots(newSlots, newCap);

        for (int i = 0; i < capacity; i++) {
            int oldBase = i * SLOT_LONGS;
            long meta = slots[oldBase + OFF_META];
            if (meta != EMPTY_SLOT_TAG) {
                int h = (int) (meta >>> 32);
                int pos = h & newMask;
                while (newSlots[pos * SLOT_LONGS + OFF_META] != EMPTY_SLOT_TAG) {
                    pos = (pos + 1) & newMask;
                }
                int newBase = pos * SLOT_LONGS;
                System.arraycopy(slots, oldBase, newSlots, newBase, SLOT_LONGS);
                int gid = (int) meta;
                gidToSlotBase[gid] = newBase;
            }
        }
        this.slots = newSlots;
        this.capacity = newCap;
        this.mask = newMask;
        this.threshold = (int) (newCap * LOAD_FACTOR);
    }
}
