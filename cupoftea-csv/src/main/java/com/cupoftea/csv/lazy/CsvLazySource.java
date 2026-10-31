package com.cupoftea.csv.lazy;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.cupoftea.csv.fast.FastCsvReader;
import com.cupoftea.csv.fast.SimdCsvScanner;
import com.cupoftea.exceptions.CsvParseException;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.configurations.CSVImporterConfiguration;
import com.cupoftea.lazy.LazySource;
import com.cupoftea.lazy.PartitionReader;
import com.cupoftea.lazy.ScanContext;
import com.cupoftea.table.Column;
import com.cupoftea.table.Schema;

/**
 * High-performance streaming CSV data source.
 * Evaluates CSV files of any size (including out-of-core >14 GB files) across memory-mapped partitions.
 */
public class CsvLazySource implements LazySource {

    private final File file;
    private final CSVImporterConfiguration config;
    private String[] colNames;
    private Class<?>[] colTypes;
    private byte[] colTypeIds;
    private Schema schema;
    private List<Column> headers;
    private long dataStartOffset = 0;
    private boolean initialized = false;

    public CsvLazySource(File file, CSVImporterConfiguration config) {
        if (file == null || !file.exists()) {
            throw new ImportException("CSV file does not exist: " + (file != null ? file.getPath() : "null"));
        }
        this.file = file;
        this.config = config != null ? config : new CSVImporterConfiguration();
        initMetadata();
    }

