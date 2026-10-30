package com.cupoftea.table;

import java.util.Arrays;
import java.util.Objects;

import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * High-performance primitive-backed open-addressing hash table for GroupBy partitioning.
 * Stores representative row indices rather than object keys, eliminating key allocation
 * and pointer chasing.
 */
public class GroupHashTable {

    private static final float LOAD_FACTOR = 0.70f;
    private static final int MIN_CAPACITY = 16;

    private int capacity;
    private int mask;
    private int threshold;
    private int numGroups;

    // table[pos] stores representative row index of group, or -1 if empty.
    private int[] table;
    private int[] hashes;
    private int[] groupIds;

    // firstRowOfGroup[g] stores the representative row index of group g.
    private int[] firstRowOfGroup;

    private final ColumnVector[] keyVectors;
    private final boolean isSingleKey;
    private final ColumnVector singleKey;
    private final KeyType singleKeyType;

    public enum KeyType {
        INT, LONG, DOUBLE, FLOAT, BOOLEAN, STRING, OBJECT, MULTI
    }

    public GroupHashTable(ColumnVector[] keyVectors, int expectedRows) {
        if (keyVectors == null || keyVectors.length == 0) {
            throw new IllegalArgumentException("At least one key vector must be provided");
        }
        this.keyVectors = keyVectors;
        this.isSingleKey = keyVectors.length == 1;
        this.singleKey = isSingleKey ? keyVectors[0] : null;

        if (isSingleKey) {
            if (singleKey instanceof IntColumnVector) singleKeyType = KeyType.INT;
            else if (singleKey instanceof LongColumnVector) singleKeyType = KeyType.LONG;
            else if (singleKey instanceof DoubleColumnVector) singleKeyType = KeyType.DOUBLE;
            else if (singleKey instanceof FloatColumnVector) singleKeyType = KeyType.FLOAT;
            else if (singleKey instanceof BooleanColumnVector) singleKeyType = KeyType.BOOLEAN;
            else if (singleKey instanceof StringColumnVector) singleKeyType = KeyType.STRING;
            else singleKeyType = KeyType.OBJECT;
        } else {
            singleKeyType = KeyType.MULTI;
        }

        int targetCap = Math.max(MIN_CAPACITY, nextPowerOfTwo((int) Math.ceil(Math.min(expectedRows, 32768) / LOAD_FACTOR)));
        this.capacity = targetCap;
        this.mask = targetCap - 1;
        this.threshold = (int) (targetCap * LOAD_FACTOR);
        this.numGroups = 0;

        this.table = new int[capacity];
        Arrays.fill(this.table, -1);
        this.hashes = new int[capacity];
        this.groupIds = new int[capacity];
        this.firstRowOfGroup = new int[Math.max(16, targetCap / 4)];
    }

    public int findOrInsert(int row) {
        int h = computeHash(row);
        int pos = h & mask;
        while (true) {
            int repRow = table[pos];
            if (repRow == -1) {
                // Empty slot -> new group
                table[pos] = row;
                hashes[pos] = h;
                int gid = numGroups++;
                groupIds[pos] = gid;
                if (gid >= firstRowOfGroup.length) {
                    firstRowOfGroup = Arrays.copyOf(firstRowOfGroup, firstRowOfGroup.length * 2);
                }
                firstRowOfGroup[gid] = row;
                if (numGroups >= threshold) {
                    resize();
                }
                return gid;
            }
            if (hashes[pos] == h && keysEqual(row, repRow)) {
                return groupIds[pos];
            }
            pos = (pos + 1) & mask;
        }
    }

    public int find(int row) {
        int h = computeHash(row);
        int pos = h & mask;
        while (true) {
            int repRow = table[pos];
            if (repRow == -1) {
                return -1;
            }
            if (hashes[pos] == h && keysEqual(row, repRow)) {
                return groupIds[pos];
            }
            pos = (pos + 1) & mask;
        }
    }

    public int numGroups() {
        return numGroups;
    }

    public int[] getFirstRowOfGroup() {
        return Arrays.copyOf(firstRowOfGroup, numGroups);
    }

