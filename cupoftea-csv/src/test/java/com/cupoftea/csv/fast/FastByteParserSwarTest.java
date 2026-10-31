package com.cupoftea.csv.fast;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;

import org.junit.jupiter.api.Test;

class FastByteParserSwarTest {

    @Test
    void indexOfByteMatchesNaiveScan() {
        Random rnd = new Random(42);
        for (int iter = 0; iter < 2000; iter++) {
            byte[] buf = new byte[rnd.nextInt(40)];
            for (int i = 0; i < buf.length; i++) {
                buf[i] = (byte) (rnd.nextInt(4) == 0 ? ',' : rnd.nextInt(256));
            }
            int start = buf.length == 0 ? 0 : rnd.nextInt(buf.length);
            int expected = -1;
            for (int i = start; i < buf.length; i++) {
                if (buf[i] == ',') {
                    expected = i;
                    break;
                }
            }
            assertThat(FastByteParser.indexOfByte(buf, start, buf.length, (byte) ',')).isEqualTo(expected);
        }
    }

    @Test
    void indexOfStructuralFindsFirstSpecial() {
        byte[] buf = "abcdefghijklmnop,qrs\nt".getBytes();
        assertThat(FastByteParser.indexOfStructural(buf, 0, buf.length, (byte) ',', (byte) '"', (byte) '\\'))
                .isEqualTo(16);
        assertThat(FastByteParser.indexOfStructural(buf, 17, buf.length, (byte) ',', (byte) '"', (byte) '\\'))
                .isEqualTo(20);
        assertThat(FastByteParser.indexOfStructural(buf, 21, buf.length, (byte) ',', (byte) '"', (byte) '\\'))
                .isEqualTo(buf.length);
    }

    @Test
    void highBitBytesDoNotCauseFalsePositives() {
        byte[] buf = new byte[24];
        java.util.Arrays.fill(buf, (byte) 0x81);
        buf[19] = ';';
        assertThat(FastByteParser.indexOfByte(buf, 0, buf.length, (byte) ';')).isEqualTo(19);
    }
}
