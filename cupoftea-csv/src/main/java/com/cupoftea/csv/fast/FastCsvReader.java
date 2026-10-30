package com.cupoftea.csv.fast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.CsvParseException;
import com.cupoftea.importers.configurations.CSVImporterConfiguration;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.Schema;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.ObjectColumnVector;
import com.cupoftea.table.vector.StringColumnVector;
import com.opencsv.ICSVParser;

/**
 * High-performance chunk-based parallel CSV reader using two-stage SIMD structural scanning
 * ({@link SimdCsvScanner}), direct byte parsing ({@link FastByteParser}), zero/low object allocation,
 * and NIO memory-mapping / buffered block reading, with automatic scalar fallback.
 */
public class FastCsvReader {

    private final CSVImporterConfiguration config;

    public FastCsvReader(CSVImporterConfiguration config) {
        this.config = config != null ? config : new CSVImporterConfiguration();
    }

    /**
     * Determines whether the fast chunk-based reader is eligible to parse this file and configuration.
     */
    public static boolean isEligible(File file, CSVImporterConfiguration config) {
        if (file == null || !file.exists() || file.length() == 0) {
            return false;
        }
        if (!config.isFastEngine()) {
            return false;
        }
        char delim = config.getDelimiter();
        if (delim > 127) {
            return false;
        }
        char quote = config.getQuoteChar();
        if (quote > 127 && quote != ICSVParser.NULL_CHARACTER) {
            return false;
        }
        Charset cs = config.getCharset();
        if (!StandardCharsets.UTF_8.equals(cs)
                && !StandardCharsets.ISO_8859_1.equals(cs)
                && !StandardCharsets.US_ASCII.equals(cs)) {
            return false;
        }
        return true;
    }

