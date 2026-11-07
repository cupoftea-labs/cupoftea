package com.cupoftea.csv.fast;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * High-performance byte-level parsing utilities for fast CSV ingestion.
 * Parses integers, longs, and doubles directly from byte buffers without String allocation.
 */
public final class FastByteParser {

    private static final double[] POW10 = {
        1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9,
        1e10, 1e11, 1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19,
        1e20, 1e21, 1e22
    };

    private static final VarHandle LONG_LE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle BB_LONG_LE =
            MethodHandles.byteBufferViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    private static final long LO_BITS = 0x0101010101010101L;
    private static final long HI_BITS = 0x8080808080808080L;

    /**
     * Magic multiplier constant for SWAR fixed-point decimal parsing.
     * Derived by Quan Anh Mai (merykitty) for 1BRC: packs units, tens, and hundreds
     * digits into a 64-bit word and extracts the absolute integer value in tenths or hundredths.
     */
    private static final long MAGIC_DECIMAL_10 = 0x640a0001L;

    private FastByteParser() {
    }

    /**
     * SWAR (SIMD Within A Register) zero-byte detector. The lowest set 0x80 bit of the result
     * marks the first zero byte of {@code x}; higher bits may be false positives.
     */
    private static long zeroBytes(long x) {
        return (x - LO_BITS) & ~x & HI_BITS;
    }

