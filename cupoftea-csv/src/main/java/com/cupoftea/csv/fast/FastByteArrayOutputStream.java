package com.cupoftea.csv.fast;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * High-performance un-synchronized direct byte accumulator for parallel CSV serialization.
 * Avoids synchronized blocks and intermediate String/char array overhead.
 */
public final class FastByteArrayOutputStream extends OutputStream {

    private byte[] buf;
    private int count;

    public FastByteArrayOutputStream() {
        this(64 * 1024);
    }

    public FastByteArrayOutputStream(int initialCapacity) {
        this.buf = new byte[Math.max(128, initialCapacity)];
        this.count = 0;
    }

    public void ensureCapacity(int minCapacity) {
        if (minCapacity - buf.length > 0) {
            grow(minCapacity);
        }
    }

    private void grow(int minCapacity) {
        int oldCapacity = buf.length;
        int newCapacity = oldCapacity << 1;
        if (newCapacity - minCapacity < 0) {
            newCapacity = minCapacity;
        }
        buf = Arrays.copyOf(buf, newCapacity);
    }

    @Override
    public void write(int b) {
        ensureCapacity(count + 1);
        buf[count++] = (byte) b;
    }

    public void writeByte(byte b) {
        ensureCapacity(count + 1);
        buf[count++] = b;
    }

