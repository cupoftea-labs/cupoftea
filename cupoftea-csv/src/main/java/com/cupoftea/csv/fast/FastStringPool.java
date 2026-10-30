package com.cupoftea.csv.fast;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A fast, reusable string cache for CSV decoding to avoid duplicate String instances
 * for repeated low-cardinality categorical columns (e.g. city names, stations, status codes).
 */
public final class FastStringPool {

    private static final int DEFAULT_CAPACITY = 16384; // Power of 2
    private static final int MASK = DEFAULT_CAPACITY - 1;

    private final byte[][] keys = new byte[DEFAULT_CAPACITY][];
    private final String[] values = new String[DEFAULT_CAPACITY];
    private final Charset charset;

    public FastStringPool(Charset charset) {
        this.charset = charset != null ? charset : StandardCharsets.UTF_8;
    }

    public String getOrCreate(byte[] buf, int start, int length) {
        if (length == 0) {
            return "";
        }
        // If string is long, just create it directly without caching
        if (length > 128) {
            return new String(buf, start, length, charset);
        }

        int hash = 1;
        for (int i = 0; i < length; i++) {
            hash = 31 * hash + buf[start + i];
        }

        int slot = hash & MASK;
        byte[] key = keys[slot];
        if (key != null && key.length == length && equals(buf, start, key, length)) {
            return values[slot];
        }

        // Linear probe with max depth 3
        for (int probe = 1; probe <= 3; probe++) {
            int nextSlot = (slot + probe) & MASK;
            byte[] nextKey = keys[nextSlot];
            if (nextKey != null && nextKey.length == length && equals(buf, start, nextKey, length)) {
                return values[nextSlot];
            }
            if (nextKey == null) {
                slot = nextSlot;
                break;
            }
        }

        String str = new String(buf, start, length, charset);
        byte[] newKey = Arrays.copyOfRange(buf, start, start + length);
        keys[slot] = newKey;
        values[slot] = str;
        return str;
    }

    private static boolean equals(byte[] buf, int start, byte[] key, int length) {
        for (int i = 0; i < length; i++) {
            if (buf[start + i] != key[i]) {
                return false;
            }
        }
        return true;
    }
}
