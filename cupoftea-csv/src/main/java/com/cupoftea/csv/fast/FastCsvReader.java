package com.cupoftea.csv.fast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
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
import java.util.regex.Pattern;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.CsvParseException;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.configurations.CSVImporterConfiguration;
import com.cupoftea.table.Column;
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
 * High-performance chunk-based parallel CSV reader using direct byte parsing,
 * zero/low object allocation, and NIO memory-mapping / buffered block reading.
 */
public class FastCsvReader {

    private static final Pattern DOUBLE_PATTERN = Pattern.compile("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?$");

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
        // Supported delimiters: ASCII delimiters
        char delim = config.getDelimiter();
        if (delim > 127) {
            return false;
        }
        char quote = config.getQuoteChar();
        if (quote > 127 && quote != ICSVParser.NULL_CHARACTER) {
            return false;
        }
        // Charset must be standard single-byte or UTF-8
        Charset cs = config.getCharset();
        if (!StandardCharsets.UTF_8.equals(cs) && !StandardCharsets.ISO_8859_1.equals(cs) && !StandardCharsets.US_ASCII.equals(cs)) {
            return false;
        }
        return true;
    }

    /**
     * Reads the CSV file directly into the target DataFrame using parallel byte chunking.
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

            // For files under 100MB, reading into byte[] provides fastest sequential memory access
            if (fileLength < 100 * 1024 * 1024) {
                fullBytes = new byte[(int) fileLength];
                int offset = 0;
                while (offset < fileLength) {
                    int read = fis.read(fullBytes, offset, (int) (fileLength - offset));
                    if (read < 0) break;
                    offset += read;
                }
            } else {
                // Use MappedByteBuffer for large files
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

    private void parseBytes(byte[] fullBytes, MappedByteBuffer mappedBuf, int length, CupOfTea target) throws Exception {
        byte delim = (byte) config.getDelimiter();
        byte quote = (byte) config.getQuoteChar();
        byte escape = (byte) config.getEscapeChar();
        Charset charset = config.getCharset();

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
        int firstLineEnd = findLineBreak(fullBytes, mappedBuf, dataStart, length, quote);
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
        for (int i = 0; i < colCount; i++) {
            if (colTypes[i] == null) {
                needsInference = true;
                break;
            }
        }

        if (needsInference) {
            inferTypesFromSample(fullBytes, mappedBuf, bodyStart, length, delim, quote, escape, colCount, colTypes, charset);
        }

        for (int i = 0; i < colCount; i++) {
            if (colTypes[i] == null) {
                colTypes[i] = String.class;
            }
        }

        // 5. Partition file into chunks for multi-core parallelism
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int minChunkSize = 256 * 1024; // 256KB min chunk
        int bodyLength = length - bodyStart;

        int numChunks = 1;
        if (bodyLength > minChunkSize && availableProcessors > 1) {
            numChunks = Math.min(availableProcessors, Math.max(1, bodyLength / minChunkSize));
        }

        List<ChunkRange> chunks = partitionChunks(fullBytes, mappedBuf, bodyStart, length, numChunks, quote);

        // 6. Execute chunk parsing in parallel
        ExecutorService executor = ForkJoinPool.commonPool();
        List<Future<ChunkResult>> futures = new ArrayList<>(chunks.size());

        for (ChunkRange chunk : chunks) {
            futures.add(executor.submit(() -> parseChunk(fullBytes, mappedBuf, chunk.start, chunk.end, delim, quote, escape, colCount, colTypes, charset, config)));
        }

        List<ChunkResult> chunkResults = new ArrayList<>(chunks.size());
        int totalRows = 0;
        try {
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

        // 7. Assemble columnar vectors from chunk results
        List<ColumnVector> vectors = assembleVectors(colNames, colTypes, chunkResults, totalRows);

        // 8. Load into DataFrame
        DataFrame df = DataFrame.fromVectors(vectors);
        target.setDataFrame(df);
    }

    private static byte getByte(byte[] fullBytes, MappedByteBuffer mappedBuf, int idx) {
        return fullBytes != null ? fullBytes[idx] : mappedBuf.get(idx);
    }

    private static int findLineBreak(byte[] fullBytes, MappedByteBuffer mappedBuf, int start, int end, byte quote) {
        boolean inQuote = false;
        for (int i = start; i < end; i++) {
            byte b = getByte(fullBytes, mappedBuf, i);
            if (b == quote && quote != ICSVParser.NULL_CHARACTER) {
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
        StringBuilder sb = new StringBuilder();

        while (curr < end) {
            byte b = getByte(fullBytes, mappedBuf, curr);
            if (b == escape && escape != ICSVParser.NULL_CHARACTER && curr + 1 < end) {
                sb.append((char) getByte(fullBytes, mappedBuf, curr + 1));
                curr += 2;
                continue;
            }
            if (b == quote && quote != ICSVParser.NULL_CHARACTER) {
                if (inQuote && curr + 1 < end && getByte(fullBytes, mappedBuf, curr + 1) == quote) {
                    sb.append((char) quote);
                    curr += 2;
                    continue;
                }
                inQuote = !inQuote;
                curr++;
                continue;
            }

            if (!inQuote && b == delim) {
                fields.add(sb.toString().trim());
                sb.setLength(0);
                curr++;
                continue;
            }

            sb.append((char) b);
            curr++;
        }

        fields.add(sb.toString().trim());
        return fields;
    }

    private void inferTypesFromSample(byte[] fullBytes, MappedByteBuffer mappedBuf, int bodyStart, int length,
                                      byte delim, byte quote, byte escape, int colCount, Class<?>[] colTypes, Charset charset) {
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
            int lineEnd = findLineBreak(fullBytes, mappedBuf, curr, length, quote);
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
                        if (allInt[c] && !isInteger(raw)) allInt[c] = false;
                        if (allLong[c] && !isLong(raw)) allLong[c] = false;
                        if (allDouble[c] && !isDouble(raw)) allDouble[c] = false;
                        if (allLocalDate[c] && !isLocalDate(raw)) allLocalDate[c] = false;
                        if (allLocalDateTime[c] && !isLocalDateTime(raw)) allLocalDateTime[c] = false;
                        if (allInstant[c] && !isInstant(raw)) allInstant[c] = false;
                    }
                    linesCount++;
                }
            }
            if (lineEnd == -1) break;
            curr = skipLineBreak(fullBytes, mappedBuf, lineEnd, length);
        }

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

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isLong(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isDouble(String value) {
        if (!DOUBLE_PATTERN.matcher(value).matches()) {
            return false;
        }
        try {
            double parsed = Double.parseDouble(value);
            return !Double.isNaN(parsed) && !Double.isInfinite(parsed);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isLocalDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean isLocalDateTime(String value) {
        try {
            LocalDateTime.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean isInstant(String value) {
        try {
            Instant.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private record ChunkRange(int start, int end) {}

    private static List<ChunkRange> partitionChunks(byte[] fullBytes, MappedByteBuffer mappedBuf,
                                                   int start, int length, int numChunks, byte quote) {
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

        for (int i = 0; i < numChunks - 1; i++) {
            int targetEnd = currStart + chunkTarget;
            if (targetEnd >= length) {
                break;
            }
            int breakPos = findLineBreak(fullBytes, mappedBuf, targetEnd, length, quote);
            if (breakPos == -1) {
                break;
            }
            int nextStart = skipLineBreak(fullBytes, mappedBuf, breakPos, length);
            ranges.add(new ChunkRange(currStart, nextStart));
            currStart = nextStart;
        }

        if (currStart < length) {
            ranges.add(new ChunkRange(currStart, length));
        }

        return ranges;
    }

    private static final class ChunkResult {
        final int rowCount;
        final Object[] columnData;
        final BitSet[] nullBitsets;

        ChunkResult(int rowCount, Object[] columnData, BitSet[] nullBitsets) {
            this.rowCount = rowCount;
            this.columnData = columnData;
            this.nullBitsets = nullBitsets;
        }
    }

    private static ChunkResult parseChunk(byte[] fullBytes, MappedByteBuffer mappedBuf, int start, int end,
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

                if (b == escape && escape != ICSVParser.NULL_CHARACTER && curr + 1 < end) {
                    hadQuote = true; // Use fieldBuf for unescaping
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
                        parseField(fullBytes, mappedBuf, fStart, curr, hadQuote, fieldBuf, fieldLen,
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

            parseField(fullBytes, mappedBuf, fStart, curr, hadQuote, fieldBuf, fieldLen,
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

    private static void parseField(byte[] fullBytes, MappedByteBuffer mappedBuf, int start, int end,
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
            if (FastByteParser.isBlank(rawBuf, rawStart, rawEnd) || FastByteParser.isNaOrEmpty(rawBuf, rawStart, rawEnd)) {
                nullBitsets[colIdx].set(row);
            } else {
                ((int[]) colData[colIdx])[row] = FastByteParser.parseInt(rawBuf, rawStart, rawEnd);
            }
        } else if (type == long.class || type == Long.class) {
            if (FastByteParser.isBlank(rawBuf, rawStart, rawEnd) || FastByteParser.isNaOrEmpty(rawBuf, rawStart, rawEnd)) {
                nullBitsets[colIdx].set(row);
            } else {
                ((long[]) colData[colIdx])[row] = FastByteParser.parseLong(rawBuf, rawStart, rawEnd);
            }
        } else if (type == double.class || type == Double.class) {
            if (FastByteParser.isBlank(rawBuf, rawStart, rawEnd) || FastByteParser.isNaOrEmpty(rawBuf, rawStart, rawEnd)) {
                nullBitsets[colIdx].set(row);
            } else {
                ((double[]) colData[colIdx])[row] = FastByteParser.parseDouble(rawBuf, rawStart, rawEnd);
            }
        } else if (type == float.class || type == Float.class) {
            if (FastByteParser.isBlank(rawBuf, rawStart, rawEnd) || FastByteParser.isNaOrEmpty(rawBuf, rawStart, rawEnd)) {
                nullBitsets[colIdx].set(row);
            } else {
                ((float[]) colData[colIdx])[row] = FastByteParser.parseFloat(rawBuf, rawStart, rawEnd);
            }
        } else if (type == boolean.class || type == Boolean.class) {
            if (FastByteParser.isBlank(rawBuf, rawStart, rawEnd) || FastByteParser.isNaOrEmpty(rawBuf, rawStart, rawEnd)) {
                nullBitsets[colIdx].set(row);
            } else {
                ((boolean[]) colData[colIdx])[row] = FastByteParser.parseBoolean(rawBuf, rawStart, rawEnd);
            }
        } else if (type == String.class) {
            if (!hadQuote && (FastByteParser.isBlank(rawBuf, rawStart, rawEnd) || FastByteParser.isNaOrEmpty(rawBuf, rawStart, rawEnd))) {
                nullBitsets[colIdx].set(row);
            } else if (hadQuote && rawEnd == 0 && !config.isPreserveEmptyStrings()) {
                nullBitsets[colIdx].set(row);
            } else {
                ((String[]) colData[colIdx])[row] = stringPool.getOrCreate(rawBuf, rawStart, rawEnd - rawStart);
            }
        } else {
            // Object / temporal / BigDecimal types
            if (FastByteParser.isBlank(rawBuf, rawStart, rawEnd) || FastByteParser.isNaOrEmpty(rawBuf, rawStart, rawEnd)) {
                nullBitsets[colIdx].set(row);
            } else {
                String strVal = stringPool.getOrCreate(rawBuf, rawStart, rawEnd - rawStart).trim();
                ((Object[]) colData[colIdx])[row] = convertObjectValue(strVal, type);
            }
        }
    }

    private static Object convertObjectValue(String str, Class<?> type) {
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

    private static List<ColumnVector> assembleVectors(String[] colNames, Class<?>[] colTypes,
                                                     List<ChunkResult> chunkResults, int totalRows) {
        int colCount = colNames.length;
        List<ColumnVector> vectors = new ArrayList<>(colCount);

        for (int c = 0; c < colCount; c++) {
            Class<?> type = colTypes[c];
            String name = colNames[c];

            if (type == int.class || type == Integer.class) {
                int[] data = new int[totalRows];
                BitSet nulls = new BitSet(totalRows);
                int offset = 0;
                for (ChunkResult chunk : chunkResults) {
                    int rows = chunk.rowCount;
                    System.arraycopy((int[]) chunk.columnData[c], 0, data, offset, rows);
                    for (int r = 0; r < rows; r++) {
                        if (chunk.nullBitsets[c].get(r)) {
                            nulls.set(offset + r);
                        }
                    }
                    offset += rows;
                }
                vectors.add(new IntColumnVector(name, true, data, nulls, totalRows));
            } else if (type == long.class || type == Long.class) {
                long[] data = new long[totalRows];
                BitSet nulls = new BitSet(totalRows);
                int offset = 0;
                for (ChunkResult chunk : chunkResults) {
                    int rows = chunk.rowCount;
                    System.arraycopy((long[]) chunk.columnData[c], 0, data, offset, rows);
                    for (int r = 0; r < rows; r++) {
                        if (chunk.nullBitsets[c].get(r)) {
                            nulls.set(offset + r);
                        }
                    }
                    offset += rows;
                }
                vectors.add(new LongColumnVector(name, true, data, nulls, totalRows));
            } else if (type == double.class || type == Double.class) {
                double[] data = new double[totalRows];
                BitSet nulls = new BitSet(totalRows);
                int offset = 0;
                for (ChunkResult chunk : chunkResults) {
                    int rows = chunk.rowCount;
                    System.arraycopy((double[]) chunk.columnData[c], 0, data, offset, rows);
                    for (int r = 0; r < rows; r++) {
                        if (chunk.nullBitsets[c].get(r)) {
                            nulls.set(offset + r);
                        }
                    }
                    offset += rows;
                }
                vectors.add(new DoubleColumnVector(name, true, data, nulls, totalRows));
            } else if (type == float.class || type == Float.class) {
                float[] data = new float[totalRows];
                BitSet nulls = new BitSet(totalRows);
                int offset = 0;
                for (ChunkResult chunk : chunkResults) {
                    int rows = chunk.rowCount;
                    System.arraycopy((float[]) chunk.columnData[c], 0, data, offset, rows);
                    for (int r = 0; r < rows; r++) {
                        if (chunk.nullBitsets[c].get(r)) {
                            nulls.set(offset + r);
                        }
                    }
                    offset += rows;
                }
                vectors.add(new FloatColumnVector(name, true, data, nulls, totalRows));
            } else if (type == boolean.class || type == Boolean.class) {
                boolean[] data = new boolean[totalRows];
                BitSet nulls = new BitSet(totalRows);
                int offset = 0;
                for (ChunkResult chunk : chunkResults) {
                    int rows = chunk.rowCount;
                    System.arraycopy((boolean[]) chunk.columnData[c], 0, data, offset, rows);
                    for (int r = 0; r < rows; r++) {
                        if (chunk.nullBitsets[c].get(r)) {
                            nulls.set(offset + r);
                        }
                    }
                    offset += rows;
                }
                vectors.add(new BooleanColumnVector(name, true, data, nulls, totalRows));
            } else if (type == String.class) {
                String[] data = new String[totalRows];
                BitSet nulls = new BitSet(totalRows);
                int offset = 0;
                for (ChunkResult chunk : chunkResults) {
                    int rows = chunk.rowCount;
                    System.arraycopy((String[]) chunk.columnData[c], 0, data, offset, rows);
                    for (int r = 0; r < rows; r++) {
                        if (chunk.nullBitsets[c].get(r)) {
                            nulls.set(offset + r);
                        }
                    }
                    offset += rows;
                }
                vectors.add(new StringColumnVector(name, true, data, nulls, totalRows));
            } else {
                Object[] data = new Object[totalRows];
                BitSet nulls = new BitSet(totalRows);
                int offset = 0;
                for (ChunkResult chunk : chunkResults) {
                    int rows = chunk.rowCount;
                    System.arraycopy((Object[]) chunk.columnData[c], 0, data, offset, rows);
                    for (int r = 0; r < rows; r++) {
                        if (chunk.nullBitsets[c].get(r)) {
                            nulls.set(offset + r);
                        }
                    }
                    offset += rows;
                }
                vectors.add(new ObjectColumnVector(name, type, true, data, nulls, totalRows));
            }
        }

        return vectors;
    }
}
