package com.cupoftea.table.vector;

import static com.cupoftea.table.Aggregations.count;
import static com.cupoftea.table.Aggregations.sum;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;

/**
 * Tests for 64-bit SWAR block hashing in {@link StringColumnVector#hashRow(int)}
 * and {@link StringColumnVector#hashBytes(byte[], int, int)}.
 */
class StringColumnVectorHashTest {

    @Test
    @DisplayName("Null rows and empty strings return hash 0")
    void testNullAndEmptyHashZero() {
        StringColumnVector vec = new StringColumnVector("s", 4, true);
        vec.appendNull();
        vec.appendString("");
        vec.appendUtf8Bytes(new byte[]{1, 2, 3}, 1, 0);

        assertThat(vec.hashRow(0)).isZero();
        assertThat(vec.hashRow(1)).isZero();
        assertThat(vec.hashRow(2)).isZero();
        assertThat(StringColumnVector.hashBytes(null, 0, 5)).isZero();
        assertThat(StringColumnVector.hashBytes(new byte[8], 0, 0)).isZero();
    }

    @Test
    @DisplayName("Hashes are invariant to buffer offset, alignment, and surrounding garbage bytes across lengths 1..128")
    void testAlignmentAndBufferBoundaryInvariance() {
        Random rng = new Random(228L);
        for (int len = 1; len <= 128; len++) {
            byte[] payload = new byte[len];
            rng.nextBytes(payload);

            // 1. Tight exact-length array (exercises end == buf.length; for len < 8 exercises <8 fallback, for len >= 8 exercises end - 8 shift)
            int expected = StringColumnVector.hashBytes(payload, 0, len);

            // 2. Embedded inside a larger buffer with non-zero surrounding garbage at various alignments (0..15)
            for (int offset = 0; offset < 16; offset++) {
                // Case A: plenty of trailing room (i + 8 <= buf.length)
                byte[] padded = new byte[offset + len + 32];
                Arrays.fill(padded, (byte) 0xA5);
                System.arraycopy(payload, 0, padded, offset, len);
                assertThat(StringColumnVector.hashBytes(padded, offset, len))
                        .as("padded buffer hash for len=%d offset=%d", len, offset)
                        .isEqualTo(expected);

                // Case B: slice ends right at the end of the buffer (i + 8 > buf.length for tail)
                byte[] flushEnd = new byte[offset + len];
                Arrays.fill(flushEnd, (byte) 0x5A);
                System.arraycopy(payload, 0, flushEnd, offset, len);
                assertThat(StringColumnVector.hashBytes(flushEnd, offset, len))
                        .as("flush-end buffer hash for len=%d offset=%d", len, offset)
                        .isEqualTo(expected);
            }

            // 3. Stored inside StringColumnVector at multiple rows
            StringColumnVector vec = new StringColumnVector("col", 4, true);
            vec.appendString("prefix_" + len);
            vec.appendUtf8Bytes(payload, 0, len);
            vec.appendString("suffix_" + len);
            vec.appendUtf8Bytes(payload, 0, len);

            assertThat(vec.hashRow(1)).isEqualTo(expected);
            assertThat(vec.hashRow(3)).isEqualTo(expected);

            if (len <= 16) {
                long p1 = 0L;
                long p2 = 0L;
                for (int k = Math.min(len, 8) - 1; k >= 0; k--) {
                    p1 = (p1 << 8) | (payload[k] & 0xFFL);
                }
                for (int k = len - 1; k >= 8; k--) {
                    p2 = (p2 << 8) | (payload[k] & 0xFFL);
                }
                assertThat(StringColumnVector.hashShortWords(p1, p2, len))
                        .as("hashShortWords equivalence for len=%d", len)
                        .isEqualTo(expected);
            }
        }
        assertThat(StringColumnVector.hashShortWords(0L, 0L, 0)).isZero();
    }