    private void resize() {
        int newCap = capacity << 1;
        int newMask = newCap - 1;
        int[] newTable = new int[newCap];
        Arrays.fill(newTable, -1);
        int[] newHashes = new int[newCap];
        int[] newGroupIds = new int[newCap];

        for (int i = 0; i < capacity; i++) {
            int repRow = table[i];
            if (repRow != -1) {
                int h = hashes[i];
                int gid = groupIds[i];
                int pos = h & newMask;
                while (newTable[pos] != -1) {
                    pos = (pos + 1) & newMask;
                }
                newTable[pos] = repRow;
                newHashes[pos] = h;
                newGroupIds[pos] = gid;
            }
        }

        this.table = newTable;
        this.hashes = newHashes;
        this.groupIds = newGroupIds;
        this.capacity = newCap;
        this.mask = newMask;
        this.threshold = (int) (newCap * LOAD_FACTOR);
    }

    private int computeHash(int row) {
        switch (singleKeyType) {
            case INT: {
                IntColumnVector v = (IntColumnVector) singleKey;
                if (v.isNull(row)) return 0;
                return mix(v.getInt(row));
            }
            case LONG: {
                LongColumnVector v = (LongColumnVector) singleKey;
                if (v.isNull(row)) return 0;
                return mix(v.getLong(row));
            }
            case DOUBLE: {
                DoubleColumnVector v = (DoubleColumnVector) singleKey;
                if (v.isNull(row)) return 0;
                double d = v.getDouble(row);
                return mix(Double.doubleToLongBits(d == 0.0 ? 0.0 : d));
            }
            case FLOAT: {
                FloatColumnVector v = (FloatColumnVector) singleKey;
                if (v.isNull(row)) return 0;
                float f = v.getFloat(row);
                return mix(Float.floatToIntBits(f == 0.0f ? 0.0f : f));
            }
            case BOOLEAN: {
                BooleanColumnVector v = (BooleanColumnVector) singleKey;
                if (v.isNull(row)) return 0;
                return v.getBoolean(row) ? 1231 : 1237;
            }
            case STRING: {
                StringColumnVector v = (StringColumnVector) singleKey;
                if (v.isNull(row)) return 0;
                String s = v.getString(row);
                return s != null ? mix(s.hashCode()) : 0;
            }
            case OBJECT: {
                if (singleKey.isNull(row)) return 0;
                Object o = singleKey.getObject(row);
                return (o != null && !NA.isNA(o)) ? mix(o.hashCode()) : 0;
            }
            case MULTI:
            default: {
                int h = 1;
                for (ColumnVector kv : keyVectors) {
                    h = 31 * h + columnHash(kv, row);
                }
                return mix(h);
            }
        }
    }

    private boolean keysEqual(int r1, int r2) {
        if (r1 == r2) return true;
        switch (singleKeyType) {
            case INT: {
                IntColumnVector v = (IntColumnVector) singleKey;
                boolean n1 = v.isNull(r1);
                boolean n2 = v.isNull(r2);
                if (n1 != n2) return false;
                return n1 || v.getInt(r1) == v.getInt(r2);
            }
            case LONG: {
                LongColumnVector v = (LongColumnVector) singleKey;
                boolean n1 = v.isNull(r1);
                boolean n2 = v.isNull(r2);
                if (n1 != n2) return false;
                return n1 || v.getLong(r1) == v.getLong(r2);
            }
            case DOUBLE: {
                DoubleColumnVector v = (DoubleColumnVector) singleKey;
                boolean n1 = v.isNull(r1);
                boolean n2 = v.isNull(r2);
                if (n1 != n2) return false;
                if (n1) return true;
                double d1 = v.getDouble(r1);
                double d2 = v.getDouble(r2);
                return Double.doubleToLongBits(d1 == 0.0 ? 0.0 : d1) == Double.doubleToLongBits(d2 == 0.0 ? 0.0 : d2);
            }
            case FLOAT: {
                FloatColumnVector v = (FloatColumnVector) singleKey;
                boolean n1 = v.isNull(r1);
                boolean n2 = v.isNull(r2);
                if (n1 != n2) return false;
                if (n1) return true;
                float f1 = v.getFloat(r1);
                float f2 = v.getFloat(r2);
                return Float.floatToIntBits(f1 == 0.0f ? 0.0f : f1) == Float.floatToIntBits(f2 == 0.0f ? 0.0f : f2);
            }
            case BOOLEAN: {
                BooleanColumnVector v = (BooleanColumnVector) singleKey;
                boolean n1 = v.isNull(r1);
                boolean n2 = v.isNull(r2);
                if (n1 != n2) return false;
                return n1 || v.getBoolean(r1) == v.getBoolean(r2);
            }
            case STRING: {
                StringColumnVector v = (StringColumnVector) singleKey;
                boolean n1 = v.isNull(r1);
                boolean n2 = v.isNull(r2);
                if (n1 != n2) return false;
                return n1 || Objects.equals(v.getString(r1), v.getString(r2));
            }
            case OBJECT: {
                boolean n1 = singleKey.isNull(r1);
                boolean n2 = singleKey.isNull(r2);
                if (n1 != n2) return false;
                return n1 || Objects.equals(singleKey.getObject(r1), singleKey.getObject(r2));
            }
            case MULTI:
            default: {
                for (ColumnVector kv : keyVectors) {
                    if (!columnEqual(kv, r1, r2)) return false;
                }
                return true;
            }
        }
    }