    /**
     * Finds the first index in [start, end) holding {@code target}, scanning 8 bytes per step
     * with plain 64-bit arithmetic (no incubator modules required).
     *
     * @return the index, or -1 if absent
     */
    public static int indexOfByte(byte[] buf, int start, int end, byte target) {
        final long bt = (target & 0xFFL) * LO_BITS;
        int i = start;
        for (int limit = end - 8; i <= limit; i += 8) {
            long mask = zeroBytes(((long) LONG_LE.get(buf, i)) ^ bt);
            if (mask != 0) {
                return i + (Long.numberOfTrailingZeros(mask) >>> 3);
            }
        }
        for (; i < end; i++) {
            if (buf[i] == target) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the first index in [start, end) holding {@code target} directly in a {@link java.nio.ByteBuffer}.
     *
     * @return the index, or -1 if absent
     */
    public static int indexOfByte(java.nio.ByteBuffer buf, int start, int end, byte target) {
        final long bt = (target & 0xFFL) * LO_BITS;
        int i = start;
        for (int limit = end - 8; i <= limit; i += 8) {
            long mask = zeroBytes(((long) BB_LONG_LE.get(buf, i)) ^ bt);
            if (mask != 0) {
                return i + (Long.numberOfTrailingZeros(mask) >>> 3);
            }
        }
        for (; i < end; i++) {
            if (buf.get(i) == target) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the first index in [start, end) holding any CSV structural byte: the delimiter,
     * the quote, the escape (each ignored when {@code NUL}-disabled by passing a duplicate byte),
     * {@code '\n'} or {@code '\r'}.
     *
     * @return the index, or {@code end} if none found
     */
    public static int indexOfStructural(byte[] buf, int start, int end, byte delim, byte quote, byte escape) {
        final long bd = (delim & 0xFFL) * LO_BITS;
        final long bq = (quote & 0xFFL) * LO_BITS;
        final long be = (escape & 0xFFL) * LO_BITS;
        final long bn = ('\n') * LO_BITS;
        final long br = ('\r') * LO_BITS;
        int i = start;
        for (int limit = end - 8; i <= limit; i += 8) {
            long w = (long) LONG_LE.get(buf, i);
            long mask = zeroBytes(w ^ bd) | zeroBytes(w ^ bq) | zeroBytes(w ^ be)
                    | zeroBytes(w ^ bn) | zeroBytes(w ^ br);
            if (mask != 0) {
                return i + (Long.numberOfTrailingZeros(mask) >>> 3);
            }
        }
        for (; i < end; i++) {
            byte b = buf[i];
            if (b == delim || b == quote || b == escape || b == '\n' || b == '\r') {
                return i;
            }
        }
        return end;
    }

    /**
     * Checks if the byte range [start, end) is empty or all whitespace.
     */
    public static boolean isBlank(byte[] buf, int start, int end) {
        while (start < end && buf[start] <= ' ' && (buf[start] == ' ' || buf[start] == '\t' || buf[start] == '\r')) {
            start++;
        }
        while (end > start && buf[end - 1] <= ' ' && (buf[end - 1] == ' ' || buf[end - 1] == '\t' || buf[end - 1] == '\r')) {
            end--;
        }
        return start >= end;
    }

    /**
     * Checks if trimmed byte range matches typical NA representations:
     * "", "NA", "N/A", "null", "NULL", "#N/A".
     */
    public static boolean isNaOrEmpty(byte[] buf, int start, int end) {
        int rawLen = end - start;
        if (rawLen <= 0) {
            return true;
        }
        byte first = buf[start];
        if (first > ' ') {
            if (first != 'N' && first != 'n' && first != '#') {
                return false;
            }
            if (buf[end - 1] > ' ' && (rawLen > 4 || rawLen < 2)) {
                return false;
            }
        } else if (rawLen == 1) {
            return first == ' ' || first == '\t' || first == '\r';
        }
        while (start < end && buf[start] <= ' ' && (buf[start] == ' ' || buf[start] == '\t' || buf[start] == '\r')) {
            start++;
        }
        while (end > start && buf[end - 1] <= ' ' && (buf[end - 1] == ' ' || buf[end - 1] == '\t' || buf[end - 1] == '\r')) {
            end--;
        }
        int len = end - start;
        if (len == 0) {
            return true;
        }
        byte b0 = buf[start];
        if (b0 != 'N' && b0 != 'n' && b0 != '#') {
            return false;
        }
        if (len == 2) {
            return b0 == 'N' && buf[start + 1] == 'A';
        }
        if (len == 3) {
            return b0 == 'N' && buf[start + 1] == '/' && buf[start + 2] == 'A';
        }
        if (len == 4) {
            byte b1 = buf[start + 1];
            byte b2 = buf[start + 2];
            byte b3 = buf[start + 3];
            return (b0 == 'n' && b1 == 'u' && b2 == 'l' && b3 == 'l')
                || (b0 == 'N' && b1 == 'U' && b2 == 'L' && b3 == 'L')
                || (b0 == '#' && b1 == 'N' && b2 == '/' && b3 == 'A');
        }
        return false;
    }

    /**
     * Parses an integer from bytes without boxing or String allocation.
     * Throws NumberFormatException if invalid.
     */
    public static int parseInt(byte[] buf, int start, int end) {
        while (start < end && buf[start] <= ' ' && (buf[start] == ' ' || buf[start] == '\t' || buf[start] == '\r' || buf[start] == '\n')) {
            start++;
        }
        while (end > start && buf[end - 1] <= ' ' && (buf[end - 1] == ' ' || buf[end - 1] == '\t' || buf[end - 1] == '\r' || buf[end - 1] == '\n')) {
            end--;
        }
        if (start >= end) {
            throw new NumberFormatException("Empty number");
        }
        boolean negative = false;
        byte first = buf[start];
        if (first == '-') {
            negative = true;
            start++;
        } else if (first == '+') {
            start++;
        }
        int digitsLen = end - start;
        if (digitsLen <= 0) {
            throw new NumberFormatException("Invalid integer format");
        }
        if (digitsLen <= 8 && start + 8 <= buf.length) {
            long word = (long) LONG_LE.get(buf, start);
            long mask = (~0L >>> ((8 - digitsLen) << 3));
            long val = word & mask;
            if (((val & 0xF0F0F0F0F0F0F0F0L & mask) == (0x3030303030303030L & mask))
                    && (((val + 0x0606060606060606L) & 0xF0F0F0F0F0F0F0F0L & mask) == (0x3030303030303030L & mask))) {
                long shifted = val << ((8 - digitsLen) << 3);
                long v = (shifted & 0x0F0F0F0F0F0F0F0FL) * 2561L >>> 8;
                v = (v & 0x00FF00FF00FF00FFL) * 6553601L >>> 16;
                v = (v & 0x0000FFFF0000FFFFL) * 42949672960001L >>> 32;
                int result = (int) v;
                return negative ? -result : result;
            }
        }
        if (digitsLen <= 9) {
            int result = 0;
            for (int i = start; i < end; i++) {
                int digit = buf[i] - '0';
                if (digit < 0 || digit > 9) {
                    throw new NumberFormatException("Invalid digit: " + (char) buf[i]);
                }
                result = result * 10 + digit;
            }
            return negative ? -result : result;
        }
        int result = 0;
        int limit = negative ? Integer.MIN_VALUE : -Integer.MAX_VALUE;
        int multmin = limit / 10;
        for (int i = start; i < end; i++) {
            byte b = buf[i];
            if (b < '0' || b > '9') {
                throw new NumberFormatException("Invalid digit: " + (char) b);
            }
            int digit = b - '0';
            if (result < multmin) {
                throw new NumberFormatException("Integer overflow");
            }
            result *= 10;
            if (result < limit + digit) {
                throw new NumberFormatException("Integer overflow");
            }
            result -= digit;
        }
        return negative ? result : -result;
    }

    /**
     * Parses a long from bytes without boxing or String allocation.
     * Throws NumberFormatException if invalid.
     */
    public static long parseLong(byte[] buf, int start, int end) {
        while (start < end && buf[start] <= ' ' && (buf[start] == ' ' || buf[start] == '\t' || buf[start] == '\r')) {
            start++;
        }
        while (end > start && buf[end - 1] <= ' ' && (buf[end - 1] == ' ' || buf[end - 1] == '\t' || buf[end - 1] == '\r')) {
            end--;
        }
        if (start >= end) {
            throw new NumberFormatException("Empty number");
        }
        boolean negative = false;
        byte first = buf[start];
        if (first == '-') {
            negative = true;
            start++;
        } else if (first == '+') {
            start++;
        }
        int digitsLen = end - start;
        if (digitsLen <= 0) {
            throw new NumberFormatException("Invalid long format");
        }
        if (digitsLen <= 18) {
            long result = 0L;
            for (int i = start; i < end; i++) {
                int digit = buf[i] - '0';
                if (digit < 0 || digit > 9) {
                    throw new NumberFormatException("Invalid digit: " + (char) buf[i]);
                }
                result = result * 10L + digit;
            }
            return negative ? -result : result;
        }
        long result = 0;
        long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
        long multmin = limit / 10;
        for (int i = start; i < end; i++) {
            byte b = buf[i];
            if (b < '0' || b > '9') {
                throw new NumberFormatException("Invalid digit: " + (char) b);
            }
            int digit = b - '0';
            if (result < multmin) {
                throw new NumberFormatException("Long overflow");
            }
            result *= 10;
            if (result < limit + digit) {
                throw new NumberFormatException("Long overflow");
            }
            result -= digit;
        }
        return negative ? result : -result;
    }

    /**
     * Parses a double directly from byte buffer without String allocation.
     * Handles sign, integer part, fractional part, and optional exponent (e.g. 1.23e-4).
     * Falls back to Double.parseDouble via String only for rare complex formats.
     */
    public static double parseDouble(byte[] buf, int start, int end) {
        while (start < end && buf[start] <= ' ' && (buf[start] == ' ' || buf[start] == '\t' || buf[start] == '\r')) {
            start++;
        }
        while (end > start && buf[end - 1] <= ' ' && (buf[end - 1] == ' ' || buf[end - 1] == '\t' || buf[end - 1] == '\r')) {
            end--;
        }
        if (start >= end) {
            throw new NumberFormatException("Empty double string");
        }

        int origStart = start;
        boolean negative = false;
        if (buf[start] == '-') {
            negative = true;
            start++;
        } else if (buf[start] == '+') {
            start++;
        }
        if (start >= end) {
            throw new NumberFormatException("Invalid double format");
        }

        long integerPart = 0;
        boolean hasDigits = false;
        int i = start;

        while (i < end && buf[i] >= '0' && buf[i] <= '9') {
            hasDigits = true;
            if (integerPart > 900_719_925_474_099L) { // > 2^53
                return Double.parseDouble(new String(buf, origStart, end - origStart, StandardCharsets.ISO_8859_1));
            }
            integerPart = integerPart * 10 + (buf[i] - '0');
            i++;
        }

        long fractionalPart = 0;
        int fracDigits = 0;
        if (i < end && buf[i] == '.') {
            i++;
            while (i < end && buf[i] >= '0' && buf[i] <= '9') {
                hasDigits = true;
                if (fracDigits < 18) {
                    fractionalPart = fractionalPart * 10 + (buf[i] - '0');
                    fracDigits++;
                }
                i++;
            }
        }

        if (!hasDigits) {
            throw new NumberFormatException("No digits found in double");
        }

        double val = fracDigits > 0
                ? (double) integerPart + ((double) fractionalPart / POW10[fracDigits])
                : (double) integerPart;

        if (i < end && (buf[i] == 'e' || buf[i] == 'E')) {
            i++;
            boolean expNeg = false;
            if (i < end && buf[i] == '-') {
                expNeg = true;
                i++;
            } else if (i < end && buf[i] == '+') {
                i++;
            }
            int exp = 0;
            while (i < end && buf[i] >= '0' && buf[i] <= '9') {
                exp = exp * 10 + (buf[i] - '0');
                i++;
            }
            if (exp < POW10.length) {
                val = expNeg ? (val / POW10[exp]) : (val * POW10[exp]);
            } else {
                val = expNeg ? (val / Math.pow(10, exp)) : (val * Math.pow(10, exp));
            }
        }

        if (i < end) {
            return Double.parseDouble(new String(buf, origStart, end - origStart, StandardCharsets.ISO_8859_1));
        }

        return negative ? -val : val;
    }

    /**
     * Parses a 1-decimal fixed-point number (e.g., -12.3, 4.5, -99.9, 105.2) into a scaled long
     * representing tenths (value * 10). Uses Quan Anh Mai's SWAR bitshift digit unpacking algorithm
     * for high-speed branchless execution without floating-point operations.
     *
     * @param buf byte buffer containing the ASCII digits
     * @param start start index (inclusive)
     * @param end end index (exclusive)
     * @return signed long value scaled by 10 (e.g. "12.3" -> 123L, "-5.6" -> -56L)
     */
    public static long parseFixedPoint1(byte[] buf, int start, int end) {
        while (start < end && buf[start] <= ' ' && (buf[start] == ' ' || buf[start] == '\t' || buf[start] == '\r')) {
            start++;
        }
        while (end > start && buf[end - 1] <= ' ' && (buf[end - 1] == ' ' || buf[end - 1] == '\t' || buf[end - 1] == '\r')) {
            end--;
        }
        int len = end - start;
        if (len <= 0) {
            throw new NumberFormatException("Empty decimal number");
        }
        // Fast path: if 8 bytes are safely readable from buffer, use Quan Anh Mai's branchless SWAR algorithm
        // Works for 1- or 2-digit integer part + 1 decimal digit (e.g. -99.9 .. 99.9)
        if (len <= 5 && start + 8 <= buf.length) {
            long word = (long) LONG_LE.get(buf, start);
            long mask = (~0L >>> ((8 - len) << 3));
            long masked = word & mask;
            int dotPos = Long.numberOfTrailingZeros(~masked & 0x10101000L);
            long signed = (~masked << 59) >> 63;
            int intDigitsCount = (dotPos >>> 3) - (signed != 0 ? 1 : 0);
            if (dotPos < 64 && (dotPos >>> 3) == len - 2 && buf[start + (dotPos >>> 3)] == '.'
                    && intDigitsCount >= 1 && intDigitsCount <= 2) {
                long removeSignMask = ~(signed & 0xFFL);
                long digits = ((masked & removeSignMask) << (28 - dotPos)) & 0x0F000F0F00L;
                long absValue = ((digits * MAGIC_DECIMAL_10) >>> 32) & 0x3FFL;
                return (absValue ^ signed) - signed;
            }
        }
        return parseFixedPointGeneral(buf, start, end, 1);
    }

    /**
     * Parses a 2-decimal fixed-point number (e.g., -12.34, 1.99, 100.50) into a scaled long
     * representing hundredths (value * 100).
     *
     * @param buf byte buffer containing the ASCII digits
     * @param start start index (inclusive)
     * @param end end index (exclusive)
     * @return signed long value scaled by 100 (e.g. "12.34" -> 1234L, "-0.99" -> -99L)
     */
    public static long parseFixedPoint2(byte[] buf, int start, int end) {
        while (start < end && buf[start] <= ' ' && (buf[start] == ' ' || buf[start] == '\t' || buf[start] == '\r')) {
            start++;
        }
        while (end > start && buf[end - 1] <= ' ' && (buf[end - 1] == ' ' || buf[end - 1] == '\t' || buf[end - 1] == '\r')) {
            end--;
        }
        int len = end - start;
        if (len <= 0) {
            throw new NumberFormatException("Empty decimal number");
        }
        // Fast path: if 8 bytes are safely readable from buffer, use SWAR bitshift unpacking
        if (len <= 6 && start + 8 <= buf.length) {
            long word = (long) LONG_LE.get(buf, start);
            long mask = (~0L >>> ((8 - len) << 3));
            long masked = word & mask;
            int dotPos = Long.numberOfTrailingZeros(~masked & 0x10101000L);
            long signed = (~masked << 59) >> 63;
            int intDigitsCount = (dotPos >>> 3) - (signed != 0 ? 1 : 0);
            if (dotPos < 64 && (dotPos >>> 3) == len - 3 && buf[start + (dotPos >>> 3)] == '.'
                    && intDigitsCount >= 1 && intDigitsCount <= 2) {
                long removeSignMask = ~(signed & 0xFFL);
                int shift = 32 - (dotPos & ~7);
                long aligned = (masked & removeSignMask) << shift;
                long intPart = (((aligned >>> 16) & 0x0F0FL) * 0x0A01L) >>> 8;
                long fracPart = (((aligned >>> 40) & 0x0F0FL) * 0x0A01L) >>> 8;
                long absVal = (intPart & 0xFFL) * 100L + (fracPart & 0xFFL);
                return (absVal ^ signed) - signed;
            }
        }
        return parseFixedPointGeneral(buf, start, end, 2);
    }

    /**
     * General fallback parser for fixed-point decimal values with arbitrary scale without floating-point division.
     */
    public static long parseFixedPointGeneral(byte[] buf, int start, int end, int targetScale) {
        boolean negative = false;
        if (buf[start] == '-') {
            negative = true;
            start++;
        } else if (buf[start] == '+') {
            start++;
        }
        if (start >= end) {
            throw new NumberFormatException("Invalid fixed-point decimal format");
        }
        long integerPart = 0L;
        int i = start;
        boolean hasDigits = false;
        while (i < end && buf[i] >= '0' && buf[i] <= '9') {
            integerPart = integerPart * 10L + (buf[i] - '0');
            hasDigits = true;
            i++;
        }
        long fracPart = 0L;
        int fracDigits = 0;
        if (i < end && buf[i] == '.') {
            i++;
            while (i < end && buf[i] >= '0' && buf[i] <= '9') {
                if (fracDigits < targetScale) {
                    fracPart = fracPart * 10L + (buf[i] - '0');
                    fracDigits++;
                }
                hasDigits = true;
                i++;
            }
        }
        if (!hasDigits || i < end) {
            // Not a simple fixed-point decimal, fall back to parseDouble scaled
            double d = parseDouble(buf, start - (negative ? 1 : 0), end);
            return Math.round(d * POW10[targetScale]);
        }
        while (fracDigits < targetScale) {
            fracPart *= 10L;
            fracDigits++;
        }
        long val = integerPart * (long) POW10[targetScale] + fracPart;
        return negative ? -val : val;
    }

    /**
     * Parses a float directly from byte buffer without String allocation.
     */
    public static float parseFloat(byte[] buf, int start, int end) {
        return (float) parseDouble(buf, start, end);
    }

    /**
     * Parses boolean from byte buffer.
     * "true" / "1" -> true
     * "false" / "0" -> false
     */
    public static boolean parseBoolean(byte[] buf, int start, int end) {
        while (start < end && buf[start] <= ' ' && (buf[start] == ' ' || buf[start] == '\t' || buf[start] == '\r')) {
            start++;
        }
        while (end > start && buf[end - 1] <= ' ' && (buf[end - 1] == ' ' || buf[end - 1] == '\t' || buf[end - 1] == '\r')) {
            end--;
        }
        int len = end - start;
        if (len == 4 && (buf[start] == 't' || buf[start] == 'T')
            && (buf[start + 1] == 'r' || buf[start + 1] == 'R')
            && (buf[start + 2] == 'u' || buf[start + 2] == 'U')
            && (buf[start + 3] == 'e' || buf[start + 3] == 'E')) {
            return true;
        }
        if (len == 5 && (buf[start] == 'f' || buf[start] == 'F')
            && (buf[start + 1] == 'a' || buf[start + 1] == 'A')
            && (buf[start + 2] == 'l' || buf[start + 2] == 'L')
            && (buf[start + 3] == 's' || buf[start + 3] == 'S')
            && (buf[start + 4] == 'e' || buf[start + 4] == 'E')) {
            return false;
        }
        if (len == 1) {
            if (buf[start] == '1') return true;
            if (buf[start] == '0') return false;
        }
        throw new IllegalArgumentException("Cannot parse boolean from bytes");
    }
}