    @Override
    public void write(byte[] b, int off, int len) {
        if (len <= 0) return;
        ensureCapacity(count + len);
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    public void writeBytes(byte[] b) {
        if (b == null || b.length == 0) return;
        write(b, 0, b.length);
    }

    /**
     * Appends an ASCII / Latin-1 or UTF-8 String directly into the buffer.
     */
    public void writeAsciiOrUtf8(String s) {
        if (s == null || s.isEmpty()) return;
        int len = s.length();
        ensureCapacity(count + len * 3);
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c < 0x80) {
                buf[count++] = (byte) c;
            } else {
                byte[] utf = s.substring(i).getBytes(StandardCharsets.UTF_8);
                ensureCapacity(count + utf.length);
                System.arraycopy(utf, 0, buf, count, utf.length);
                count += utf.length;
                return;
            }
        }
    }

    private static final byte[] DIGITS_PAIR = new byte[200];
    static {
        for (int i = 0; i < 100; i++) {
            DIGITS_PAIR[i * 2] = (byte) ('0' + (i / 10));
            DIGITS_PAIR[i * 2 + 1] = (byte) ('0' + (i % 10));
        }
    }

    /**
     * High-speed integer serialization into bytes using 2-digit lookup tables without heap allocation.
     */
    public void writeInt(int v) {
        if (v == Integer.MIN_VALUE) {
            writeAsciiOrUtf8("-2147483648");
            return;
        }
        ensureCapacity(count + 12);
        if (v < 0) {
            buf[count++] = '-';
            v = -v;
        }
        int charPos = count + stringSize(v);
        int end = charPos;
        while (v >= 100) {
            int q = v / 100;
            int r = v - (q * 100);
            v = q;
            int pairIdx = r << 1;
            buf[--charPos] = DIGITS_PAIR[pairIdx + 1];
            buf[--charPos] = DIGITS_PAIR[pairIdx];
        }
        if (v >= 10) {
            int pairIdx = v << 1;
            buf[--charPos] = DIGITS_PAIR[pairIdx + 1];
            buf[--charPos] = DIGITS_PAIR[pairIdx];
        } else {
            buf[--charPos] = (byte) ('0' + v);
        }
        count = end;
    }

    /**
     * High-speed long serialization into bytes using 2-digit lookup tables without heap allocation.
     */
    public void writeLong(long v) {
        if (v == Long.MIN_VALUE) {
            writeAsciiOrUtf8("-9223372036854775808");
            return;
        }
        ensureCapacity(count + 21);
        if (v < 0) {
            buf[count++] = '-';
            v = -v;
        }
        int charPos = count + stringSize(v);
        int end = charPos;
        while (v >= 100) {
            long q = v / 100;
            int r = (int) (v - (q * 100));
            v = q;
            int pairIdx = r << 1;
            buf[--charPos] = DIGITS_PAIR[pairIdx + 1];
            buf[--charPos] = DIGITS_PAIR[pairIdx];
        }
        if (v >= 10) {
            int pairIdx = ((int) v) << 1;
            buf[--charPos] = DIGITS_PAIR[pairIdx + 1];
            buf[--charPos] = DIGITS_PAIR[pairIdx];
        } else {
            buf[--charPos] = (byte) ('0' + v);
        }
        count = end;
    }

    private static int stringSize(int x) {
        int p = 10;
        for (int i = 1; i < 10; i++) {
            if (x < p) return i;
            p = 10 * p;
        }
        return 10;
    }

    private static int stringSize(long x) {
        long p = 10;
        for (int i = 1; i < 19; i++) {
            if (x < p) return i;
            p = 10 * p;
        }
        return 19;
    }

    /**
     * Fast float/double formatting into bytes with fixed-point decimal fast paths
     * to eliminate temporary String and char[] heap allocations.
     */
    public void writeDouble(double v) {
        if (Double.isNaN(v)) {
            ensureCapacity(count + 3);
            buf[count++] = 'N'; buf[count++] = 'a'; buf[count++] = 'N';
            return;
        }
        if (v == Double.POSITIVE_INFINITY) {
            writeAsciiOrUtf8("Infinity");
            return;
        }
        if (v == Double.NEGATIVE_INFINITY) {
            writeAsciiOrUtf8("-Infinity");
            return;
        }
        long raw = Double.doubleToRawLongBits(v);
        boolean negative = (raw < 0);
        double absV = negative ? -v : v;

        // Fixed-point fast path for standard metric/financial numbers (1 to 2 decimal places)
        if (absV < 10_000_000_000.0) {
            long scaled10 = Math.round(absV * 10.0);
            if (scaled10 >= 0 && Math.abs(absV - (scaled10 / 10.0)) < 1e-9) {
                ensureCapacity(count + 25);
                if (negative) buf[count++] = '-';
                long intPart = scaled10 / 10;
                int fracPart = (int) (scaled10 % 10);
                writeLongNoCapacity(intPart);
                buf[count++] = '.';
                buf[count++] = (byte) ('0' + fracPart);
                return;
            }
            long scaled100 = Math.round(absV * 100.0);
            if (scaled100 >= 0 && Math.abs(absV - (scaled100 / 100.0)) < 1e-9) {
                ensureCapacity(count + 25);
                if (negative) buf[count++] = '-';
                long intPart = scaled100 / 100;
                int fracPart = (int) (scaled100 % 100);
                writeLongNoCapacity(intPart);
                buf[count++] = '.';
                int pairIdx = fracPart << 1;
                buf[count++] = DIGITS_PAIR[pairIdx];
                buf[count++] = DIGITS_PAIR[pairIdx + 1];
                return;
            }
        }

        String s = Double.toString(v);
        int len = s.length();
        ensureCapacity(count + len);
        for (int i = 0; i < len; i++) {
            buf[count++] = (byte) s.charAt(i);
        }
    }

    private void writeLongNoCapacity(long v) {
        if (v == 0) {
            buf[count++] = '0';
            return;
        }
        int charPos = count + stringSize(v);
        int end = charPos;
        while (v >= 100) {
            long q = v / 100;
            int r = (int) (v - (q * 100));
            v = q;
            int pairIdx = r << 1;
            buf[--charPos] = DIGITS_PAIR[pairIdx + 1];
            buf[--charPos] = DIGITS_PAIR[pairIdx];
        }
        if (v >= 10) {
            int pairIdx = ((int) v) << 1;
            buf[--charPos] = DIGITS_PAIR[pairIdx + 1];
            buf[--charPos] = DIGITS_PAIR[pairIdx];
        } else {
            buf[--charPos] = (byte) ('0' + v);
        }
        count = end;
    }

    public void writeFloat(float v) {
        String s = Float.toString(v);
        int len = s.length();
        ensureCapacity(count + len);
        for (int i = 0; i < len; i++) {
            buf[count++] = (byte) s.charAt(i);
        }
    }

    public void writeBoolean(boolean v) {
        if (v) {
            ensureCapacity(count + 4);
            buf[count++] = 't';
            buf[count++] = 'r';
            buf[count++] = 'u';
            buf[count++] = 'e';
        } else {
            ensureCapacity(count + 5);
            buf[count++] = 'f';
            buf[count++] = 'a';
            buf[count++] = 'l';
            buf[count++] = 's';
            buf[count++] = 'e';
        }
    }

    private void reverse(int from, int to) {
        while (from < to) {
            byte tmp = buf[from];
            buf[from] = buf[to];
            buf[to] = tmp;
            from++;
            to--;
        }
    }

    public byte[] getBuffer() {
        return buf;
    }

    public int size() {
        return count;
    }

    public void reset() {
        count = 0;
    }

    /**
     * Zero-copy write to FileChannel via ByteBuffer wrapping the underlying array.
     */
    public void writeToFileChannel(FileChannel channel) throws IOException {
        if (count == 0) return;
        ByteBuffer bb = ByteBuffer.wrap(buf, 0, count);
        while (bb.hasRemaining()) {
            channel.write(bb);
        }
    }
}
