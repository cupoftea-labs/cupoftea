package com.cupoftea.csv.fast;

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

    private FastByteParser() {
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
        if (start >= end) {
            return true;
        }
        byte first = buf[start];
        if (first > ' ' && first != 'N' && first != 'n' && first != '#') {
            return false;
        }
        if (end - start == 1) {
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
            throw new NumberFormatException("Invalid integer format");
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