    /**
     * Reads the CSV file directly into the target DataFrame using parallel byte chunking
     * and SIMD structural scanning when supported.
     */
    public void readInto(File file, CupOfTea target) {
        long fileLength = file.length();
        if (fileLength == 0) {
            target.clear();
            return;
        }

        try (FileInputStream fis = new FileInputStream(file);
             FileChannel channel = fis.getChannel()) {

            byte[] fullBytes = null;
            MappedByteBuffer mappedBuf = null;

            // For files under 100MB, reading into byte[] provides fastest sequential and SIMD memory access
            if (fileLength < 100 * 1024 * 1024) {
                fullBytes = new byte[(int) fileLength];
                int offset = 0;
                while (offset < fileLength) {
                    int read = fis.read(fullBytes, offset, (int) (fileLength - offset));
                    if (read < 0) break;
                    offset += read;
                }
            } else {
                mappedBuf = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileLength);
            }

            parseBytes(fullBytes, mappedBuf, (int) fileLength, target);

        } catch (IOException e) {
            throw new CsvParseException("Failed to read CSV file: " + file.getPath(), e);
        } catch (Exception e) {
            if (e instanceof CsvParseException) {
                throw (CsvParseException) e;
            }
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new CsvParseException("Failed to parse CSV file: " + file.getPath(), e);
        }
    }

    /**
     * Reads an in-memory CSV byte array directly into the target DataFrame.
     */
    public void readInto(byte[] bytes, CupOfTea target) {
        if (bytes == null || bytes.length == 0) {
            target.clear();
            return;
        }
        try {
            parseBytes(bytes, null, bytes.length, target);
        } catch (Exception e) {
            if (e instanceof CsvParseException) {
                throw (CsvParseException) e;
            }
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new CsvParseException("Failed to parse CSV bytes", e);
        }
    }

    private void parseBytes(byte[] fullBytes, MappedByteBuffer mappedBuf, int length, CupOfTea target) throws Exception {
        byte delim = (byte) config.getDelimiter();
        byte quote = (byte) config.getQuoteChar();
        byte escape = (byte) config.getEscapeChar();
        Charset charset = config.getCharset();
        boolean useSimd = config.isSimdEnabled() && SimdDetector.isSupported();

        // 1. Check UTF-8 BOM
        int dataStart = 0;
        if (length >= 3) {
            byte b0 = getByte(fullBytes, mappedBuf, 0);
            byte b1 = getByte(fullBytes, mappedBuf, 1);
            byte b2 = getByte(fullBytes, mappedBuf, 2);
            if ((b0 & 0xFF) == 0xEF && (b1 & 0xFF) == 0xBB && (b2 & 0xFF) == 0xBF) {
                dataStart = 3;
            }
        }

        if (dataStart >= length) {
            target.clear();
            return;
        }

        // 2. Parse Header line or determine column count
        int firstLineEnd = findLineBreak(fullBytes, mappedBuf, dataStart, length, quote, escape, false, useSimd);
        int headerEnd = (firstLineEnd == -1) ? length : firstLineEnd;

        List<String> firstRowFields = parseRowStrings(fullBytes, mappedBuf, dataStart, headerEnd, delim, quote, escape, charset);
        if (firstRowFields.isEmpty()) {
            target.clear();
            return;
        }

        int colCount = firstRowFields.size();
        String[] colNames = new String[colCount];
        int bodyStart;

        if (config.isHasHeaders()) {
            for (int i = 0; i < colCount; i++) {
                colNames[i] = firstRowFields.get(i);
            }
            bodyStart = skipLineBreak(fullBytes, mappedBuf, headerEnd, length);
        } else {
            for (int i = 0; i < colCount; i++) {
                colNames[i] = String.valueOf(i);
            }
            bodyStart = dataStart;
        }

        // 3. Resolve Column Types
        Schema explicitSchema = config.getSchema() != null ? config.getSchema() : target.getSchema();
        Class<?>[] colTypes = new Class<?>[colCount];

        for (int i = 0; i < colCount; i++) {
            String name = colNames[i];
            Class<?> st = null;
            if (explicitSchema != null && explicitSchema.hasColumn(name)) {
                st = explicitSchema.getType(name);
            } else if (explicitSchema != null && i < explicitSchema.size() && !config.isHasHeaders()) {
                st = explicitSchema.getColumn(i).columnType();
            }
            colTypes[i] = st;
        }

        // 4. Sample inference if types not fully specified
        boolean needsInference = false;
        if (config.isInferTypes() || config.isInferNumericTypes()) {
            for (int i = 0; i < colCount; i++) {
                if (colTypes[i] == null) {
                    needsInference = true;
                    break;
                }
            }
        }

        if (needsInference) {
            if (useSimd && fullBytes != null) {
                inferTypesFromSampleZeroAlloc(fullBytes, bodyStart, length, delim, quote, escape, colCount, colTypes);
            } else {
                inferTypesFromSampleScalar(fullBytes, mappedBuf, bodyStart, length, delim, quote, escape, colCount, colTypes, charset);
            }
        }

        byte[] colTypeIds = new byte[colCount];
        for (int i = 0; i < colCount; i++) {
            if (colTypes[i] == null) {
                colTypes[i] = String.class;
            }
            colTypeIds[i] = resolveTypeId(colTypes[i]);
        }

        // 5. Partition file into chunks for multi-core parallelism
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int minChunkSize = 256 * 1024;
        int bodyLength = length - bodyStart;

        int numChunks = 1;
        if (bodyLength > minChunkSize && availableProcessors > 1) {
            numChunks = Math.min(availableProcessors, Math.max(1, bodyLength / minChunkSize));
        }

        List<ChunkRange> chunks = partitionChunks(fullBytes, mappedBuf, bodyStart, length, numChunks, quote, escape, useSimd);

        // 6. Execute chunk parsing (single-threaded directly when 1 chunk, parallel across ForkJoinPool otherwise)
        List<ChunkResult> chunkResults = new ArrayList<>(chunks.size());
        int totalRows = 0;

        if (chunks.size() == 1) {
            ChunkRange chunk = chunks.get(0);
            ChunkResult res = executeChunk(fullBytes, mappedBuf, chunk.start, chunk.end,
                    delim, quote, escape, colCount, colTypes, colTypeIds, charset, config, useSimd);
            chunkResults.add(res);
            totalRows = res.rowCount;
        } else if (chunks.size() > 1) {
            ExecutorService executor = ForkJoinPool.commonPool();
            List<Future<ChunkResult>> futures = new ArrayList<>(chunks.size() - 1);

            for (int i = 1; i < chunks.size(); i++) {
                ChunkRange chunk = chunks.get(i);
                futures.add(executor.submit(() -> executeChunk(fullBytes, mappedBuf, chunk.start, chunk.end,
                        delim, quote, escape, colCount, colTypes, colTypeIds, charset, config, useSimd)));
            }

            try {
                // Execute first chunk on calling thread while worker threads process remaining chunks
                ChunkRange firstChunk = chunks.get(0);
                ChunkResult firstRes = executeChunk(fullBytes, mappedBuf, firstChunk.start, firstChunk.end,
                        delim, quote, escape, colCount, colTypes, colTypeIds, charset, config, useSimd);
                chunkResults.add(firstRes);
                totalRows += firstRes.rowCount;

                for (Future<ChunkResult> future : futures) {
                    ChunkResult res = future.get();
                    chunkResults.add(res);
                    totalRows += res.rowCount;
                }
            } catch (ExecutionException ee) {
                Throwable cause = ee.getCause();
                if (cause instanceof CsvParseException) {
                    throw (CsvParseException) cause;
                }
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                throw new CsvParseException("Error parsing CSV chunks", cause);
            }
        }

        // 7. Assemble columnar vectors from chunk results
        List<ColumnVector> vectors = assembleVectors(colNames, colTypes, colTypeIds, chunkResults, totalRows, useSimd);

        // 8. Load into DataFrame
        DataFrame df = DataFrame.fromVectors(vectors);
        target.setDataFrame(df);
    }

    private static byte resolveTypeId(Class<?> t) {
        if (t == int.class || t == Integer.class) return SimdCsvScanner.TYPE_INT;
        if (t == long.class || t == Long.class) return SimdCsvScanner.TYPE_LONG;
        if (t == double.class || t == Double.class) return SimdCsvScanner.TYPE_DOUBLE;
        if (t == float.class || t == Float.class) return SimdCsvScanner.TYPE_FLOAT;
        if (t == boolean.class || t == Boolean.class) return SimdCsvScanner.TYPE_BOOLEAN;
        if (t == String.class) return SimdCsvScanner.TYPE_STRING;
        return SimdCsvScanner.TYPE_OBJECT;
    }

    private static ChunkResult executeChunk(
            byte[] fullBytes, MappedByteBuffer mappedBuf, int start, int end,
            byte delim, byte quote, byte escape,
            int colCount, Class<?>[] colTypes, byte[] colTypeIds,
            Charset charset, CSVImporterConfiguration config, boolean useSimd) {

        if (useSimd) {
            if (fullBytes != null) {
                return SimdCsvScanner.parseChunkSimd(fullBytes, start, end,
                        delim, quote, escape, colCount, colTypes, colTypeIds, charset, config);
            } else if (mappedBuf != null) {
                int len = end - start;
                byte[] chunkBytes = new byte[len];
                ByteBuffer slice = mappedBuf.duplicate();
                slice.position(start);
                slice.get(chunkBytes, 0, len);
                return SimdCsvScanner.parseChunkSimd(chunkBytes, 0, len,
                        delim, quote, escape, colCount, colTypes, colTypeIds, charset, config);
            }
        }
        return parseChunkScalar(fullBytes, mappedBuf, start, end, delim, quote, escape, colCount, colTypes, charset, config);
    }

    private static byte getByte(byte[] fullBytes, MappedByteBuffer mappedBuf, int idx) {
        return fullBytes != null ? fullBytes[idx] : mappedBuf.get(idx);
    }

    private static int findLineBreak(byte[] fullBytes, MappedByteBuffer mappedBuf,
                                     int start, int end, byte quote, byte escape,
                                     boolean initialInsideQuote, boolean useSimd) {
        if (useSimd && fullBytes != null) {
            return SimdCsvScanner.findLineBreak(fullBytes, start, end, quote, escape, initialInsideQuote);
        }
        boolean inQuote = initialInsideQuote;
        boolean hasEscape = (escape != ICSVParser.NULL_CHARACTER && escape != quote);
        boolean hasQuote = (quote != ICSVParser.NULL_CHARACTER);
        for (int i = start; i < end; i++) {
            byte b = getByte(fullBytes, mappedBuf, i);
            if (hasEscape && b == escape && i + 1 < end) {
                i++;
                continue;
            }
            if (hasQuote && b == quote) {
                inQuote = !inQuote;
            } else if (!inQuote && (b == '\n' || b == '\r')) {
                return i;
            }
        }
        return -1;
    }

    private static int skipLineBreak(byte[] fullBytes, MappedByteBuffer mappedBuf, int pos, int length) {
        if (pos >= length) return length;
        byte b = getByte(fullBytes, mappedBuf, pos);
        if (b == '\r') {
            pos++;
            if (pos < length && getByte(fullBytes, mappedBuf, pos) == '\n') {
                pos++;
            }
            return pos;
        } else if (b == '\n') {
            return pos + 1;
        }
        return pos;
    }

    private static List<String> parseRowStrings(byte[] fullBytes, MappedByteBuffer mappedBuf, int start, int end,
                                                byte delim, byte quote, byte escape, Charset charset) {
        List<String> fields = new ArrayList<>();
        int curr = start;
        boolean inQuote = false;
        byte[] buf = new byte[Math.max(64, end - start)];
        int bufLen = 0;

        while (curr < end) {
            byte b = getByte(fullBytes, mappedBuf, curr);
            if (b == escape && escape != ICSVParser.NULL_CHARACTER && curr + 1 < end) {
                if (bufLen >= buf.length) buf = Arrays.copyOf(buf, buf.length * 2);
                buf[bufLen++] = getByte(fullBytes, mappedBuf, curr + 1);
                curr += 2;
                continue;
            }
            if (b == quote && quote != ICSVParser.NULL_CHARACTER) {
                if (inQuote && curr + 1 < end && getByte(fullBytes, mappedBuf, curr + 1) == quote) {
                    if (bufLen >= buf.length) buf = Arrays.copyOf(buf, buf.length * 2);
                    buf[bufLen++] = quote;
                    curr += 2;
                    continue;
                }
                inQuote = !inQuote;
                curr++;
                continue;
            }

            if (!inQuote && b == delim) {
                fields.add(new String(buf, 0, bufLen, charset).trim());
                bufLen = 0;
                curr++;
                continue;
            }

            if (bufLen >= buf.length) buf = Arrays.copyOf(buf, buf.length * 2);
            buf[bufLen++] = b;
            curr++;
        }

        fields.add(new String(buf, 0, bufLen, charset).trim());
        return fields;
    }

    private void inferTypesFromSampleZeroAlloc(byte[] buf, int bodyStart, int length,
                                               byte delim, byte quote, byte escape,
                                               int colCount, Class<?>[] colTypes) {
        int maxSampleLines = 64;
        boolean[] allBoolean = new boolean[colCount];
        boolean[] allInt = new boolean[colCount];
        boolean[] allLong = new boolean[colCount];
        boolean[] allDouble = new boolean[colCount];
        boolean[] allLocalDate = new boolean[colCount];
        boolean[] allLocalDateTime = new boolean[colCount];
        boolean[] allInstant = new boolean[colCount];
        boolean[] hasValue = new boolean[colCount];

        Arrays.fill(allBoolean, true);
        Arrays.fill(allInt, true);
        Arrays.fill(allLong, true);
        Arrays.fill(allDouble, true);
        Arrays.fill(allLocalDate, true);
        Arrays.fill(allLocalDateTime, true);
        Arrays.fill(allInstant, true);

        boolean fullInference = config.isInferTypes();
        boolean hasQuote = (quote != ICSVParser.NULL_CHARACTER);
        boolean hasEscape = (escape != ICSVParser.NULL_CHARACTER && escape != quote);

        int curr = bodyStart;
        int linesCount = 0;

        while (curr < length && linesCount < maxSampleLines) {
            int colIdx = 0;
            boolean inQuote = false;
            int fStart = curr;

            while (curr < length) {
                byte b = buf[curr];
                if (hasEscape && b == escape && curr + 1 < length) {
                    curr += 2;
                    continue;
                }
                if (hasQuote && b == quote) {
                    if (inQuote && curr + 1 < length && buf[curr + 1] == quote) {
                        curr += 2;
                        continue;
                    }
                    inQuote = !inQuote;
                    curr++;
                    continue;
                }
                if (!inQuote) {
                    if (b == delim) {
                        if (colIdx < colCount && colTypes[colIdx] == null) {
                            inspectSampleCellBytes(buf, fStart, curr, quote, colIdx, fullInference,
                                    hasValue, allBoolean, allInt, allLong, allDouble,
                                    allLocalDate, allLocalDateTime, allInstant);
                        }
                        colIdx++;
                        curr++;
                        fStart = curr;
                        continue;
                    } else if (b == '\n' || b == '\r') {
                        break;
                    }
                }
                curr++;
            }

            if (colIdx < colCount && colTypes[colIdx] == null) {
                inspectSampleCellBytes(buf, fStart, curr, quote, colIdx, fullInference,
                        hasValue, allBoolean, allInt, allLong, allDouble,
                        allLocalDate, allLocalDateTime, allInstant);
            }
            colIdx++;
            if (colIdx == colCount) {
                linesCount++;
            }
            if (curr >= length) break;
            curr = skipLineBreak(buf, null, curr, length);
        }

        assignInferredColumnTypes(colCount, colTypes, hasValue, allBoolean, allInt, allLong, allDouble,
                allLocalDate, allLocalDateTime, allInstant);
    }

    private static void inspectSampleCellBytes(
            byte[] buf, int start, int end, byte quote, int c, boolean fullInference,
            boolean[] hasValue, boolean[] allBoolean, boolean[] allInt, boolean[] allLong,
            boolean[] allDouble, boolean[] allLocalDate, boolean[] allLocalDateTime, boolean[] allInstant) {

        while (start < end && buf[start] <= ' ' && (buf[start] == ' ' || buf[start] == '\t' || buf[start] == '\r')) {
            start++;
        }
        while (end > start && buf[end - 1] <= ' ' && (buf[end - 1] == ' ' || buf[end - 1] == '\t' || buf[end - 1] == '\r')) {
            end--;
        }
        if (end - start >= 2 && buf[start] == quote && buf[end - 1] == quote) {
            start++;
            end--;
        }
        if (FastByteParser.isNaOrEmpty(buf, start, end)) {
            return;
        }
        hasValue[c] = true;
        if (allInt[c] && !isIntegerBytes(buf, start, end)) allInt[c] = false;
        if (allDouble[c] && !isDoubleBytes(buf, start, end)) allDouble[c] = false;
        if (fullInference) {
            if (allBoolean[c] && !isBooleanBytes(buf, start, end)) allBoolean[c] = false;
            if (allLong[c] && !isLongBytes(buf, start, end)) allLong[c] = false;
            if (allLocalDate[c] && !isLocalDateBytes(buf, start, end)) allLocalDate[c] = false;
            if (allLocalDateTime[c] && !isLocalDateTimeBytes(buf, start, end)) allLocalDateTime[c] = false;
            if (allInstant[c] && !isInstantBytes(buf, start, end)) allInstant[c] = false;
        }
    }

    private static boolean isBooleanBytes(byte[] buf, int start, int end) {
        int len = end - start;
        if (len == 4) {
            return (buf[start] == 't' || buf[start] == 'T')
                && (buf[start + 1] == 'r' || buf[start + 1] == 'R')
                && (buf[start + 2] == 'u' || buf[start + 2] == 'U')
                && (buf[start + 3] == 'e' || buf[start + 3] == 'E');
        }
        if (len == 5) {
            return (buf[start] == 'f' || buf[start] == 'F')
                && (buf[start + 1] == 'a' || buf[start + 1] == 'A')
                && (buf[start + 2] == 'l' || buf[start + 2] == 'L')
                && (buf[start + 3] == 's' || buf[start + 3] == 'S')
                && (buf[start + 4] == 'e' || buf[start + 4] == 'E');
        }
        return false;
    }

    private static boolean isIntegerBytes(byte[] buf, int start, int end) {
        int len = end - start;
        if (len == 0 || len > 11) return false;
        int i = start;
        byte first = buf[i];
        if (first == '-' || first == '+') {
            if (len == 1) return false;
            i++;
        }
        int digits = end - i;
        if (digits > 10) return false;
        long val = 0L;
        while (i < end) {
            byte b = buf[i++];
            if (b < '0' || b > '9') return false;
            val = val * 10L + (b - '0');
        }
        if (first == '-') {
            return -val >= Integer.MIN_VALUE;
        }
        return val <= Integer.MAX_VALUE;
    }

    private static boolean isLongBytes(byte[] buf, int start, int end) {
        int len = end - start;
        if (len == 0 || len > 20) return false;
        int i = start;
        byte first = buf[i];
        if (first == '-' || first == '+') {
            if (len == 1) return false;
            i++;
        }
        int digits = end - i;
        if (digits > 19) return false;
        for (int p = i; p < end; p++) {
            byte b = buf[p];
            if (b < '0' || b > '9') return false;
        }
        if (digits < 19) return true;
        try {
            FastByteParser.parseLong(buf, start, end);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isDoubleBytes(byte[] buf, int start, int end) {
        int len = end - start;
        if (len == 0) return false;
        int i = start;
        byte first = buf[i];
        if (first == '-' || first == '+') {
            i++;
            if (i == end) return false;
        }
        boolean hasDigits = false;
        while (i < end && buf[i] >= '0' && buf[i] <= '9') {
            hasDigits = true;
            i++;
        }
        if (i < end && buf[i] == '.') {
            i++;
            while (i < end && buf[i] >= '0' && buf[i] <= '9') {
                hasDigits = true;
                i++;
            }
        }
        if (!hasDigits) return false;
        if (i < end && (buf[i] == 'e' || buf[i] == 'E')) {
            i++;
            if (i < end && (buf[i] == '-' || buf[i] == '+')) {
                i++;
            }
            if (i == end) return false;
            while (i < end && buf[i] >= '0' && buf[i] <= '9') {
                i++;
            }
        }
        return i == end;
    }

    private static boolean isLocalDateBytes(byte[] buf, int start, int end) {
        if (end - start != 10 || buf[start + 4] != '-' || buf[start + 7] != '-') {
            return false;
        }
        return isLocalDateFast(new String(buf, start, 10, StandardCharsets.US_ASCII));
    }

    private static boolean isLocalDateTimeBytes(byte[] buf, int start, int end) {
        int len = end - start;
        if (len < 16 || buf[start + 4] != '-' || buf[start + 7] != '-' || buf[start + 10] != 'T') {
            return false;
        }
        return isLocalDateTimeFast(new String(buf, start, len, StandardCharsets.US_ASCII));
    }

    private static boolean isInstantBytes(byte[] buf, int start, int end) {
        int len = end - start;
        if (len < 20 || buf[start + 4] != '-' || buf[start + 10] != 'T' || buf[end - 1] != 'Z') {
            return false;
        }
        return isInstantFast(new String(buf, start, len, StandardCharsets.US_ASCII));
    }

    private void inferTypesFromSampleScalar(byte[] fullBytes, MappedByteBuffer mappedBuf, int bodyStart, int length,
                                            byte delim, byte quote, byte escape, int colCount, Class<?>[] colTypes,
                                            Charset charset) {
        int curr = bodyStart;
        int linesCount = 0;
        int maxSampleLines = 1000;

        boolean[] allBoolean = new boolean[colCount];
        boolean[] allInt = new boolean[colCount];
        boolean[] allLong = new boolean[colCount];
        boolean[] allDouble = new boolean[colCount];
        boolean[] allLocalDate = new boolean[colCount];
        boolean[] allLocalDateTime = new boolean[colCount];
        boolean[] allInstant = new boolean[colCount];
        boolean[] hasValue = new boolean[colCount];

        Arrays.fill(allBoolean, true);
        Arrays.fill(allInt, true);
        Arrays.fill(allLong, true);
        Arrays.fill(allDouble, true);
        Arrays.fill(allLocalDate, true);
        Arrays.fill(allLocalDateTime, true);
        Arrays.fill(allInstant, true);

        Set<String> naStrings = config.getNaStrings();

        while (curr < length && linesCount < maxSampleLines) {
            int lineEnd = findLineBreak(fullBytes, mappedBuf, curr, length, quote, escape, false, false);
            int end = (lineEnd == -1) ? length : lineEnd;
            if (end > curr) {
                List<String> rowFields = parseRowStrings(fullBytes, mappedBuf, curr, end, delim, quote, escape, charset);
                if (rowFields.size() == colCount) {
                    for (int c = 0; c < colCount; c++) {
                        if (colTypes[c] != null) continue;
                        String raw = rowFields.get(c);
                        if (raw == null || raw.isEmpty() || (naStrings != null && naStrings.contains(raw))) {
                            continue;
                        }
                        hasValue[c] = true;
                        if (allBoolean[c] && !isBoolean(raw)) allBoolean[c] = false;
                        if (allInt[c] && !isIntegerFast(raw)) allInt[c] = false;
                        if (allLong[c] && !isLongFast(raw)) allLong[c] = false;
                        if (allDouble[c] && !isDoubleFast(raw)) allDouble[c] = false;
                        if (allLocalDate[c] && !isLocalDateFast(raw)) allLocalDate[c] = false;
                        if (allLocalDateTime[c] && !isLocalDateTimeFast(raw)) allLocalDateTime[c] = false;
                        if (allInstant[c] && !isInstantFast(raw)) allInstant[c] = false;
                    }
                    linesCount++;
                }
            }
            if (lineEnd == -1) break;
            curr = skipLineBreak(fullBytes, mappedBuf, lineEnd, length);
        }

        assignInferredColumnTypes(colCount, colTypes, hasValue, allBoolean, allInt, allLong, allDouble,
                allLocalDate, allLocalDateTime, allInstant);
    }

    private void assignInferredColumnTypes(int colCount, Class<?>[] colTypes,
                                           boolean[] hasValue, boolean[] allBoolean,
                                           boolean[] allInt, boolean[] allLong, boolean[] allDouble,
                                           boolean[] allLocalDate, boolean[] allLocalDateTime, boolean[] allInstant) {
        for (int i = 0; i < colCount; i++) {
            if (colTypes[i] != null) continue;

            if (config.isInferTypes()) {
                if (!hasValue[i]) {
                    colTypes[i] = String.class;
                } else if (allBoolean[i]) {
                    colTypes[i] = Boolean.class;
                } else if (allInt[i]) {
                    colTypes[i] = Integer.class;
                } else if (allLong[i]) {
                    colTypes[i] = Long.class;
                } else if (allDouble[i]) {
                    colTypes[i] = Double.class;
                } else if (allLocalDate[i]) {
                    colTypes[i] = LocalDate.class;
                } else if (allLocalDateTime[i]) {
                    colTypes[i] = LocalDateTime.class;
                } else if (allInstant[i]) {
                    colTypes[i] = Instant.class;
                } else {
                    colTypes[i] = String.class;
                }
            } else if (config.isInferNumericTypes()) {
                if (!hasValue[i]) {
                    colTypes[i] = String.class;
                } else if (allInt[i]) {
                    colTypes[i] = Integer.class;
                } else if (allDouble[i]) {
                    colTypes[i] = Double.class;
                } else {
                    colTypes[i] = String.class;
                }
            } else {
                colTypes[i] = String.class;
            }
        }
    }

    private static boolean isBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }

    private static boolean isIntegerFast(String s) {
        int len = s.length();
        if (len == 0 || len > 11) return false;
        int start = 0;
        char first = s.charAt(0);
        if (first == '-' || first == '+') {
            if (len == 1) return false;
            start = 1;
        }
        int digits = len - start;
        if (digits > 10) return false;
        for (int i = start; i < len; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        if (digits < 10) return true;
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isLongFast(String s) {
        int len = s.length();
        if (len == 0 || len > 20) return false;
        int start = 0;
        char first = s.charAt(0);
        if (first == '-' || first == '+') {
            if (len == 1) return false;
            start = 1;
        }
        int digits = len - start;
        if (digits > 19) return false;
        for (int i = start; i < len; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        if (digits < 19) return true;
        try {
            Long.parseLong(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isDoubleFast(String s) {
        int len = s.length();
        if (len == 0) return false;
        int i = 0;
        char first = s.charAt(0);
        if (first == '-' || first == '+') {
            i++;
            if (i == len) return false;
        }
        boolean hasDigits = false;
        while (i < len && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
            hasDigits = true;
            i++;
        }
        if (i < len && s.charAt(i) == '.') {
            i++;
            while (i < len && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                hasDigits = true;
                i++;
            }
        }
        if (!hasDigits) return false;
        if (i < len && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            i++;
            if (i < len && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
                i++;
            }
            if (i == len) return false;
            while (i < len && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                i++;
            }
        }
        return i == len;
    }

    private static boolean isLocalDateFast(String value) {
        if (value.length() != 10 || value.charAt(4) != '-' || value.charAt(7) != '-') {
            return false;
        }
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean isLocalDateTimeFast(String value) {
        if (value.length() < 16 || value.charAt(4) != '-' || value.charAt(7) != '-' || value.charAt(10) != 'T') {
            return false;
        }
        try {
            LocalDateTime.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean isInstantFast(String value) {
        int len = value.length();
        if (len < 20 || value.charAt(4) != '-' || value.charAt(10) != 'T' || value.charAt(len - 1) != 'Z') {
            return false;
        }
        try {
            Instant.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private record ChunkRange(int start, int end) {}

    private static List<ChunkRange> partitionChunks(byte[] fullBytes, MappedByteBuffer mappedBuf,
                                                    int start, int length, int numChunks,
                                                    byte quote, byte escape, boolean useSimd) {
        List<ChunkRange> ranges = new ArrayList<>(numChunks);
        if (start >= length) {
            return ranges;
        }
        if (numChunks <= 1) {
            ranges.add(new ChunkRange(start, length));
            return ranges;
        }

        int totalLen = length - start;
        int chunkTarget = totalLen / numChunks;
        int currStart = start;
        boolean insideQuote = false;
        int parityPos = start;

        for (int i = 0; i < numChunks - 1; i++) {
            int targetEnd = currStart + chunkTarget;
            if (targetEnd >= length) {
                break;
            }
            if (quote != ICSVParser.NULL_CHARACTER && fullBytes != null) {
                if (useSimd) {
                    insideQuote = SimdCsvScanner.scanQuoteParity(fullBytes, parityPos, targetEnd, quote, escape, insideQuote);
                } else {
                    insideQuote = scanQuoteParityScalar(fullBytes, parityPos, targetEnd, quote, escape, insideQuote);
                }
                parityPos = targetEnd;
            }
            int breakPos = findLineBreak(fullBytes, mappedBuf, targetEnd, length, quote, escape, insideQuote, useSimd);
            if (breakPos == -1) {
                break;
            }
            int nextStart = skipLineBreak(fullBytes, mappedBuf, breakPos, length);
            ranges.add(new ChunkRange(currStart, nextStart));
            currStart = nextStart;
            insideQuote = false;
            parityPos = nextStart;
        }

        if (currStart < length) {
            ranges.add(new ChunkRange(currStart, length));
        }

        return ranges;
    }

    private static boolean scanQuoteParityScalar(byte[] buf, int start, int end, byte quote, byte escape, boolean insideQuote) {
        boolean hasEscape = (escape != ICSVParser.NULL_CHARACTER && escape != quote);
        for (int i = start; i < end; i++) {
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

    static final class ChunkResult {
        final int rowCount;
        final Object[] columnData;
        final BitSet[] nullBitsets;

        ChunkResult(int rowCount, Object[] columnData, BitSet[] nullBitsets) {
            this.rowCount = rowCount;
            this.columnData = columnData;
            this.nullBitsets = nullBitsets;
        }
    }

    /**
     * Scalar fallback chunk parser for platforms without Java Vector API SIMD support
     * or when SIMD acceleration is explicitly disabled.
     */
    private static ChunkResult parseChunkScalar(byte[] fullBytes, MappedByteBuffer mappedBuf, int start, int end,
                                                byte delim, byte quote, byte escape, int colCount, Class<?>[] colTypes,
                                                Charset charset, CSVImporterConfiguration config) {
        int estimatedRows = Math.max(16, (end - start) / Math.max(10, colCount * 8));

        Object[] colData = new Object[colCount];
        BitSet[] nullBitsets = new BitSet[colCount];

        for (int c = 0; c < colCount; c++) {
            Class<?> t = colTypes[c];
            nullBitsets[c] = new BitSet(estimatedRows);
            if (t == int.class || t == Integer.class) {
                colData[c] = new int[estimatedRows];
            } else if (t == long.class || t == Long.class) {
                colData[c] = new long[estimatedRows];
            } else if (t == double.class || t == Double.class) {
                colData[c] = new double[estimatedRows];
            } else if (t == float.class || t == Float.class) {
                colData[c] = new float[estimatedRows];
            } else if (t == boolean.class || t == Boolean.class) {
                colData[c] = new boolean[estimatedRows];
            } else if (t == String.class) {
                colData[c] = new String[estimatedRows];
            } else {
                colData[c] = new Object[estimatedRows];
            }
        }

        FastStringPool stringPool = new FastStringPool(charset);
        byte[] fieldBuf = new byte[256];
        int curr = start;
        int row = 0;

        while (curr < end) {
            if (row >= getCapacity(colData[0], colTypes[0])) {
                int newCap = getCapacity(colData[0], colTypes[0]) * 2;
                for (int c = 0; c < colCount; c++) {
                    colData[c] = resizeArray(colData[c], colTypes[c], newCap);
                }
            }

            int colIdx = 0;
            boolean inQuote = false;
            boolean hadQuote = false;
            int fStart = curr;
            int fieldLen = 0;

            while (curr < end) {
                byte b = getByte(fullBytes, mappedBuf, curr);

                if (b == escape && escape != ICSVParser.NULL_CHARACTER && escape != quote && curr + 1 < end) {
                    hadQuote = true;
                    if (fieldLen >= fieldBuf.length) {
                        fieldBuf = Arrays.copyOf(fieldBuf, fieldBuf.length * 2);
                    }
                    fieldBuf[fieldLen++] = getByte(fullBytes, mappedBuf, curr + 1);
                    curr += 2;
                    continue;
                }

                if (b == quote && quote != ICSVParser.NULL_CHARACTER) {
                    hadQuote = true;
                    if (inQuote && curr + 1 < end && getByte(fullBytes, mappedBuf, curr + 1) == quote) {
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

                if (!inQuote) {
                    if (b == delim) {
                        parseFieldScalar(fullBytes, mappedBuf, fStart, curr, hadQuote, fieldBuf, fieldLen,
                                colIdx, row, colTypes, colData, nullBitsets, stringPool, charset, config);
                        colIdx++;
                        curr++;
                        fStart = curr;
                        hadQuote = false;
                        fieldLen = 0;
                        continue;
                    } else if (b == '\n' || b == '\r') {
                        break;
                    }
                }

                if (hadQuote) {
                    if (fieldLen >= fieldBuf.length) {
                        fieldBuf = Arrays.copyOf(fieldBuf, fieldBuf.length * 2);
                    }
                    fieldBuf[fieldLen++] = b;
                }
                curr++;
            }

            parseFieldScalar(fullBytes, mappedBuf, fStart, curr, hadQuote, fieldBuf, fieldLen,
                    colIdx, row, colTypes, colData, nullBitsets, stringPool, charset, config);
            colIdx++;

            if (colIdx != colCount) {
                throw new CsvParseException("Row length mismatch: expected " + colCount + " columns, but got " + colIdx);
            }

            row++;
            curr = skipLineBreak(fullBytes, mappedBuf, curr, end);
        }

        return new ChunkResult(row, colData, nullBitsets);
    }

    private static int getCapacity(Object arr, Class<?> type) {
        if (type == int.class || type == Integer.class) return ((int[]) arr).length;
        if (type == long.class || type == Long.class) return ((long[]) arr).length;
        if (type == double.class || type == Double.class) return ((double[]) arr).length;
        if (type == float.class || type == Float.class) return ((float[]) arr).length;
        if (type == boolean.class || type == Boolean.class) return ((boolean[]) arr).length;
        if (type == String.class) return ((String[]) arr).length;
        return ((Object[]) arr).length;
    }

    private static Object resizeArray(Object arr, Class<?> type, int newCap) {
        if (type == int.class || type == Integer.class) return Arrays.copyOf((int[]) arr, newCap);
        if (type == long.class || type == Long.class) return Arrays.copyOf((long[]) arr, newCap);
        if (type == double.class || type == Double.class) return Arrays.copyOf((double[]) arr, newCap);
        if (type == float.class || type == Float.class) return Arrays.copyOf((float[]) arr, newCap);
        if (type == boolean.class || type == Boolean.class) return Arrays.copyOf((boolean[]) arr, newCap);
        if (type == String.class) return Arrays.copyOf((String[]) arr, newCap);
        return Arrays.copyOf((Object[]) arr, newCap);
    }

    private static void parseFieldScalar(byte[] fullBytes, MappedByteBuffer mappedBuf, int start, int end,
                                         boolean hadQuote, byte[] fieldBuf, int fieldLen,
                                         int colIdx, int row, Class<?>[] colTypes, Object[] colData,
                                         BitSet[] nullBitsets, FastStringPool stringPool, Charset charset,
                                         CSVImporterConfiguration config) {
        if (colIdx >= colTypes.length) {
            throw new CsvParseException("Row length mismatch: extra column at index " + colIdx);
        }

        byte[] rawBuf;
        int rawStart;
        int rawEnd;

        if (hadQuote) {
            rawBuf = fieldBuf;
            rawStart = 0;
            rawEnd = fieldLen;
        } else {
            int len = end - start;
            if (fullBytes != null) {
                rawBuf = fullBytes;
                rawStart = start;
                rawEnd = end;
            } else {
                if (fieldBuf.length < len) {
                    fieldBuf = new byte[Math.max(fieldBuf.length * 2, len)];
                }
                for (int i = 0; i < len; i++) {
                    fieldBuf[i] = mappedBuf.get(start + i);
                }
                rawBuf = fieldBuf;
                rawStart = 0;
                rawEnd = len;
            }
        }

        Class<?> type = colTypes[colIdx];

        if (type == int.class || type == Integer.class) {
            if (FastByteParser.isNaOrEmpty(rawBuf, rawStart, rawEnd)) {
                nullBitsets[colIdx].set(row);
            } else {
                ((int[]) colData[colIdx])[row] = FastByteParser.parseInt(rawBuf, rawStart, rawEnd);
            }
        } else if (type == long.class || type == Long.class) {
            if (FastByteParser.isNaOrEmpty(rawBuf, rawStart, rawEnd)) {
                nullBitsets[colIdx].set(row);
            } else {
                ((long[]) colData[colIdx])[row] = FastByteParser.parseLong(rawBuf, rawStart, rawEnd);
            }
        } else if (type == double.class || type == Double.class) {
            if (FastByteParser.isNaOrEmpty(rawBuf, rawStart, rawEnd)) {
                nullBitsets[colIdx].set(row);
            } else {
                ((double[]) colData[colIdx])[row] = FastByteParser.parseDouble(rawBuf, rawStart, rawEnd);
            }
        } else if (type == float.class || type == Float.class) {
            if (FastByteParser.isNaOrEmpty(rawBuf, rawStart, rawEnd)) {
                nullBitsets[colIdx].set(row);
            } else {
                ((float[]) colData[colIdx])[row] = FastByteParser.parseFloat(rawBuf, rawStart, rawEnd);
            }
        } else if (type == boolean.class || type == Boolean.class) {
            if (FastByteParser.isNaOrEmpty(rawBuf, rawStart, rawEnd)) {
                nullBitsets[colIdx].set(row);
            } else {
                ((boolean[]) colData[colIdx])[row] = FastByteParser.parseBoolean(rawBuf, rawStart, rawEnd);
            }
        } else if (type == String.class) {
            if (!hadQuote && FastByteParser.isNaOrEmpty(rawBuf, rawStart, rawEnd)) {
                nullBitsets[colIdx].set(row);
            } else if (hadQuote && rawEnd == 0 && !config.isPreserveEmptyStrings()) {
                nullBitsets[colIdx].set(row);
            } else {
                ((String[]) colData[colIdx])[row] = stringPool.getOrCreate(rawBuf, rawStart, rawEnd - rawStart);
            }
        } else {
            if (FastByteParser.isNaOrEmpty(rawBuf, rawStart, rawEnd)) {
                nullBitsets[colIdx].set(row);
            } else {
                String strVal = stringPool.getOrCreate(rawBuf, rawStart, rawEnd - rawStart).trim();
                ((Object[]) colData[colIdx])[row] = convertObjectValue(strVal, type);
            }
        }
    }

    static Object convertObjectValue(String str, Class<?> type) {
        if (type == LocalDate.class) {
            return LocalDate.parse(str);
        }
        if (type == LocalDateTime.class) {
            return LocalDateTime.parse(str);
        }
        if (type == Instant.class) {
            return Instant.parse(str);
        }
        if (type == BigDecimal.class) {
            return new BigDecimal(str);
        }
        return str;
    }

    private static List<ColumnVector> assembleVectors(String[] colNames, Class<?>[] colTypes, byte[] colTypeIds,
                                                      List<ChunkResult> chunkResults, int totalRows, boolean useSimd) {
        int colCount = colNames.length;
        List<ColumnVector> vectors = new ArrayList<>(colCount);

        if (useSimd && chunkResults.size() == 1) {
            ChunkResult single = chunkResults.get(0);
            for (int c = 0; c < colCount; c++) {
                String name = colNames[c];
                BitSet nulls = single.nullBitsets[c];
                switch (colTypeIds[c]) {
                    case SimdCsvScanner.TYPE_INT ->
                        vectors.add(new IntColumnVector(name, true, (int[]) single.columnData[c], nulls, totalRows));
                    case SimdCsvScanner.TYPE_LONG ->
                        vectors.add(new LongColumnVector(name, true, (long[]) single.columnData[c], nulls, totalRows));
                    case SimdCsvScanner.TYPE_DOUBLE ->
                        vectors.add(new DoubleColumnVector(name, true, (double[]) single.columnData[c], nulls, totalRows));
                    case SimdCsvScanner.TYPE_FLOAT ->
                        vectors.add(new FloatColumnVector(name, true, (float[]) single.columnData[c], nulls, totalRows));
                    case SimdCsvScanner.TYPE_BOOLEAN ->
                        vectors.add(new BooleanColumnVector(name, true, (boolean[]) single.columnData[c], nulls, totalRows));
                    case SimdCsvScanner.TYPE_STRING ->
                        vectors.add(new StringColumnVector(name, true, (String[]) single.columnData[c], nulls, totalRows));
                    default ->
                        vectors.add(new ObjectColumnVector(name, colTypes[c], true, (Object[]) single.columnData[c], nulls, totalRows));
                }
            }
            return vectors;
        }

        for (int c = 0; c < colCount; c++) {
            String name = colNames[c];
            BitSet nulls = new BitSet(totalRows);

            switch (colTypeIds[c]) {
                case SimdCsvScanner.TYPE_INT -> {
                    int[] data = new int[totalRows];
                    int offset = 0;
                    for (ChunkResult chunk : chunkResults) {
                        int rows = chunk.rowCount;
                        System.arraycopy((int[]) chunk.columnData[c], 0, data, offset, rows);
                        copyNullBits(chunk.nullBitsets[c], nulls, offset);
                        offset += rows;
                    }
                    vectors.add(new IntColumnVector(name, true, data, nulls, totalRows));
                }
                case SimdCsvScanner.TYPE_LONG -> {
                    long[] data = new long[totalRows];
                    int offset = 0;
                    for (ChunkResult chunk : chunkResults) {
                        int rows = chunk.rowCount;
                        System.arraycopy((long[]) chunk.columnData[c], 0, data, offset, rows);
                        copyNullBits(chunk.nullBitsets[c], nulls, offset);
                        offset += rows;
                    }
                    vectors.add(new LongColumnVector(name, true, data, nulls, totalRows));
                }
                case SimdCsvScanner.TYPE_DOUBLE -> {
                    double[] data = new double[totalRows];
                    int offset = 0;
                    for (ChunkResult chunk : chunkResults) {
                        int rows = chunk.rowCount;
                        System.arraycopy((double[]) chunk.columnData[c], 0, data, offset, rows);
                        copyNullBits(chunk.nullBitsets[c], nulls, offset);
                        offset += rows;
                    }
                    vectors.add(new DoubleColumnVector(name, true, data, nulls, totalRows));
                }
                case SimdCsvScanner.TYPE_FLOAT -> {
                    float[] data = new float[totalRows];
                    int offset = 0;
                    for (ChunkResult chunk : chunkResults) {
                        int rows = chunk.rowCount;
                        System.arraycopy((float[]) chunk.columnData[c], 0, data, offset, rows);
                        copyNullBits(chunk.nullBitsets[c], nulls, offset);
                        offset += rows;
                    }
                    vectors.add(new FloatColumnVector(name, true, data, nulls, totalRows));
                }
                case SimdCsvScanner.TYPE_BOOLEAN -> {
                    boolean[] data = new boolean[totalRows];
                    int offset = 0;
                    for (ChunkResult chunk : chunkResults) {
                        int rows = chunk.rowCount;
                        System.arraycopy((boolean[]) chunk.columnData[c], 0, data, offset, rows);
                        copyNullBits(chunk.nullBitsets[c], nulls, offset);
                        offset += rows;
                    }
                    vectors.add(new BooleanColumnVector(name, true, data, nulls, totalRows));
                }
                case SimdCsvScanner.TYPE_STRING -> {
                    String[] data = new String[totalRows];
                    int offset = 0;
                    for (ChunkResult chunk : chunkResults) {
                        int rows = chunk.rowCount;
                        System.arraycopy((String[]) chunk.columnData[c], 0, data, offset, rows);
                        copyNullBits(chunk.nullBitsets[c], nulls, offset);
                        offset += rows;
                    }
                    vectors.add(new StringColumnVector(name, true, data, nulls, totalRows));
                }
                default -> {
                    Object[] data = new Object[totalRows];
                    int offset = 0;
                    for (ChunkResult chunk : chunkResults) {
                        int rows = chunk.rowCount;
                        System.arraycopy((Object[]) chunk.columnData[c], 0, data, offset, rows);
                        copyNullBits(chunk.nullBitsets[c], nulls, offset);
                        offset += rows;
                    }
                    vectors.add(new ObjectColumnVector(name, colTypes[c], true, data, nulls, totalRows));
                }
            }
        }

        return vectors;
    }

    private static void copyNullBits(BitSet src, BitSet dst, int offset) {
        if (!src.isEmpty()) {
            for (int r = src.nextSetBit(0); r >= 0; r = src.nextSetBit(r + 1)) {
                dst.set(offset + r);
            }
        }
    }
}