    @Test
    @DisplayName("Strings differing only by trailing NUL bytes or single bit flips produce distinct hashes")
    void testTrailingZeroBytesAndSingleBitSensitivity() {
        byte[] a1 = new byte[]{'a'};
        byte[] a2 = new byte[]{'a', 0};
        byte[] a3 = new byte[]{'a', 0, 0, 0, 0, 0, 0, 0, 0};

        int h1 = StringColumnVector.hashBytes(a1, 0, a1.length);
        int h2 = StringColumnVector.hashBytes(a2, 0, a2.length);
        int h3 = StringColumnVector.hashBytes(a3, 0, a3.length);

        assertThat(h1).isNotEqualTo(h2);
        assertThat(h2).isNotEqualTo(h3);
        assertThat(h1).isNotEqualTo(h3);

        // Single bit flip across a 23-byte payload (2 x 8-byte blocks + 7-byte tail)
        byte[] base = " CupOfTea-SWAR-Hash-228".getBytes(StandardCharsets.UTF_8);
        int baseHash = StringColumnVector.hashBytes(base, 0, base.length);
        for (int byteIdx = 0; byteIdx < base.length; byteIdx++) {
            for (int bit = 0; bit < 8; bit++) {
                byte[] mutated = base.clone();
                mutated[byteIdx] ^= (byte) (1 << bit);
                int mutatedHash = StringColumnVector.hashBytes(mutated, 0, mutated.length);
                assertThat(mutatedHash)
                        .as("bit flip at byte=%d bit=%d", byteIdx, bit)
                        .isNotEqualTo(baseHash);
            }
        }
    }

    @Test
    @DisplayName("Low collision distribution across 10,000 sequential keys of varying lengths")
    void testCollisionDistribution() {
        int count = 10_000;
        StringColumnVector vec = new StringColumnVector("keys", count, false);
        for (int i = 0; i < count; i++) {
            vec.appendString("station_key_" + i + "_region_" + (i % 97));
        }

        Set<Integer> uniqueHashes = new HashSet<>(count * 2);
        for (int i = 0; i < count; i++) {
            uniqueHashes.add(vec.hashRow(i));
        }
        // With 10,000 keys in 2^32 space, birthday paradox expects ~0.01 collisions; >9,990 unique is well within bounds
        assertThat(uniqueHashes.size()).isGreaterThan(9_990);
    }

    @Test
    @DisplayName("GroupBy, LazyGroupBy, and DictionaryColumnVector remain consistent with SWAR string hashing")
    void testGroupByAndDictionaryConsistency() {
        String[] keys = {
            "a", "ab", "abc", "abcd", "abcde", "abcdef", "abcdefg",
            "abcdefgh", "abcdefghi", "abcdefghijklmno", "abcdefghijklmnop",
            "abcdefghijklmnopqrstuvwx", "☕🍵🚀-utf8-long-string-key-with-tail"
        };

        CupOfTea df = new CupOfTea();
        df.addColumn("k", String.class);
        df.addColumn("v", Integer.class);

        StringColumnVector kVec = (StringColumnVector) df.getDataFrame().getColumnVector("k");
        IntColumnVector vVec = (IntColumnVector) df.getDataFrame().getColumnVector("v");

        for (int round = 0; round < 20; round++) {
            for (int i = 0; i < keys.length; i++) {
                kVec.appendString(keys[i]);
                vVec.appendInt(i + 1);
            }
        }

        // 1. Eager GroupBy
        CupOfTea grouped = df.groupBy("k").agg(
                count().as("cnt"),
                sum("v").as("total")
        );
        assertThat(grouped.size()).isEqualTo(keys.length);

        // 2. Lazy GroupBy (uses CompactRunningGroupBy.CompactStringTable)
        CupOfTea lazyGrouped = df.lazy()
                .groupBy("k")
                .agg(count().as("cnt"), sum("v").as("total"))
                .collect();
        assertThat(lazyGrouped.size()).isEqualTo(keys.length);

        // 3. Dictionary encoding
        DictionaryColumnVector dict = kVec.toDictionary();
        assertThat(dict.getDictionarySize()).isEqualTo(keys.length);
        for (int r = 0; r < kVec.size(); r++) {
            assertThat(dict.getString(r)).isEqualTo(keys[r % keys.length]);
        }
    }
}
