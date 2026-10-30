package com.cupoftea.csv.fast;

import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Set;

import com.cupoftea.exceptions.CsvParseException;
import com.cupoftea.importers.configurations.CSVImporterConfiguration;
import com.opencsv.ICSVParser;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * Two-stage SIMD-accelerated CSV structural scanner using the Java Vector API
 * ({@code jdk.incubator.vector.ByteVector}).
 *
 * <h3>Architecture</h3>
 * <ol>
 *   <li><b>Stage 1 (Vector Loads &amp; Mask Extraction):</b> Loads 4 unrolled SIMD registers per
 *       iteration ({@link ByteVector#SPECIES_PREFERRED}: 64B on NEON, 128B on AVX2, 256B on AVX-512),
 *       performs in-register bitwise ORs across structural delimiter comparisons, and extracts 64-bit
 *       delimiter and quote bitmasks.</li>
 *   <li><b>Stage 2 (Bitwise Structural Resolution):</b> Computes the quote enclosure mask via branchless
 *       parallel prefix-XOR ({@link #prefixXor(long)}), filters out delimiters inside quotes
 *       ({@code active = delims & ~insideQuotes}), and traverses active field/line boundaries using
 *       hardware bit-scanning intrinsics ({@code TZCNT} via {@link Long#numberOfTrailingZeros(long)}
 *       and {@code BLSR} via {@code mask &= (mask - 1L)}).</li>
 * </ol>
 */
public final class SimdCsvScanner {

    /**
     * Preferred hardware byte vector species (SPECIES_512 on AVX-512, SPECIES_256 on AVX2, SPECIES_128 on NEON).
     */
    public static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;

    /**
     * Vector register width in bytes (16, 32, or 64).
     */
    public static final int VLEN = SPECIES.length();

    /**
     * Loop unroll factor (4 vectors per iteration to balance instruction-level parallelism and register pressure).
     */
    public static final int UNROLL_FACTOR = 4;

    /**
     * Total bytes scanned per unrolled loop iteration ({@code VLEN * 4}).
     */
    public static final int STEP = VLEN * UNROLL_FACTOR;

    // Pre-broadcasted vector constants for common structural tokens
    public static final ByteVector V_COMMA = ByteVector.broadcast(SPECIES, (byte) ',');
    public static final ByteVector V_SEMI  = ByteVector.broadcast(SPECIES, (byte) ';');
    public static final ByteVector V_LF    = ByteVector.broadcast(SPECIES, (byte) '\n');
    public static final ByteVector V_CR    = ByteVector.broadcast(SPECIES, (byte) '\r');
    public static final ByteVector V_QUOTE = ByteVector.broadcast(SPECIES, (byte) '"');
    public static final ByteVector V_BACKSLASH = ByteVector.broadcast(SPECIES, (byte) '\\');

    static final byte TYPE_INT = 0;
    static final byte TYPE_LONG = 1;
    static final byte TYPE_DOUBLE = 2;
    static final byte TYPE_FLOAT = 3;
    static final byte TYPE_BOOLEAN = 4;
    static final byte TYPE_STRING = 5;
    static final byte TYPE_OBJECT = 6;
    static final byte TYPE_CATEGORICAL = 7;

    private static final Set<String> DEFAULT_NA_STRINGS = Set.of("NA", "N/A", "null", "NULL", "#N/A");

    private static final int MAPPED_WINDOW_SIZE = 1024 * 1024;

    private SimdCsvScanner() {
    }

    /**
     * Unboxed callback invoked for each active (outside-quote) structural delimiter or newline.
     */
    @FunctionalInterface
    public interface BoundaryConsumer {
        /**
         * Called when an active structural token (delimiter, {@code \n}, or {@code \r}) is encountered.
         *
         * @param offset    absolute byte index in the buffer
         * @param character byte value at {@code buffer[offset]}
         */
        void onBoundary(int offset, byte character);
    }

    /**
     * Computes the parallel prefix-XOR (cumulative parity mask) across a 64-bit quote bitmask.
     *
     * <p>Each bit {@code i} in the returned mask is {@code 1} if an odd number of quote bits
     * occurred in positions {@code 0..i} (meaning byte {@code i} is enclosed inside quotes),
     * and {@code 0} otherwise.</p>
     *
     * @param mask 64-bit quote bitmask
     * @return 64-bit quote enclosure mask
     */
    public static long prefixXor(long mask) {
        mask ^= mask << 1;
        mask ^= mask << 2;
        mask ^= mask << 4;
        mask ^= mask << 8;
        mask ^= mask << 16;
        mask ^= mask << 32;
        return mask;
    }

    /**
     * Scans the byte slice {@code [start, end)} using the two-stage SIMD scanner and dispatches
     * active (unquoted) structural tokens ({@code delim}, {@code \n}, {@code \r}) to {@code consumer}.
     *
     * @param buf      source byte array
     * @param start    inclusive start offset
     * @param end      exclusive end offset
     * @param delim    field delimiter byte (e.g., {@code ','} or {@code ';'})
     * @param quote    quote character byte (e.g., {@code '"'}), or {@code 0} if quoting is disabled
     * @param escape   escape character byte (e.g., {@code '\\'}), or {@code 0} if escaping is disabled
     * @param consumer unboxed callback receiving {@code (offset, byte)} for each active boundary
     * @return {@code true} if the scan ended inside an unclosed quoted string
     */
    public static boolean scan(byte[] buf, int start, int end,
                               byte delim, byte quote, byte escape,
                               BoundaryConsumer consumer) {
        final ByteVector vDelim = resolveDelimVector(delim);
        final boolean hasQuote = (quote != ICSVParser.NULL_CHARACTER);
        final ByteVector vQuote = hasQuote ? resolveQuoteVector(quote) : V_QUOTE;
        final boolean hasEscape = (escape != ICSVParser.NULL_CHARACTER && escape != quote);
        final ByteVector vEscape = hasEscape ? resolveEscapeVector(escape) : V_BACKSLASH;
        final int blocksPerStep = Math.max(1, STEP >>> 6);

        boolean insideQuote = false;
        int offset = start;

        while (offset < end) {
            long dm0 = 0L, dm1 = 0L, dm2 = 0L, dm3 = 0L;
            long qm0 = 0L, qm1 = 0L, qm2 = 0L, qm3 = 0L;
            int blocksCount;
            int stepAdvance;
            boolean stepHasEscape = false;

            if (offset + STEP <= end) {
                ByteVector v0 = ByteVector.fromArray(SPECIES, buf, offset);
                ByteVector v1 = ByteVector.fromArray(SPECIES, buf, offset + VLEN);
                ByteVector v2 = ByteVector.fromArray(SPECIES, buf, offset + VLEN * 2);
                ByteVector v3 = ByteVector.fromArray(SPECIES, buf, offset + VLEN * 3);

                long q0 = 0L, q1 = 0L, q2 = 0L, q3 = 0L;
                if (hasQuote) {
                    q0 = v0.compare(VectorOperators.EQ, vQuote).toLong();
                    q1 = v1.compare(VectorOperators.EQ, vQuote).toLong();
                    q2 = v2.compare(VectorOperators.EQ, vQuote).toLong();
                    q3 = v3.compare(VectorOperators.EQ, vQuote).toLong();
                }

                if ((q0 | q1 | q2 | q3) == 0L && insideQuote) {
                    offset += STEP;
                    continue;
                }

                long d0 = matchDelims(v0, vDelim);
                long d1 = matchDelims(v1, vDelim);
                long d2 = matchDelims(v2, vDelim);
                long d3 = matchDelims(v3, vDelim);

                if (((d0 | d1 | d2 | d3) | (q0 | q1 | q2 | q3)) == 0L && !insideQuote) {
                    offset += STEP;
                    continue;
                }

                if (hasEscape && ((q0 | q1 | q2 | q3) != 0L)) {
                    stepHasEscape = v0.compare(VectorOperators.EQ, vEscape)
                            .or(v1.compare(VectorOperators.EQ, vEscape))
                            .or(v2.compare(VectorOperators.EQ, vEscape))
                            .or(v3.compare(VectorOperators.EQ, vEscape))
                            .anyTrue()
                            || (offset > start && buf[offset - 1] == escape);
                }

                if (VLEN == 16) {
                    dm0 = d0 | (d1 << 16) | (d2 << 32) | (d3 << 48);
                    qm0 = q0 | (q1 << 16) | (q2 << 32) | (q3 << 48);
                } else if (VLEN == 32) {
                    dm0 = d0 | (d1 << 32);
                    qm0 = q0 | (q1 << 32);
                    dm1 = d2 | (d3 << 32);
                    qm1 = q2 | (q3 << 32);
                } else if (VLEN == 64) {
                    dm0 = d0; qm0 = q0;
                    dm1 = d1; qm1 = q1;
                    dm2 = d2; qm2 = q2;
                    dm3 = d3; qm3 = q3;
                } else {
                    dm0 = d0 | (d1 << 8) | (d2 << 16) | (d3 << 24);
                    qm0 = q0 | (q1 << 8) | (q2 << 16) | (q3 << 24);
                }
                blocksCount = blocksPerStep;
                stepAdvance = STEP;
            } else {
                int limit = Math.min(offset + 64, end);
                for (int vOff = offset, shift = 0; vOff < limit; vOff += VLEN, shift += VLEN) {
                    VectorMask<Byte> rangeMask = SPECIES.indexInRange(vOff, limit);
                    ByteVector v = ByteVector.fromArray(SPECIES, buf, vOff, rangeMask);
                    long d = v.compare(VectorOperators.EQ, vDelim)
                              .or(v.compare(VectorOperators.EQ, V_LF))
                              .or(v.compare(VectorOperators.EQ, V_CR))
                              .and(rangeMask)
                              .toLong();
                    dm0 |= (d << shift);
                    if (hasQuote) {
                        long q = v.compare(VectorOperators.EQ, vQuote).and(rangeMask).toLong();
                        qm0 |= (q << shift);
                    }
                }
                stepHasEscape = hasEscape && qm0 != 0L;
                blocksCount = 1;
                stepAdvance = limit - offset;
            }

            for (int blk = 0; blk < blocksCount; blk++) {
                long delimMask = (blk == 0) ? dm0 : (blk == 1) ? dm1 : (blk == 2) ? dm2 : dm3;
                long quoteMask = (blk == 0) ? qm0 : (blk == 1) ? qm1 : (blk == 2) ? qm2 : qm3;
                int blockOffset = offset + (blk << 6);

                long activeDelimiters;
                if (quoteMask == 0L) {
                    if (insideQuote) {
                        continue;
                    }
                    activeDelimiters = delimMask;
                } else {
                    if (stepHasEscape) {
                        quoteMask = filterEscapedQuotes(buf, start, blockOffset, quoteMask, escape);
                    }
                    long enclosureMask = prefixXor(quoteMask);
                    if (insideQuote) {
                        enclosureMask = ~enclosureMask;
                    }
                    activeDelimiters = delimMask & ~enclosureMask;
                    insideQuote ^= (Long.bitCount(quoteMask) & 1) != 0;
                }

                while (activeDelimiters != 0L) {
                    int bitIdx = Long.numberOfTrailingZeros(activeDelimiters);
                    int absoluteOffset = blockOffset + bitIdx;
                    consumer.onBoundary(absoluteOffset, buf[absoluteOffset]);
                    activeDelimiters &= (activeDelimiters - 1L);
                }
            }

            offset += stepAdvance;
        }

        return insideQuote;
    }

    /**
     * Scans {@code [start, end)} and populates {@code outOffsets} with the byte indices of active
     * structural delimiters and newlines. Zero object allocations.
     *
     * @param buf        source byte array
     * @param start      inclusive start offset
     * @param end        exclusive end offset
     * @param delim      field delimiter byte
     * @param quote      quote character byte
     * @param escape     escape character byte
     * @param outOffsets destination array for boundary offsets (must have sufficient capacity)
     * @return number of boundary offsets written to {@code outOffsets}
     */
    public static int scanToOffsets(byte[] buf, int start, int end,
                                    byte delim, byte quote, byte escape,
                                    int[] outOffsets) {
        final ByteVector vDelim = resolveDelimVector(delim);
        final boolean hasQuote = (quote != ICSVParser.NULL_CHARACTER);
        final ByteVector vQuote = hasQuote ? resolveQuoteVector(quote) : V_QUOTE;
        final boolean hasEscape = (escape != ICSVParser.NULL_CHARACTER && escape != quote);
        final ByteVector vEscape = hasEscape ? resolveEscapeVector(escape) : V_BACKSLASH;
        final int blocksPerStep = Math.max(1, STEP >>> 6);

        boolean insideQuote = false;
        int count = 0;
        int offset = start;

        while (offset < end) {
            long dm0 = 0L, dm1 = 0L, dm2 = 0L, dm3 = 0L;
            long qm0 = 0L, qm1 = 0L, qm2 = 0L, qm3 = 0L;
            int blocksCount;
            int stepAdvance;
            boolean stepHasEscape = false;

            if (offset + STEP <= end) {
                ByteVector v0 = ByteVector.fromArray(SPECIES, buf, offset);
                ByteVector v1 = ByteVector.fromArray(SPECIES, buf, offset + VLEN);
                ByteVector v2 = ByteVector.fromArray(SPECIES, buf, offset + VLEN * 2);
                ByteVector v3 = ByteVector.fromArray(SPECIES, buf, offset + VLEN * 3);

                long q0 = 0L, q1 = 0L, q2 = 0L, q3 = 0L;
                if (hasQuote) {
                    q0 = v0.compare(VectorOperators.EQ, vQuote).toLong();
                    q1 = v1.compare(VectorOperators.EQ, vQuote).toLong();
                    q2 = v2.compare(VectorOperators.EQ, vQuote).toLong();
                    q3 = v3.compare(VectorOperators.EQ, vQuote).toLong();
                }

                if ((q0 | q1 | q2 | q3) == 0L && insideQuote) {
                    offset += STEP;
                    continue;
                }

                long d0 = matchDelims(v0, vDelim);
                long d1 = matchDelims(v1, vDelim);
                long d2 = matchDelims(v2, vDelim);
                long d3 = matchDelims(v3, vDelim);

                if (((d0 | d1 | d2 | d3) | (q0 | q1 | q2 | q3)) == 0L && !insideQuote) {
                    offset += STEP;
                    continue;
                }

                if (hasEscape && ((q0 | q1 | q2 | q3) != 0L)) {
                    stepHasEscape = v0.compare(VectorOperators.EQ, vEscape)
                            .or(v1.compare(VectorOperators.EQ, vEscape))
                            .or(v2.compare(VectorOperators.EQ, vEscape))
                            .or(v3.compare(VectorOperators.EQ, vEscape))
                            .anyTrue()
                            || (offset > start && buf[offset - 1] == escape);
                }

                if (VLEN == 16) {
                    dm0 = d0 | (d1 << 16) | (d2 << 32) | (d3 << 48);
                    qm0 = q0 | (q1 << 16) | (q2 << 32) | (q3 << 48);
                } else if (VLEN == 32) {
                    dm0 = d0 | (d1 << 32);
                    qm0 = q0 | (q1 << 32);
                    dm1 = d2 | (d3 << 32);
                    qm1 = q2 | (q3 << 32);
                } else if (VLEN == 64) {
                    dm0 = d0; qm0 = q0;
                    dm1 = d1; qm1 = q1;
                    dm2 = d2; qm2 = q2;
                    dm3 = d3; qm3 = q3;
                } else {
                    dm0 = d0 | (d1 << 8) | (d2 << 16) | (d3 << 24);
                    qm0 = q0 | (q1 << 8) | (q2 << 16) | (q3 << 24);
                }
                blocksCount = blocksPerStep;
                stepAdvance = STEP;
            } else {
                int limit = Math.min(offset + 64, end);
                for (int vOff = offset, shift = 0; vOff < limit; vOff += VLEN, shift += VLEN) {
                    VectorMask<Byte> rangeMask = SPECIES.indexInRange(vOff, limit);
                    ByteVector v = ByteVector.fromArray(SPECIES, buf, vOff, rangeMask);
                    long d = v.compare(VectorOperators.EQ, vDelim)
                              .or(v.compare(VectorOperators.EQ, V_LF))
                              .or(v.compare(VectorOperators.EQ, V_CR))
                              .and(rangeMask)
                              .toLong();
                    dm0 |= (d << shift);
                    if (hasQuote) {
                        long q = v.compare(VectorOperators.EQ, vQuote).and(rangeMask).toLong();
                        qm0 |= (q << shift);
                    }
                }
                stepHasEscape = hasEscape && qm0 != 0L;
                blocksCount = 1;
                stepAdvance = limit - offset;
            }

            for (int blk = 0; blk < blocksCount; blk++) {
                long delimMask = (blk == 0) ? dm0 : (blk == 1) ? dm1 : (blk == 2) ? dm2 : dm3;
                long quoteMask = (blk == 0) ? qm0 : (blk == 1) ? qm1 : (blk == 2) ? qm2 : qm3;
                int blockOffset = offset + (blk << 6);

                long activeDelimiters;
                if (quoteMask == 0L) {
                    if (insideQuote) {
                        continue;
                    }
                    activeDelimiters = delimMask;
                } else {
                    if (stepHasEscape) {
                        quoteMask = filterEscapedQuotes(buf, start, blockOffset, quoteMask, escape);
                    }
                    long enclosureMask = prefixXor(quoteMask);
                    if (insideQuote) {
                        enclosureMask = ~enclosureMask;
                    }
                    activeDelimiters = delimMask & ~enclosureMask;
                    insideQuote ^= (Long.bitCount(quoteMask) & 1) != 0;
                }

                while (activeDelimiters != 0L) {
                    int bitIdx = Long.numberOfTrailingZeros(activeDelimiters);
                    if (count < outOffsets.length) {
                        outOffsets[count] = blockOffset + bitIdx;
                    }
                    count++;
                    activeDelimiters &= (activeDelimiters - 1L);
                }
            }

            offset += stepAdvance;
        }

        return count;
    }

    /**
     * Finds the first unquoted line break ({@code \n} or {@code \r}) in {@code [start, end)}
     * using SIMD vector comparisons and parallel prefix-XOR quote masking.
     *
     * @param buf                source byte array
     * @param start              inclusive start offset
     * @param end                exclusive end offset
     * @param quote              quote character byte
     * @param escape             escape character byte
     * @param initialInsideQuote initial quote parity state at {@code start}
     * @return byte offset of the first unquoted line break, or {@code -1} if not found
     */
    public static int findLineBreak(byte[] buf, int start, int end,
                                    byte quote, byte escape, boolean initialInsideQuote) {
        final boolean hasQuote = (quote != ICSVParser.NULL_CHARACTER);
        final ByteVector vQuote = hasQuote ? resolveQuoteVector(quote) : V_QUOTE;
        final boolean hasEscape = (escape != ICSVParser.NULL_CHARACTER && escape != quote);

        boolean insideQuote = initialInsideQuote;
        int offset = start;

        while (offset + VLEN <= end) {
            ByteVector v = ByteVector.fromArray(SPECIES, buf, offset);
            long nlMask = v.compare(VectorOperators.EQ, V_LF)
                           .or(v.compare(VectorOperators.EQ, V_CR))
                           .toLong();
            long qMask = hasQuote ? v.compare(VectorOperators.EQ, vQuote).toLong() : 0L;

            if (nlMask == 0L && qMask == 0L) {
                offset += VLEN;
                continue;
            }

            if (qMask == 0L) {
                if (!insideQuote && nlMask != 0L) {
                    return offset + Long.numberOfTrailingZeros(nlMask);
                }
                offset += VLEN;
                continue;
            }

            if (hasEscape) {
                qMask = filterEscapedQuotes(buf, start, offset, qMask, escape);
            }
            long enclosure = prefixXor(qMask);
            if (insideQuote) {
                enclosure = ~enclosure;
            }
            long activeNl = nlMask & ~enclosure;
            if (activeNl != 0L) {
                return offset + Long.numberOfTrailingZeros(activeNl);
            }
            insideQuote ^= (Long.bitCount(qMask) & 1) != 0;
            offset += VLEN;
        }

        // Scalar tail
        for (int i = offset; i < end; i++) {
            byte b = buf[i];
            if (hasEscape && b == escape && i + 1 < end) {
                i++;
                continue;
            }
            if (hasQuote && b == quote) {
                insideQuote = !insideQuote;
            } else if (!insideQuote && (b == '\n' || b == '\r')) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Computes the quote parity state at {@code end} by scanning {@code [start, end)} with
     * 4x unrolled SIMD vectors and XOR bitmask accumulation.
     *
     * @param buf                source byte array
     * @param start              inclusive start offset
     * @param end                exclusive end offset
     * @param quote              quote character byte
     * @param escape             escape character byte
     * @param initialInsideQuote quote state at {@code start}
     * @return quote state at {@code end}
     */
    public static boolean scanQuoteParity(byte[] buf, int start, int end,
                                          byte quote, byte escape, boolean initialInsideQuote) {
        if (quote == ICSVParser.NULL_CHARACTER || start >= end) {
            return initialInsideQuote;
        }
        final ByteVector vQuote = resolveQuoteVector(quote);
        final boolean hasEscape = (escape != ICSVParser.NULL_CHARACTER && escape != quote);

        long parityAcc = initialInsideQuote ? 1L : 0L;
        int offset = start;

        while (offset + STEP <= end) {
            long q0 = ByteVector.fromArray(SPECIES, buf, offset).compare(VectorOperators.EQ, vQuote).toLong();
            long q1 = ByteVector.fromArray(SPECIES, buf, offset + VLEN).compare(VectorOperators.EQ, vQuote).toLong();
            long q2 = ByteVector.fromArray(SPECIES, buf, offset + VLEN * 2).compare(VectorOperators.EQ, vQuote).toLong();
            long q3 = ByteVector.fromArray(SPECIES, buf, offset + VLEN * 3).compare(VectorOperators.EQ, vQuote).toLong();

            if (hasEscape && ((q0 | q1 | q2 | q3) != 0L)) {
                q0 = filterEscapedQuotes(buf, start, offset, q0, escape);
                q1 = filterEscapedQuotes(buf, start, offset + VLEN, q1, escape);
                q2 = filterEscapedQuotes(buf, start, offset + VLEN * 2, q2, escape);
                q3 = filterEscapedQuotes(buf, start, offset + VLEN * 3, q3, escape);
            }
            parityAcc ^= (q0 ^ q1 ^ q2 ^ q3);
            offset += STEP;
        }

        boolean insideQuote = (Long.bitCount(parityAcc) & 1) != 0;
        for (int i = offset; i < end; i++) {
            byte b = buf[i];
            if (hasEscape && b == escape && i + 1 < end) {
                i++;
                continue;
            }
            if (b == quote) {
                insideQuote = !insideQuote;
            }
        }
        return insideQuote;
    }

    /**
     * Fused two-stage SIMD chunk parser for {@link FastCsvReader} over a memory-mapped file slice.
     * Streams through an L2-cache-resident window buffer to avoid humongous heap allocations
     * and redundant DRAM round-trips on multi-gigabyte files.
     */
    static FastCsvReader.ChunkResult parseChunkSimdMapped(
            MappedByteBuffer mappedBuf, int chunkStart, int chunkEnd,
            byte delim, byte quote, byte escape,
            int colCount, Class<?>[] colTypes, byte[] colTypeIds,
            Charset charset, CSVImporterConfiguration config) {
        int totalChunkLen = chunkEnd - chunkStart;
        int winSize = Math.min(totalChunkLen, MAPPED_WINDOW_SIZE);
        byte[] window = new byte[Math.max(STEP, winSize)];
        ByteBuffer slice = mappedBuf.duplicate();
        slice.position(chunkStart);
        slice.get(window, 0, winSize);
        return parseChunkSimd(window, 0, winSize, slice, chunkStart + winSize, chunkEnd,
                delim, quote, escape, colCount, colTypes, colTypeIds, charset, config);
    }

    /**
     * Fused two-stage SIMD chunk parser for {@link FastCsvReader}.
     * Scans {@code buf[start..end)} in 4x unrolled SIMD chunks, resolves quote parity via
     * {@link #prefixXor(long)}, and populates typed columnar arrays directly with zero
     * per-chunk object allocations in the hot scanning loop.
     */
    static FastCsvReader.ChunkResult parseChunkSimd(
            byte[] buf, int start, int end,
            byte delim, byte quote, byte escape,
            int colCount, Class<?>[] colTypes, byte[] colTypeIds,
            Charset charset, CSVImporterConfiguration config) {
        return parseChunkSimd(buf, start, end, null, 0, 0,
                delim, quote, escape, colCount, colTypes, colTypeIds, charset, config);
    }

    private static FastCsvReader.ChunkResult parseChunkSimd(
            byte[] buf, int start, int end,
            ByteBuffer mappedSlice, int mapPos, int mapEnd,
            byte delim, byte quote, byte escape,
            int colCount, Class<?>[] colTypes, byte[] colTypeIds,
            Charset charset, CSVImporterConfiguration config) {

        int totalChunkBytes = (mappedSlice != null) ? (end - start) + (mapEnd - mapPos) : (end - start);
        int capacity = Math.max(16, totalChunkBytes / Math.max(10, colCount * 6));
        int estStringBytes = (colCount <= 2)
                ? Math.max(256, (int) (((long) totalChunkBytes * 3) >>> 2))
                : Math.max(256, totalChunkBytes / colCount);

        Object[] colData = new Object[colCount];
        BitSet[] nullBitsets = new BitSet[colCount];
        boolean needsStringPool = false;
        boolean isIso88591 = StandardCharsets.ISO_8859_1.equals(charset);

        for (int c = 0; c < colCount; c++) {
            nullBitsets[c] = new BitSet(capacity);
            switch (colTypeIds[c]) {
                case TYPE_INT -> colData[c] = new int[capacity];
                case TYPE_LONG -> colData[c] = new long[capacity];
                case TYPE_DOUBLE -> colData[c] = new double[capacity];
                case TYPE_FLOAT -> colData[c] = new float[capacity];
                case TYPE_BOOLEAN -> colData[c] = new boolean[capacity];
                case TYPE_STRING, TYPE_CATEGORICAL -> colData[c] = new FastCsvReader.StringChunkBuffer(capacity, estStringBytes);
                default -> {
                    colData[c] = new Object[capacity];
                    needsStringPool = true;
                }
            }
        }

        FastStringPool stringPool = needsStringPool
                ? new FastStringPool(charset, mappedSlice == null ? buf : null)
                : null;
        byte[] fieldBuf = new byte[256];
        Set<String> naStrings = config.getNaStrings();
        boolean customNaStrings = naStrings != null && !naStrings.equals(DEFAULT_NA_STRINGS);
        boolean preserveEmptyStrings = config.isPreserveEmptyStrings();

        final ByteVector vDelim = resolveDelimVector(delim);
        final boolean hasQuote = (quote != ICSVParser.NULL_CHARACTER);
        final ByteVector vQuote = hasQuote ? resolveQuoteVector(quote) : V_QUOTE;
        final boolean hasEscape = (escape != ICSVParser.NULL_CHARACTER && escape != quote);
        final ByteVector vEscape = hasEscape ? resolveEscapeVector(escape) : V_BACKSLASH;
        final int blocksPerStep = Math.max(1, STEP >>> 6);

        int row = 0;
        int colIdx = 0;
        int fStart = start;
        boolean insideQuote = false;
        boolean fieldHadQuote = false;
        boolean fieldHadEscape = false;
        int fieldQuoteCount = 0;
        int lastCrOffset = -2;
        int offset = start;

        while (offset < end || mapPos < mapEnd) {
            if (mappedSlice != null && offset + STEP > end && mapPos < mapEnd) {
                int keep = end - fStart;
                if (keep > 0 && fStart > 0) {
                    System.arraycopy(buf, fStart, buf, 0, keep);
                }
                offset -= fStart;
                lastCrOffset = (lastCrOffset >= fStart - 1) ? (lastCrOffset - fStart) : -2;
                fStart = 0;
                if (buf.length - keep < STEP) {
                    buf = Arrays.copyOf(buf, buf.length << 1);
                }
                int toRead = Math.min(buf.length - keep, mapEnd - mapPos);
                mappedSlice.get(buf, keep, toRead);
                end = keep + toRead;
                mapPos += toRead;
                start = 0;
            }

            long dm0 = 0L, dm1 = 0L, dm2 = 0L, dm3 = 0L;
            long qm0 = 0L, qm1 = 0L, qm2 = 0L, qm3 = 0L;
            int blocksCount;
            int stepAdvance;
            boolean stepHasEscape = false;

            if (offset + STEP <= end) {
                ByteVector v0 = ByteVector.fromArray(SPECIES, buf, offset);
                ByteVector v1 = ByteVector.fromArray(SPECIES, buf, offset + VLEN);
                ByteVector v2 = ByteVector.fromArray(SPECIES, buf, offset + VLEN * 2);
                ByteVector v3 = ByteVector.fromArray(SPECIES, buf, offset + VLEN * 3);

                long q0 = 0L, q1 = 0L, q2 = 0L, q3 = 0L;
                if (hasQuote) {
                    q0 = v0.compare(VectorOperators.EQ, vQuote).toLong();
                    q1 = v1.compare(VectorOperators.EQ, vQuote).toLong();
                    q2 = v2.compare(VectorOperators.EQ, vQuote).toLong();
                    q3 = v3.compare(VectorOperators.EQ, vQuote).toLong();
                }

                if ((q0 | q1 | q2 | q3) == 0L && insideQuote) {
                    fieldHadQuote = true;
                    fieldQuoteCount += 4;
                    offset += STEP;
                    continue;
                }

                long d0 = matchDelims(v0, vDelim);
                long d1 = matchDelims(v1, vDelim);
                long d2 = matchDelims(v2, vDelim);
                long d3 = matchDelims(v3, vDelim);

                // Fast-path block skipping (Section 3.C)
                if (((d0 | d1 | d2 | d3) | (q0 | q1 | q2 | q3)) == 0L && !insideQuote) {
                    offset += STEP;
                    continue;
                }

                if (hasEscape && ((q0 | q1 | q2 | q3) != 0L || insideQuote)) {
                    stepHasEscape = v0.compare(VectorOperators.EQ, vEscape)
                            .or(v1.compare(VectorOperators.EQ, vEscape))
                            .or(v2.compare(VectorOperators.EQ, vEscape))
                            .or(v3.compare(VectorOperators.EQ, vEscape))
                            .anyTrue()
                            || (offset > start && buf[offset - 1] == escape);
                }

                if (VLEN == 16) {
                    dm0 = d0 | (d1 << 16) | (d2 << 32) | (d3 << 48);
                    qm0 = q0 | (q1 << 16) | (q2 << 32) | (q3 << 48);
                } else if (VLEN == 32) {
                    dm0 = d0 | (d1 << 32);
                    qm0 = q0 | (q1 << 32);
                    dm1 = d2 | (d3 << 32);
                    qm1 = q2 | (q3 << 32);
                } else if (VLEN == 64) {
                    dm0 = d0; qm0 = q0;
                    dm1 = d1; qm1 = q1;
                    dm2 = d2; qm2 = q2;
                    dm3 = d3; qm3 = q3;
                } else {
                    dm0 = d0 | (d1 << 8) | (d2 << 16) | (d3 << 24);
                    qm0 = q0 | (q1 << 8) | (q2 << 16) | (q3 << 24);
                }
                blocksCount = blocksPerStep;
                stepAdvance = STEP;
            } else {
                // Masked tail load (Section 3.G)
                int limit = Math.min(offset + 64, end);
                for (int vOff = offset, shift = 0; vOff < limit; vOff += VLEN, shift += VLEN) {
                    VectorMask<Byte> rangeMask = SPECIES.indexInRange(vOff, limit);
                    ByteVector v = ByteVector.fromArray(SPECIES, buf, vOff, rangeMask);
                    long d = v.compare(VectorOperators.EQ, vDelim)
                              .or(v.compare(VectorOperators.EQ, V_LF))
                              .or(v.compare(VectorOperators.EQ, V_CR))
                              .and(rangeMask)
                              .toLong();
                    dm0 |= (d << shift);
                    if (hasQuote) {
                        long q = v.compare(VectorOperators.EQ, vQuote).and(rangeMask).toLong();
                        qm0 |= (q << shift);
                    }
                }
                stepHasEscape = hasEscape && (qm0 != 0L || insideQuote);
                blocksCount = 1;
                stepAdvance = limit - offset;
            }

            // Stage 2: Bitwise Structural Resolution
            for (int blk = 0; blk < blocksCount; blk++) {
                long delimMask = (blk == 0) ? dm0 : (blk == 1) ? dm1 : (blk == 2) ? dm2 : dm3;
                long rawQuoteMask = (blk == 0) ? qm0 : (blk == 1) ? qm1 : (blk == 2) ? qm2 : qm3;
                int blockOffset = offset + (blk << 6);

                if (rawQuoteMask == 0L && !insideQuote) {
                    // Fast unquoted block path: no prefixXor needed
                    long activeDelimiters = delimMask;
                    while (activeDelimiters != 0L) {
                        int bitIdx = Long.numberOfTrailingZeros(activeDelimiters);
                        int absoluteOffset = blockOffset + bitIdx;
                        activeDelimiters &= (activeDelimiters - 1L);

                        byte b = buf[absoluteOffset];
                        if (b == delim) {
                            if (colIdx >= colCount) {
                                throw new CsvParseException("Row length mismatch: expected " + colCount + " columns, but got " + (colIdx + 1));
                            }
                            if (fieldHadQuote) {
                                fieldBuf = materializeQuotedField(buf, fStart, absoluteOffset, fieldQuoteCount,
                                        quote, escape, fieldHadEscape, fieldBuf, colIdx, row, colTypeIds[colIdx],
                                        colTypes[colIdx], colData, nullBitsets, stringPool, isIso88591,
                                        preserveEmptyStrings, customNaStrings, naStrings);
                                fieldHadQuote = false;
                                fieldHadEscape = false;
                                fieldQuoteCount = 0;
                            } else {
                                materializeUnquotedField(buf, fStart, absoluteOffset, colIdx, row,
                                        colTypeIds[colIdx], colTypes[colIdx], colData, nullBitsets,
                                        stringPool, isIso88591, customNaStrings, naStrings);
                            }
                            colIdx++;
                            fStart = absoluteOffset + 1;
                        } else {
                            if (b == '\n' && absoluteOffset == lastCrOffset + 1) {
                                fStart = absoluteOffset + 1;
                                continue;
                            }
                            if (b == '\r') {
                                lastCrOffset = absoluteOffset;
                            }
                            if (colIdx + 1 != colCount) {
                                throw new CsvParseException("Row length mismatch: expected " + colCount + " columns, but got " + (colIdx + 1));
                            }
                            if (fieldHadQuote) {
                                fieldBuf = materializeQuotedField(buf, fStart, absoluteOffset, fieldQuoteCount,
                                        quote, escape, fieldHadEscape, fieldBuf, colIdx, row, colTypeIds[colIdx],
                                        colTypes[colIdx], colData, nullBitsets, stringPool, isIso88591,
                                        preserveEmptyStrings, customNaStrings, naStrings);
                                fieldHadQuote = false;
                                fieldHadEscape = false;
                                fieldQuoteCount = 0;
                            } else {
                                materializeUnquotedField(buf, fStart, absoluteOffset, colIdx, row,
                                        colTypeIds[colIdx], colTypes[colIdx], colData, nullBitsets,
                                        stringPool, isIso88591, customNaStrings, naStrings);
                            }
                            row++;
                            if (row >= capacity) {
                                capacity <<= 1;
                                resizeColumns(colData, colTypeIds, colCount, capacity);
                            }
                            colIdx = 0;
                            fStart = absoluteOffset + 1;
                        }
                    }
                } else if (rawQuoteMask == 0L) {
                    // insideQuote == true and no quotes in this block: entire 64B block is inside quotes
                    fieldHadQuote = true;
                    if (stepHasEscape) fieldHadEscape = true;
                    fieldQuoteCount += 4;
                } else {
                    // Block contains quotes: resolve escaped quotes and compute prefixXor enclosure mask
                    long quoteMask = stepHasEscape
                            ? filterEscapedQuotes(buf, start, blockOffset, rawQuoteMask, escape)
                            : rawQuoteMask;

                    long enclosureMask = prefixXor(quoteMask);
                    if (insideQuote) {
                        enclosureMask = ~enclosureMask;
                    }
                    long activeDelimiters = delimMask & ~enclosureMask;
                    insideQuote ^= (Long.bitCount(quoteMask) & 1) != 0;

                    long remainingQuoteMask = rawQuoteMask;
                    while (activeDelimiters != 0L) {
                        int bitIdx = Long.numberOfTrailingZeros(activeDelimiters);
                        int absoluteOffset = blockOffset + bitIdx;
                        activeDelimiters &= (activeDelimiters - 1L);

                        byte b = buf[absoluteOffset];
                        if (b == '\n' && absoluteOffset == lastCrOffset + 1) {
                            fStart = absoluteOffset + 1;
                            continue;
                        }

                        long priorBits = (1L << bitIdx) - 1L;
                        long fieldQuotes = remainingQuoteMask & priorBits;
                        remainingQuoteMask &= ~priorBits;
                        boolean hadQ = fieldHadQuote || (fieldQuotes != 0L);
                        boolean hadEsc = fieldHadEscape || stepHasEscape;
                        int qCount = fieldQuoteCount + Long.bitCount(fieldQuotes);

                        if (b == delim) {
                            if (colIdx >= colCount) {
                                throw new CsvParseException("Row length mismatch: expected " + colCount + " columns, but got " + (colIdx + 1));
                            }
                            if (hadQ) {
                                fieldBuf = materializeQuotedField(buf, fStart, absoluteOffset, qCount,
                                        quote, escape, hadEsc, fieldBuf, colIdx, row, colTypeIds[colIdx],
                                        colTypes[colIdx], colData, nullBitsets, stringPool, isIso88591,
                                        preserveEmptyStrings, customNaStrings, naStrings);
                            } else {
                                materializeUnquotedField(buf, fStart, absoluteOffset, colIdx, row,
                                        colTypeIds[colIdx], colTypes[colIdx], colData, nullBitsets,
                                        stringPool, isIso88591, customNaStrings, naStrings);
                            }
                            colIdx++;
                            fStart = absoluteOffset + 1;
                            fieldHadQuote = false;
                            fieldHadEscape = false;
                            fieldQuoteCount = 0;
                        } else {
                            if (b == '\r') {
                                lastCrOffset = absoluteOffset;
                            }
                            if (colIdx + 1 != colCount) {
                                throw new CsvParseException("Row length mismatch: expected " + colCount + " columns, but got " + (colIdx + 1));
                            }
                            if (hadQ) {
                                fieldBuf = materializeQuotedField(buf, fStart, absoluteOffset, qCount,
                                        quote, escape, hadEsc, fieldBuf, colIdx, row, colTypeIds[colIdx],
                                        colTypes[colIdx], colData, nullBitsets, stringPool, isIso88591,
                                        preserveEmptyStrings, customNaStrings, naStrings);
                            } else {
                                materializeUnquotedField(buf, fStart, absoluteOffset, colIdx, row,
                                        colTypeIds[colIdx], colTypes[colIdx], colData, nullBitsets,
                                        stringPool, isIso88591, customNaStrings, naStrings);
                            }
                            row++;
                            if (row >= capacity) {
                                capacity <<= 1;
                                resizeColumns(colData, colTypeIds, colCount, capacity);
                            }
                            colIdx = 0;
                            fStart = absoluteOffset + 1;
                            fieldHadQuote = false;
                            fieldHadEscape = false;
                            fieldQuoteCount = 0;
                        }
                    }

                    if (remainingQuoteMask != 0L) {
                        fieldHadQuote = true;
                        if (stepHasEscape) fieldHadEscape = true;
                        fieldQuoteCount += Long.bitCount(remainingQuoteMask);
                    }
                }
            }

            offset += stepAdvance;
        }

        // Handle final row if file/chunk does not end with a newline
        if (colIdx > 0 || fStart < end) {
            if (colIdx + 1 != colCount) {
                throw new CsvParseException("Row length mismatch: expected " + colCount + " columns, but got " + (colIdx + 1));
            }
            if (fieldHadQuote) {
                materializeQuotedField(buf, fStart, end, fieldQuoteCount,
                        quote, escape, fieldHadEscape, fieldBuf, colIdx, row, colTypeIds[colIdx],
                        colTypes[colIdx], colData, nullBitsets, stringPool, isIso88591,
                        preserveEmptyStrings, customNaStrings, naStrings);
            } else {
                materializeUnquotedField(buf, fStart, end, colIdx, row,
                        colTypeIds[colIdx], colTypes[colIdx], colData, nullBitsets,
                        stringPool, isIso88591, customNaStrings, naStrings);
            }
            row++;
        }

        return new FastCsvReader.ChunkResult(row, colData, nullBitsets);
    }

    private static ByteVector resolveDelimVector(byte delim) {
        if (delim == ',') return V_COMMA;
        if (delim == ';') return V_SEMI;
        return ByteVector.broadcast(SPECIES, delim);
    }

    private static ByteVector resolveQuoteVector(byte quote) {
        if (quote == '"') return V_QUOTE;
        return ByteVector.broadcast(SPECIES, quote);
    }

    private static ByteVector resolveEscapeVector(byte escape) {
        if (escape == '\\') return V_BACKSLASH;
        return ByteVector.broadcast(SPECIES, escape);
    }

    private static long matchDelims(ByteVector v, ByteVector vDelim) {
        VectorMask<Byte> mDelim = v.compare(VectorOperators.EQ, vDelim);
        VectorMask<Byte> mLf    = v.compare(VectorOperators.EQ, V_LF);
        VectorMask<Byte> mCr    = v.compare(VectorOperators.EQ, V_CR);
        return mDelim.or(mLf).or(mCr).toLong();
    }

    private static long filterEscapedQuotes(byte[] buf, int chunkStart, int blockOffset, long quoteMask, byte escape) {
        long qm = quoteMask;
        while (qm != 0L) {
            int bitIdx = Long.numberOfTrailingZeros(qm);
            int pos = blockOffset + bitIdx;
            if (pos > chunkStart && buf[pos - 1] == escape) {
                int escCount = 0;
                int p = pos - 1;
                while (p >= chunkStart && buf[p] == escape) {
                    escCount++;
                    p--;
                }
                if ((escCount & 1) != 0) {
                    quoteMask &= ~(1L << bitIdx);
                }
            }
            qm &= (qm - 1L);
        }
        return quoteMask;
    }

    private static void resizeColumns(Object[] colData, byte[] colTypeIds, int colCount, int newCap) {
        for (int c = 0; c < colCount; c++) {
            switch (colTypeIds[c]) {
                case TYPE_INT -> colData[c] = Arrays.copyOf((int[]) colData[c], newCap);
                case TYPE_LONG -> colData[c] = Arrays.copyOf((long[]) colData[c], newCap);
                case TYPE_DOUBLE -> colData[c] = Arrays.copyOf((double[]) colData[c], newCap);
                case TYPE_FLOAT -> colData[c] = Arrays.copyOf((float[]) colData[c], newCap);
                case TYPE_BOOLEAN -> colData[c] = Arrays.copyOf((boolean[]) colData[c], newCap);
                case TYPE_STRING, TYPE_CATEGORICAL -> ((FastCsvReader.StringChunkBuffer) colData[c]).ensureRowCapacity(newCap);
                default -> colData[c] = Arrays.copyOf((Object[]) colData[c], newCap);
            }
        }
    }

    private static void materializeUnquotedField(
            byte[] buf, int start, int end,
            int colIdx, int row, byte typeId, Class<?> colType,
            Object[] colData, BitSet[] nullBitsets,
            FastStringPool stringPool, boolean isIso88591,
            boolean customNaStrings, Set<String> naStrings) {

        if (start == end) {
            nullBitsets[colIdx].set(row);
            if (typeId == TYPE_STRING || typeId == TYPE_CATEGORICAL) {
                ((FastCsvReader.StringChunkBuffer) colData[colIdx]).appendEmptyOrNull(row);
            }
            return;
        }

        switch (typeId) {
            case TYPE_INT -> {
                if (FastByteParser.isNaOrEmpty(buf, start, end)
                        || (customNaStrings && isCustomNa(buf, start, end, naStrings))) {
                    nullBitsets[colIdx].set(row);
                } else {
                    ((int[]) colData[colIdx])[row] = FastByteParser.parseInt(buf, start, end);
                }
            }
            case TYPE_LONG -> {
                if (FastByteParser.isNaOrEmpty(buf, start, end)
                        || (customNaStrings && isCustomNa(buf, start, end, naStrings))) {
                    nullBitsets[colIdx].set(row);
                } else {
                    ((long[]) colData[colIdx])[row] = FastByteParser.parseLong(buf, start, end);
                }
            }
            case TYPE_DOUBLE -> {
                if (FastByteParser.isNaOrEmpty(buf, start, end)
                        || (customNaStrings && isCustomNa(buf, start, end, naStrings))) {
                    nullBitsets[colIdx].set(row);
                } else {
                    ((double[]) colData[colIdx])[row] = FastByteParser.parseDouble(buf, start, end);
                }
            }
            case TYPE_FLOAT -> {
                if (FastByteParser.isNaOrEmpty(buf, start, end)
                        || (customNaStrings && isCustomNa(buf, start, end, naStrings))) {
                    nullBitsets[colIdx].set(row);
                } else {
                    ((float[]) colData[colIdx])[row] = FastByteParser.parseFloat(buf, start, end);
                }
            }
            case TYPE_BOOLEAN -> {
                if (FastByteParser.isNaOrEmpty(buf, start, end)
                        || (customNaStrings && isCustomNa(buf, start, end, naStrings))) {
                    nullBitsets[colIdx].set(row);
                } else {
                    ((boolean[]) colData[colIdx])[row] = FastByteParser.parseBoolean(buf, start, end);
                }
            }
            case TYPE_STRING, TYPE_CATEGORICAL -> {
                FastCsvReader.StringChunkBuffer strBuf = (FastCsvReader.StringChunkBuffer) colData[colIdx];
                if (FastByteParser.isNaOrEmpty(buf, start, end)
                        || (customNaStrings && isCustomNa(buf, start, end, naStrings))) {
                    nullBitsets[colIdx].set(row);
                    strBuf.appendEmptyOrNull(row);
                } else if (!isIso88591) {
                    strBuf.appendBytes(row, buf, start, end - start);
                } else {
                    strBuf.appendBytes(row, buf, start, end - start, true);
                }
            }
            default -> {
                if (FastByteParser.isNaOrEmpty(buf, start, end)
                        || (customNaStrings && isCustomNa(buf, start, end, naStrings))) {
                    nullBitsets[colIdx].set(row);
                } else {
                    String strVal = stringPool.getOrCreate(buf, start, end - start).trim();
                    ((Object[]) colData[colIdx])[row] = FastCsvReader.convertObjectValue(strVal, colType);
                }
            }
        }
    }

    private static byte[] materializeQuotedField(
            byte[] buf, int start, int end, int quoteCount,
            byte quote, byte escape, boolean hasEscape, byte[] fieldBuf,
            int colIdx, int row, byte typeId, Class<?> colType,
            Object[] colData, BitSet[] nullBitsets,
            FastStringPool stringPool, boolean isIso88591,
            boolean preserveEmptyStrings, boolean customNaStrings, Set<String> naStrings) {

        byte[] rawBuf;
        int rawStart;
        int rawEnd;

        // Zero-copy fast path for simple quoted fields: "value" with no internal quotes or escapes
        if (quoteCount == 2 && (end - start) >= 2 && buf[start] == quote && buf[end - 1] == quote
                && (!hasEscape || !containsByte(buf, start + 1, end - 1, escape))) {
            rawBuf = buf;
            rawStart = start + 1;
            rawEnd = end - 1;
        } else {
            // Unquote and unescape into reusable fieldBuf
            int fieldLen = 0;
            boolean inQuote = false;
            int curr = start;
            while (curr < end) {
                byte b = buf[curr];
                if (hasEscape && b == escape && curr + 1 < end) {
                    if (fieldLen >= fieldBuf.length) {
                        fieldBuf = Arrays.copyOf(fieldBuf, fieldBuf.length * 2);
                    }
                    fieldBuf[fieldLen++] = buf[curr + 1];
                    curr += 2;
                    continue;
                }
                if (b == quote && quote != ICSVParser.NULL_CHARACTER) {
                    if (inQuote && curr + 1 < end && buf[curr + 1] == quote) {
                        if (fieldLen >= fieldBuf.length) {
                            fieldBuf = Arrays.copyOf(fieldBuf, fieldBuf.length * 2);
                        }
                        fieldBuf[fieldLen++] = quote;
                        curr += 2;
                        continue;
                    }
                    inQuote = !inQuote;
                    curr++;
                    continue;
                }
                if (fieldLen >= fieldBuf.length) {
                    fieldBuf = Arrays.copyOf(fieldBuf, fieldBuf.length * 2);
                }
                fieldBuf[fieldLen++] = b;
                curr++;
            }
            rawBuf = fieldBuf;
            rawStart = 0;
            rawEnd = fieldLen;
        }

        if (typeId == TYPE_STRING || typeId == TYPE_CATEGORICAL) {
            FastCsvReader.StringChunkBuffer strBuf = (FastCsvReader.StringChunkBuffer) colData[colIdx];
            int len = rawEnd - rawStart;
            if (len == 0) {
                if (!preserveEmptyStrings) {
                    nullBitsets[colIdx].set(row);
                }
                strBuf.appendEmptyOrNull(row);
            } else if (customNaStrings && isCustomNa(rawBuf, rawStart, rawEnd, naStrings)) {
                nullBitsets[colIdx].set(row);
                strBuf.appendEmptyOrNull(row);
            } else if (!isIso88591) {
                strBuf.appendBytes(row, rawBuf, rawStart, len);
            } else {
                strBuf.appendBytes(row, rawBuf, rawStart, len, true);
            }
            return fieldBuf;
        }

        materializeUnquotedField(rawBuf, rawStart, rawEnd, colIdx, row, typeId, colType,
                colData, nullBitsets, stringPool, isIso88591, customNaStrings, naStrings);
        return fieldBuf;
    }

    private static boolean containsByte(byte[] buf, int start, int end, byte target) {
        for (int i = start; i < end; i++) {
            if (buf[i] == target) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCustomNa(byte[] buf, int start, int end, Set<String> naStrings) {
        while (start < end && buf[start] <= ' ' && (buf[start] == ' ' || buf[start] == '\t' || buf[start] == '\r')) {
            start++;
        }
        while (end > start && buf[end - 1] <= ' ' && (buf[end - 1] == ' ' || buf[end - 1] == '\t' || buf[end - 1] == '\r')) {
            end--;
        }
        if (start >= end) {
            return true;
        }
        String s = new String(buf, start, end - start, java.nio.charset.StandardCharsets.UTF_8);
        return naStrings.contains(s);
    }
}
