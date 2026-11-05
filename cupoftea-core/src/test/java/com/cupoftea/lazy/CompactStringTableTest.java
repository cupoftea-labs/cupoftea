package com.cupoftea.lazy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class CompactStringTableTest {

    @Test
    @DisplayName("SSO table distinguishes short, boundary, long, empty and null keys across resizes")
    void shortAndLongKeys() {
        CompactRunningGroupBy.CompactStringTable table = new CompactRunningGroupBy.CompactStringTable(4);
        Map<String, Integer> expected = new HashMap<>();
        String[] keys = new String[3000];
        for (int i = 0; i < keys.length; i++) {
            int len = i % 40; // 0..39 covers <8, 8, 9..16, 17+
            StringBuilder sb = new StringBuilder("k" + i);
            while (sb.length() < len) {
                sb.append('x');
            }
            keys[i] = sb.toString();
        }
        for (int round = 0; round < 2; round++) {
            for (String k : keys) {
                byte[] b = k.getBytes(StandardCharsets.UTF_8);
                int gid = table.findOrInsertBytes(b, 0, b.length);
                Integer prev = expected.putIfAbsent(k, gid);
                if (prev != null) {
                    assertThat(gid).isEqualTo(prev);
                }
            }
        }
        assertThat(table.numGroups()).isEqualTo(expected.size());
        assertThat(table.findOrInsertString("")).isEqualTo(table.findOrInsertString(""));
        int nullGid = table.findOrInsertNull();
        assertThat(table.findOrInsertNull()).isEqualTo(nullGid);
        assertThat(expected.values()).doesNotContain(nullGid);
        // same prefix, different length (zero-padding must not collide)
        byte[] a = {'a', 'b'};
        byte[] b = {'a', 'b', 0};
        assertThat(table.findOrInsertBytes(a, 0, 2)).isNotEqualTo(table.findOrInsertBytes(b, 0, 3));
        // string path agrees with bytes path
        assertThat(table.findOrInsertString(keys[17])).isEqualTo(expected.get(keys[17]));
    }
}
