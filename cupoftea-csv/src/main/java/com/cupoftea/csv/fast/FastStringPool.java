package com.cupoftea.csv.fast;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A fast, adaptive string cache for CSV decoding to avoid duplicate String instances
 * for repeated low-cardinality categorical columns (e.g. city names, stations, status codes),
 * while avoiding allocation overhead on high-cardinality text columns.
 */
public final class FastStringPool {

    private static final int INITIAL_CAPACITY = 4096; // Power of 2, fits in L1/L2 cache
    private static final int EXPANDED_CAPACITY = 65536; // Power of 2, accommodates 40K+ categories (e.g. 1BRC)
    private static final int MAX_POOLED_LENGTH = 64;

    private byte[][] keys;
    private String[] values;
    private int[] keyStart;
    private int[] keyLen;
    private int mask;
    private final Charset charset;
    private final byte[] sharedBuffer;

    private int lookups = 0;
    private int hits = 0;
    private boolean bypassed = false;

    public FastStringPool(Charset charset) {
        this(charset, null);
    }

    public FastStringPool(Charset charset, byte[] sharedBuffer) {
        this.charset = charset != null ? charset : StandardCharsets.UTF_8;
        this.sharedBuffer = sharedBuffer;
        this.mask = INITIAL_CAPACITY - 1;
        this.values = new String[INITIAL_CAPACITY];
        if (sharedBuffer != null) {
            this.keyStart = new int[INITIAL_CAPACITY];
            this.keyLen = new int[INITIAL_CAPACITY];
        } else {
            this.keys = new byte[INITIAL_CAPACITY][];
        }
    }

    public String getOrCreate(byte[] buf, int start, int length) {
        if (length == 0) {
            return "";
        }
        if (bypassed || length > MAX_POOLED_LENGTH) {
            return new String(buf, start, length, charset);
        }
        if (sharedBuffer != null && buf != sharedBuffer) {
            return new String(buf, start, length, charset);
        }

        if (++lookups == 2048) {
            if (hits < 4) {
                // High-cardinality / unique text column: bypass pool completely
                bypassed = true;
                return new String(buf, start, length, charset);
            } else if (hits < 1536) {
                // Medium/large categorical dictionary (e.g. 40K weather stations): expand to 64K slots
                expandTo(EXPANDED_CAPACITY);
            }
        }

        int hash = 0x811c9dc5;
        int end = start + length;
        for (int i = start; i < end; i++) {
            hash = (hash ^ buf[i]) * 0x01000193;
        }
        int localMask = this.mask;
        int slot = (hash ^ (hash >>> 15)) & localMask;

        if (sharedBuffer != null) {
            String cached = values[slot];
            if (cached != null && keyLen[slot] == length) {
                int kStart = keyStart[slot];
                if (Arrays.equals(sharedBuffer, start, end, sharedBuffer, kStart, kStart + length)) {
                    hits++;
                    return cached;
                }
            }
            int nextSlot = (slot + 1) & localMask;
            String nextCached = values[nextSlot];
            if (nextCached != null && keyLen[nextSlot] == length) {
                int kStart = keyStart[nextSlot];
                if (Arrays.equals(sharedBuffer, start, end, sharedBuffer, kStart, kStart + length)) {
                    hits++;
                    return nextCached;
                }
            }
            if (cached != null && nextCached == null) {
                slot = nextSlot;
            }
            String str = new String(sharedBuffer, start, length, charset);
            keyStart[slot] = start;
            keyLen[slot] = length;
            values[slot] = str;
            return str;
        }

        byte[] key = keys[slot];
        if (key != null && key.length == length && Arrays.equals(buf, start, end, key, 0, length)) {
            hits++;
            return values[slot];
        }

        int nextSlot = (slot + 1) & localMask;
        byte[] nextKey = keys[nextSlot];
        if (nextKey != null && nextKey.length == length && Arrays.equals(buf, start, end, nextKey, 0, length)) {
            hits++;
            return values[nextSlot];
        }
        if (key != null && nextKey == null) {
            slot = nextSlot;
        }

        String str = new String(buf, start, length, charset);
        keys[slot] = Arrays.copyOfRange(buf, start, end);
        values[slot] = str;
        return str;
    }

    private void expandTo(int newCap) {
        int newMask = newCap - 1;
        String[] newValues = new String[newCap];
        if (sharedBuffer != null) {
            int[] newKeyStart = new int[newCap];
            int[] newKeyLen = new int[newCap];
            for (int i = 0; i < values.length; i++) {
                String v = values[i];
                if (v != null) {
                    int ks = keyStart[i];
                    int kl = keyLen[i];
                    int h = 0x811c9dc5;
                    int end = ks + kl;
                    for (int p = ks; p < end; p++) {
                        h = (h ^ sharedBuffer[p]) * 0x01000193;
                    }
                    int s = (h ^ (h >>> 15)) & newMask;
                    if (newValues[s] != null) {
                        s = (s + 1) & newMask;
                    }
                    newValues[s] = v;
                    newKeyStart[s] = ks;
                    newKeyLen[s] = kl;
                }
            }
            this.keyStart = newKeyStart;
            this.keyLen = newKeyLen;
        } else {
            byte[][] newKeys = new byte[newCap][];
            for (int i = 0; i < values.length; i++) {
                String v = values[i];
                if (v != null) {
                    byte[] k = keys[i];
                    int h = 0x811c9dc5;
                    for (byte b : k) {
                        h = (h ^ b) * 0x01000193;
                    }
                    int s = (h ^ (h >>> 15)) & newMask;
                    if (newValues[s] != null) {
                        s = (s + 1) & newMask;
                    }
                    newValues[s] = v;
                    newKeys[s] = k;
                }
            }
            this.keys = newKeys;
        }
        this.values = newValues;
        this.mask = newMask;
    }
}
