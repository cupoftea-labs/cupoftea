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

    /**
     * High-speed integer serialization into bytes without heap allocation.
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
        int start = count;
        do {
            int q = v / 10;
            int r = v - (q * 10);
            buf[count++] = (byte) ('0' + r);
            v = q;
        } while (v > 0);
        reverse(start, count - 1);
    }

    /**
     * High-speed long serialization into bytes without heap allocation.
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
        int start = count;
        do {
            long q = v / 10;
            int r = (int) (v - (q * 10));
            buf[count++] = (byte) ('0' + r);
            v = q;
        } while (v > 0);
        reverse(start, count - 1);
    }

    public void writeDouble(double v) {
        writeAsciiOrUtf8(Double.toString(v));
    }

    public void writeFloat(float v) {
        writeAsciiOrUtf8(Float.toString(v));
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