    private void initMetadata() {
        if (initialized) {
            return;
        }
        initialized = true;

        long fileLength = file.length();
        if (fileLength == 0) {
            this.colNames = new String[0];
            this.colTypes = new Class<?>[0];
            this.colTypeIds = new byte[0];
            this.schema = Schema.empty();
            this.headers = new ArrayList<>();
            return;
        }

        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            int sampleSize = (int) Math.min(65536L, fileLength);
            ByteBuffer buf = ByteBuffer.allocate(sampleSize);
            channel.read(buf, 0);
            buf.flip();
            byte[] sampleBytes = new byte[buf.remaining()];
            buf.get(sampleBytes);

            int start = 0;
            // Check UTF-8 BOM
            if (sampleBytes.length >= 3) {
                if ((sampleBytes[0] & 0xFF) == 0xEF && (sampleBytes[1] & 0xFF) == 0xBB && (sampleBytes[2] & 0xFF) == 0xBF) {
                    start = 3;
                }
            }

            char delimChar = config.getDelimiter();
            byte delim = (byte) delimChar;
            byte quote = (byte) config.getQuoteChar();
            byte escape = (byte) config.getEscapeChar();
            Charset charset = config.getCharset();

            // Find first line break
            int firstLineEnd = -1;
            boolean inQuote = false;
            for (int i = start; i < sampleBytes.length; i++) {
                byte b = sampleBytes[i];
                if (quote != 0 && b == quote) {
                    inQuote = !inQuote;
                } else if (!inQuote && (b == '\n' || b == '\r')) {
                    firstLineEnd = i;
                    break;
                }
            }

            if (firstLineEnd == -1) {
                firstLineEnd = sampleBytes.length;
            }

            // Parse header or first row
            List<String> firstRow = parseRow(sampleBytes, start, firstLineEnd, delim, quote, escape, charset);
            int colCount = Math.max(1, firstRow.size());
            this.colNames = new String[colCount];

            // Resolve types from explicit schema or infer
            Schema explicitSchema = config.getSchema();

            if (config.isHasHeaders()) {
                for (int i = 0; i < colCount; i++) {
                    colNames[i] = i < firstRow.size() ? firstRow.get(i) : String.valueOf(i);
                }
                int next = firstLineEnd;
                if (next < sampleBytes.length && sampleBytes[next] == '\r') next++;
                if (next < sampleBytes.length && sampleBytes[next] == '\n') next++;
                this.dataStartOffset = next;
            } else {
                for (int i = 0; i < colCount; i++) {
                    if (explicitSchema != null && i < explicitSchema.size()) {
                        colNames[i] = explicitSchema.getColumn(i).columnName();
                    } else {
                        colNames[i] = String.valueOf(i);
                    }
                }
                this.dataStartOffset = start;
            }
            this.colTypes = new Class<?>[colCount];

            for (int i = 0; i < colCount; i++) {
                String name = colNames[i];
                if (explicitSchema != null && explicitSchema.hasColumn(name)) {
                    colTypes[i] = explicitSchema.getType(name);
                } else if (explicitSchema != null && i < explicitSchema.size() && !config.isHasHeaders()) {
                    colTypes[i] = explicitSchema.getColumn(i).columnType();
                } else {
                    colTypes[i] = String.class;
                }
            }

            this.colTypeIds = new byte[colCount];
            for (int i = 0; i < colCount; i++) {
                colTypeIds[i] = FastCsvReader.resolveTypeId(colTypes[i]);
            }

            Schema.Builder sb = Schema.builder();
            this.headers = new ArrayList<>(colCount);
            for (int i = 0; i < colCount; i++) {
                sb.add(colNames[i], colTypes[i] != null ? colTypes[i] : String.class);
                headers.add(new Column(colNames[i], colTypes[i] != null ? colTypes[i] : String.class, true));
            }
            this.schema = sb.build();

        } catch (IOException e) {
            throw new CsvParseException("Failed inspecting CSV metadata for: " + file.getPath(), e);
        }
    }

    private static List<String> parseRow(byte[] buf, int start, int end, byte delim, byte quote, byte escape, Charset charset) {
        List<String> fields = new ArrayList<>();
        int curr = start;
        boolean inQuote = false;
        byte[] fieldBuf = new byte[Math.max(32, end - start)];
        int flen = 0;

        while (curr < end) {
            byte b = buf[curr];
            if (quote != 0 && b == quote) {
                inQuote = !inQuote;
                curr++;
                continue;
            }
            if (!inQuote && b == delim) {
                fields.add(new String(fieldBuf, 0, flen, charset).trim());
                flen = 0;
                curr++;
                continue;
            }
            if (flen >= fieldBuf.length) {
                byte[] nb = new byte[fieldBuf.length * 2];
                System.arraycopy(fieldBuf, 0, nb, 0, flen);
                fieldBuf = nb;
            }
            fieldBuf[flen++] = b;
            curr++;
        }
        fields.add(new String(fieldBuf, 0, flen, charset).trim());
        return fields;
    }

    @Override
    public Schema getSchema() {
        return schema;
    }

    @Override
    public List<Column> getHeaders() {
        return headers;
    }

    @Override
    public String describe() {
        return "CsvFile(path=" + file.getName() + ", size=" + file.length() + "B)";
    }

    public static final int DEFAULT_SEGMENT_SIZE = 4 * 1024 * 1024; // 4 MB

    @Override
    public List<PartitionReader> getPartitions(ScanContext context) {
        long fileLength = file.length();
        if (fileLength == 0 || dataStartOffset >= fileLength) {
            return Collections.emptyList();
        }

        if (!FastCsvReader.isEligible(file, config)) {
            try {
                return Collections.singletonList(new CsvOpenCsvPartitionReader(file, config, context.getBatchSize()));
            } catch (IOException e) {
                throw new CsvParseException("Failed creating fallback CSV reader", e);
            }
        }

        FileChannel channel = null;
        try {
            channel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
            int segmentSize = context.getChunkSizeBytes() > 0 ? context.getChunkSizeBytes() : DEFAULT_SEGMENT_SIZE;
            long dataLen = fileLength - dataStartOffset;

            int procs = Runtime.getRuntime().availableProcessors();
            int numPartitions = 1;
            if (context.isParallel() && dataLen > segmentSize) {
                long totalSegments = (dataLen + segmentSize - 1) / segmentSize;
                numPartitions = (int) Math.max(1, Math.min((long) procs, totalSegments));
            }

            AtomicLong cursor = new AtomicLong(dataStartOffset);
            AtomicInteger activeReaders = new AtomicInteger(numPartitions);

            List<PartitionReader> readers = new ArrayList<>(numPartitions);
            for (int i = 0; i < numPartitions; i++) {
                readers.add(new CsvMmapPartitionReader(
                        channel, cursor, fileLength, dataStartOffset, segmentSize, activeReaders,
                        colNames, colTypes, colTypeIds, config
                ));
            }
            return readers;

        } catch (IOException e) {
            if (channel != null && channel.isOpen()) {
                try {
                    channel.close();
                } catch (IOException ignored) {}
            }
            throw new CsvParseException("Failed partitioning CSV file: " + file.getPath(), e);
        }
    }

    public static long findNextLineBreak(FileChannel channel, long startPos, long maxPos) throws IOException {
        long pos = startPos;
        ByteBuffer buf = ByteBuffer.allocate(8192);

        while (pos < maxPos) {
            buf.clear();
            int toRead = (int) Math.min((long) buf.capacity(), maxPos - pos);
            buf.limit(toRead);
            int read = channel.read(buf, pos);
            if (read <= 0) break;
            buf.flip();

            for (int i = 0; i < read; i++) {
                byte b = buf.get(i);
                if (b == '\n') {
                    return pos + i + 1;
                } else if (b == '\r') {
                    if (i + 1 < read) {
                        if (buf.get(i + 1) == '\n') {
                            return pos + i + 2;
                        }
                        return pos + i + 1;
                    } else if (pos + i + 1 < maxPos) {
                        ByteBuffer nextBuf = ByteBuffer.allocate(1);
                        if (channel.read(nextBuf, pos + i + 1) > 0 && nextBuf.get(0) == '\n') {
                            return pos + i + 2;
                        }
                        return pos + i + 1;
                    }
                    return pos + i + 1;
                }
            }
            pos += read;
        }
        return maxPos;
    }
}