    private static int columnHash(ColumnVector kv, int r) {
        if (kv.isNull(r)) return 0;
        if (kv instanceof IntColumnVector icv) return icv.getInt(r);
        if (kv instanceof LongColumnVector lcv) {
            long v = lcv.getLong(r);
            return (int) (v ^ (v >>> 32));
        }
        if (kv instanceof DoubleColumnVector dcv) {
            double d = dcv.getDouble(r);
            long v = Double.doubleToLongBits(d == 0.0 ? 0.0 : d);
            return (int) (v ^ (v >>> 32));
        }
        if (kv instanceof FloatColumnVector fcv) {
            float f = fcv.getFloat(r);
            return Float.floatToIntBits(f == 0.0f ? 0.0f : f);
        }
        if (kv instanceof BooleanColumnVector bcv) return bcv.getBoolean(r) ? 1231 : 1237;
        if (kv instanceof StringColumnVector scv) {
            String s = scv.getString(r);
            return s != null ? s.hashCode() : 0;
        }
        Object o = kv.getObject(r);
        return (o != null && !NA.isNA(o)) ? o.hashCode() : 0;
    }

    private static boolean columnEqual(ColumnVector kv, int r1, int r2) {
        boolean n1 = kv.isNull(r1);
        boolean n2 = kv.isNull(r2);
        if (n1 != n2) return false;
        if (n1) return true;
        if (kv instanceof IntColumnVector icv) return icv.getInt(r1) == icv.getInt(r2);
        if (kv instanceof LongColumnVector lcv) return lcv.getLong(r1) == lcv.getLong(r2);
        if (kv instanceof DoubleColumnVector dcv) {
            double d1 = dcv.getDouble(r1);
            double d2 = dcv.getDouble(r2);
            return Double.doubleToLongBits(d1 == 0.0 ? 0.0 : d1) == Double.doubleToLongBits(d2 == 0.0 ? 0.0 : d2);
        }
        if (kv instanceof FloatColumnVector fcv) {
            float f1 = fcv.getFloat(r1);
            float f2 = fcv.getFloat(r2);
            return Float.floatToIntBits(f1 == 0.0f ? 0.0f : f1) == Float.floatToIntBits(f2 == 0.0f ? 0.0f : f2);
        }
        if (kv instanceof BooleanColumnVector bcv) return bcv.getBoolean(r1) == bcv.getBoolean(r2);
        if (kv instanceof StringColumnVector scv) return Objects.equals(scv.getString(r1), scv.getString(r2));
        return Objects.equals(kv.getObject(r1), kv.getObject(r2));
    }

    private static int mix(int h) {
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }

    private static int mix(long v) {
        long h = v;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (int) h;
    }

    private static int nextPowerOfTwo(int n) {
        if (n <= 1) return 1;
        return 1 << (32 - Integer.numberOfLeadingZeros(n - 1));
    }
}
