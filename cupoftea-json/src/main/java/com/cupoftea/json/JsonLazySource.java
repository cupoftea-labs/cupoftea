package com.cupoftea.json;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.configurations.JsonImporterConfiguration;
import com.cupoftea.lazy.LazySource;
import com.cupoftea.lazy.PartitionReader;
import com.cupoftea.lazy.ScanContext;
import com.cupoftea.spi.ScanOptions;
import com.cupoftea.table.Column;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.Schema;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * High-performance streaming and lazy JSON &amp; NDJSON / JSON Lines data source.
 * Supports:
 * - Chunked / parallel scans for NDJSON / JSON Lines files across CPU cores.
 * - Projection pushdown (select columns) skipping unselected JSON fields.
 * - Direct Jackson token-level ingestion to {@link ColumnVector} with zero intermediate DOM nodes.
 */
public class JsonLazySource implements LazySource {

    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    public static final int DEFAULT_SEGMENT_SIZE = 4 * 1024 * 1024; // 4 MB chunk
    public static final int MIN_SEGMENT_SIZE = 256 * 1024; // 256 KB

    private final File file;
    private final JsonImporterConfiguration config;
    private final ScanOptions scanOptions;

    private Schema schema;
    private List<Column> headers;
    private boolean isLinesFormat;
    private long dataStartOffset = 0;
    private boolean initialized = false;

    public JsonLazySource(File file, JsonImporterConfiguration config, ScanOptions scanOptions) {
        if (file == null || !file.exists()) {
            throw new ImportException("JSON file does not exist: " + (file != null ? file.getPath() : "null"));
        }
        this.file = file;
        this.config = config != null ? config : new JsonImporterConfiguration();
        this.scanOptions = scanOptions;
        initMetadata();
    }

    private void initMetadata() {
        if (initialized) return;
        initialized = true;

        long fileLength = file.length();
        if (fileLength == 0) {
            this.schema = Schema.empty();
            this.headers = new ArrayList<>();
            this.isLinesFormat = false;
            return;
        }

        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            int sampleSize = (int) Math.min(65536L, fileLength);
            ByteBuffer buf = ByteBuffer.allocate(sampleSize);
            channel.read(buf, 0);
            buf.flip();
            byte[] sample = new byte[buf.remaining()];
            buf.get(sample);

            int start = 0;
            // Skip UTF-8 BOM
            if (sample.length >= 3 && (sample[0] & 0xFF) == 0xEF && (sample[1] & 0xFF) == 0xBB && (sample[2] & 0xFF) == 0xBF) {
                start = 3;
            }
            // Skip leading whitespace
            while (start < sample.length && Character.isWhitespace(sample[start])) {
                start++;
            }
            this.dataStartOffset = start;

            if (start >= sample.length) {
                this.schema = Schema.empty();
                this.headers = new ArrayList<>();
                this.isLinesFormat = false;
                return;
            }

            byte firstChar = sample[start];
            if (firstChar == '[') {
                this.isLinesFormat = false;
            } else if (firstChar == '{') {
                // Could be NDJSON/JSON Lines or single JSON object
                if (config.isLines()) {
                    this.isLinesFormat = true;
                } else {
                    // Check if there is a newline followed by another '{'
                    boolean hasNextLine = false;
                    for (int i = start + 1; i < sample.length; i++) {
                        if (sample[i] == '\n' || sample[i] == '\r') {
                            int j = i + 1;
                            while (j < sample.length && Character.isWhitespace(sample[j])) {
                                j++;
                            }
                            if (j < sample.length && sample[j] == '{') {
                                hasNextLine = true;
                            }
                            break;
                        }
                    }
                    this.isLinesFormat = hasNextLine || file.getName().endsWith(".jsonl") || file.getName().endsWith(".ndjson");
                }
            } else {
                this.isLinesFormat = config.isLines();
            }

            // Sample schema discovery
            Schema explicitSchema = config.getSchema();
            if (explicitSchema == null && scanOptions != null && scanOptions.getSchema() != null) {
                explicitSchema = scanOptions.getSchema();
            }

            if (explicitSchema != null && explicitSchema.size() > 0) {
                this.schema = explicitSchema;
                this.headers = new ArrayList<>(explicitSchema.getColumns());
            } else {
                inferSchemaFromSample(sample, start);
            }

        } catch (IOException e) {
            throw new ImportException("Failed inspecting JSON metadata for: " + file.getPath(), e);
        }
    }

    private void inferSchemaFromSample(byte[] sample, int start) {
        Map<String, Class<?>> discovered = new LinkedHashMap<>();
        try (JsonParser parser = JSON_FACTORY.createParser(new ByteArrayInputStream(sample, start, sample.length - start))) {
            JsonToken token = parser.nextToken();
            boolean isArr = (token == JsonToken.START_ARRAY);
            int sampleRows = 0;

            while (sampleRows < 100) {
                if (isArr) {
                    token = parser.nextToken();
                    if (token == null || token == JsonToken.END_ARRAY) break;
                } else {
                    if (sampleRows > 0) {
                        token = parser.nextToken();
                        if (token == null) break;
                    }
                }

                if (token != JsonToken.START_OBJECT) {
                    if (token != null && token.isScalarValue()) continue;
                    if (token == JsonToken.START_ARRAY) { parser.skipChildren(); continue; }
                    break;
                }

                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = parser.currentName();
                    JsonToken valToken = parser.nextToken();
                    Class<?> type = inferType(valToken, parser);
                    if (!discovered.containsKey(fieldName)) {
                        discovered.put(fieldName, type);
                    } else if (type != null) {
                        discovered.put(fieldName, mergeTypes(discovered.get(fieldName), type));
                    }
                }
                sampleRows++;
            }
        } catch (Exception ignored) {
        }

        Schema.Builder sb = Schema.builder();
        this.headers = new ArrayList<>(discovered.size());
        for (Map.Entry<String, Class<?>> entry : discovered.entrySet()) {
            Class<?> t = entry.getValue() != null ? entry.getValue() : String.class;
            sb.add(entry.getKey(), t);
            headers.add(new Column(entry.getKey(), t, true));
        }
        this.schema = sb.build();
    }

    private static Class<?> inferType(JsonToken token, JsonParser parser) {
        if (token == null || token == JsonToken.VALUE_NULL) return null;
        if (token == JsonToken.VALUE_TRUE || token == JsonToken.VALUE_FALSE) return Boolean.class;
        if (token == JsonToken.VALUE_NUMBER_INT) {
            try { parser.getIntValue(); return Integer.class; }
            catch (Exception e) {
                try { parser.getLongValue(); return Long.class; }
                catch (Exception e2) { return Double.class; }
            }
        }
        if (token == JsonToken.VALUE_NUMBER_FLOAT) return Double.class;
        return String.class;
    }

    private static Class<?> mergeTypes(Class<?> c1, Class<?> c2) {
        if (c1 == null) return c2;
        if (c2 == null || c1.equals(c2)) return c1;
        if (Number.class.isAssignableFrom(c1) && Number.class.isAssignableFrom(c2)) {
            if (c1 == Double.class || c2 == Double.class) return Double.class;
            if (c1 == Long.class || c2 == Long.class) return Long.class;
            return Integer.class;
        }
        return String.class;
    }

    @Override
    public Schema getSchema() {
        return schema;
    }

    @Override
    public List<Column> getHeaders() {
        return headers;
    }

    public boolean isLinesFormat() {
        return isLinesFormat;
    }

    @Override
    public String describe() {
        return "JsonFile(path=" + file.getName() + ", size=" + file.length() + "B, lines=" + isLinesFormat + ")";
    }

    @Override
    public List<PartitionReader> getPartitions(ScanContext context) {
        long fileLength = file.length();
        if (fileLength == 0 || dataStartOffset >= fileLength) {
            return Collections.emptyList();
        }

        int batchSize = context != null && context.getBatchSize() > 0 ? context.getBatchSize() : config.getBatchSize();
        Set<String> projected = null;
        if (context != null && context.getProjectedColumns() != null && !context.getProjectedColumns().isEmpty()) {
            projected = new HashSet<>(context.getProjectedColumns());
        }

        // Standard JSON Array format must be streamed sequentially
        if (!isLinesFormat) {
            return Collections.singletonList(new JsonArrayPartitionReader(file, schema, headers, projected, batchSize));
        }

        // NDJSON / JSON Lines format: parallel chunked scanning
        try {
            FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
            long dataLen = fileLength - dataStartOffset;
            int procs = Runtime.getRuntime().availableProcessors();

            int segmentSize;
            if (context != null && context.getChunkSizeBytes() > 0) {
                segmentSize = context.getChunkSizeBytes();
            } else if (procs > 1 && dataLen > MIN_SEGMENT_SIZE) {
                segmentSize = (int) Math.max(MIN_SEGMENT_SIZE, Math.min(DEFAULT_SEGMENT_SIZE, dataLen / procs));
            } else {
                segmentSize = DEFAULT_SEGMENT_SIZE;
            }

            int numPartitions = 1;
            if (context != null && context.isParallel() && dataLen > segmentSize) {
                long totalSegments = (dataLen + segmentSize - 1) / segmentSize;
                numPartitions = (int) Math.max(1, Math.min((long) procs, totalSegments));
            }

            AtomicLong cursor = new AtomicLong(dataStartOffset);
            AtomicInteger activeReaders = new AtomicInteger(numPartitions);

            List<PartitionReader> readers = new ArrayList<>(numPartitions);
            for (int i = 0; i < numPartitions; i++) {
                readers.add(new JsonLinesPartitionReader(
                        channel, cursor, fileLength, dataStartOffset, segmentSize, activeReaders,
                        schema, headers, projected, batchSize
                ));
            }
            return readers;
        } catch (IOException e) {
            throw new ImportException("Failed creating JSON Lines partition readers for: " + file.getPath(), e);
        }
    }

    public static long findNextLineBreak(FileChannel channel, long startPos, long maxPos) throws IOException {
        long pos = startPos;
        ByteBuffer buf = ByteBuffer.allocate(8192);

        while (pos < maxPos) {
            buf.clear();
            long toRead = Math.min((long) buf.capacity(), maxPos - pos);
            buf.limit((int) toRead);
            int read = channel.read(buf, pos);
            if (read <= 0) break;

            buf.flip();
            for (int i = 0; i < read; i++) {
                byte b = buf.get(i);
                if (b == '\n') {
                    return pos + i + 1;
                }
            }
            pos += read;
        }
        return maxPos;
    }

    // --- NDJSON / JSON Lines Chunked Partition Reader ---

    public static class JsonLinesPartitionReader implements PartitionReader {

        private final FileChannel channel;
        private final AtomicLong cursor;
        private final long fileLength;
        private final long dataStartOffset;
        private final int segmentSize;
        private final AtomicInteger activeReaders;
        private final Schema schema;
        private final List<Column> headers;
        private final Set<String> projected;
        private final int batchSize;

        private boolean closed = false;

        public JsonLinesPartitionReader(FileChannel channel, AtomicLong cursor, long fileLength,
                                        long dataStartOffset, int segmentSize, AtomicInteger activeReaders,
                                        Schema schema, List<Column> headers, Set<String> projected, int batchSize) {
            this.channel = channel;
            this.cursor = cursor;
            this.fileLength = fileLength;
            this.dataStartOffset = dataStartOffset;
            this.segmentSize = segmentSize;
            this.activeReaders = activeReaders;
            this.schema = schema;
            this.headers = headers;
            this.projected = projected;
            this.batchSize = batchSize > 0 ? batchSize : 65_536;
        }

        @Override
        public CupOfTea nextBatch() throws IOException {
            if (closed) return null;

            while (true) {
                long current = cursor.addAndGet(segmentSize) - segmentSize;
                if (current >= fileLength) {
                    return null;
                }

                long start = (current == dataStartOffset) ? dataStartOffset : findNextLineBreak(channel, current, fileLength);
                long end = (current + segmentSize >= fileLength) ? fileLength : findNextLineBreak(channel, current + segmentSize, fileLength);

                if (start >= end) {
                    continue;
                }

                // Read chunk bytes
                int chunkLen = (int) (end - start);
                byte[] chunkBytes = new byte[chunkLen];
                ByteBuffer bb = ByteBuffer.wrap(chunkBytes);
                channel.read(bb, start);

                // Parse chunk via low-level Jackson streaming parser
                try (JsonParser parser = JSON_FACTORY.createParser(new ByteArrayInputStream(chunkBytes))) {
                    return parseJsonStreamToDataFrame(parser, schema, headers, projected, -1);
                } catch (Exception e) {
                    throw new IOException("Failed parsing JSON Lines chunk at offset " + start + "-" + end, e);
                }
            }
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                if (activeReaders != null && activeReaders.decrementAndGet() == 0) {
                    if (channel != null && channel.isOpen()) {
                        channel.close();
                    }
                }
            }
        }
    }

    // --- Standard JSON Array Streaming Partition Reader ---

    public static class JsonArrayPartitionReader implements PartitionReader {

        private final File file;
        private final Schema schema;
        private final List<Column> headers;
        private final Set<String> projected;
        private final int batchSize;

        private JsonParser parser;
        private InputStream in;
        private boolean isArray = false;
        private boolean finished = false;

        public JsonArrayPartitionReader(File file, Schema schema, List<Column> headers,
                                        Set<String> projected, int batchSize) {
            this.file = file;
            this.schema = schema;
            this.headers = headers;
            this.projected = projected;
            this.batchSize = batchSize > 0 ? batchSize : 65_536;
        }

        private void initParser() throws IOException {
            if (parser == null) {
                in = new BufferedInputStream(new FileInputStream(file), 128 * 1024);
                parser = JSON_FACTORY.createParser(in);
                JsonToken token = parser.nextToken();
                if (token == null) {
                    finished = true;
                    return;
                }
                if (token == JsonToken.START_ARRAY) {
                    isArray = true;
                } else if (token != JsonToken.START_OBJECT) {
                    finished = true;
                }
            }
        }

        @Override
        public CupOfTea nextBatch() throws IOException {
            if (finished) return null;
            initParser();
            if (finished) return null;

            try {
                CupOfTea batch = parseNextBatchFromArray(parser, schema, headers, projected, batchSize, isArray);
                if (batch == null || batch.size() == 0) {
                    finished = true;
                    close();
                    return null;
                }
                return batch;
            } catch (Exception e) {
                close();
                throw new IOException("Failed parsing JSON array batch from: " + file.getPath(), e);
            }
        }

        @Override
        public void close() throws IOException {
            if (parser != null) {
                try { parser.close(); } catch (Exception ignored) {}
                parser = null;
            }
            if (in != null) {
                try { in.close(); } catch (Exception ignored) {}
                in = null;
            }
            finished = true;
        }
    }

    // --- Common Vector Ingestion & Projection Helper ---

    public static CupOfTea parseJsonStreamToDataFrame(JsonParser parser, Schema schema, List<Column> headers,
                                                      Set<String> projected, int maxRows) throws Exception {
        return parseNextBatchFromArray(parser, schema, headers, projected, maxRows, false);
    }

    public static CupOfTea parseNextBatchFromArray(JsonParser parser, Schema schema, List<Column> headers,
                                                   Set<String> projected, int maxRows, boolean isArray) throws Exception {
        Map<String, Class<?>> schemaMap = new LinkedHashMap<>();
        if (headers != null) {
            for (Column col : headers) {
                if (projected == null || projected.contains(col.columnName())) {
                    schemaMap.put(col.columnName(), col.columnType());
                }
            }
        }
        if (schema != null) {
            for (Column col : schema.getColumns()) {
                if (projected == null || projected.contains(col.columnName())) {
                    schemaMap.put(col.columnName(), col.columnType());
                }
            }
        }

        List<String> colNames = new ArrayList<>(schemaMap.keySet());
        Map<String, Integer> colIndices = new LinkedHashMap<>();
        for (int i = 0; i < colNames.size(); i++) {
            colIndices.put(colNames.get(i), i);
        }

        List<ColumnVector> vectors = new ArrayList<>();
        for (String name : colNames) {
            vectors.add(ColumnVector.create(name, schemaMap.get(name), Math.max(32, maxRows > 0 ? maxRows : 64), true));
        }

        int rowCount = 0;
        int limit = maxRows > 0 ? maxRows : Integer.MAX_VALUE;

        while (rowCount < limit) {
            JsonToken token;
            if (isArray) {
                token = parser.nextToken();
                if (token == null || token == JsonToken.END_ARRAY) {
                    break;
                }
            } else {
                token = parser.nextToken();
                if (token == null) {
                    break;
                }
            }

            if (token != JsonToken.START_OBJECT) {
                if (token != null && token.isScalarValue()) continue;
                if (token == JsonToken.START_ARRAY) { parser.skipChildren(); continue; }
                break;
            }

            boolean[] received = new boolean[Math.max(64, vectors.size() * 2)];

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                JsonToken valToken = parser.nextToken();

                // Projection filter pushdown: skip field if not projected
                if (projected != null && !projected.contains(fieldName)) {
                    if (valToken == JsonToken.START_OBJECT || valToken == JsonToken.START_ARRAY) {
                        parser.skipChildren();
                    }
                    continue;
                }

                Integer colIdx = colIndices.get(fieldName);
                if (colIdx == null) {
                    colIdx = colNames.size();
                    colNames.add(fieldName);
                    colIndices.put(fieldName, colIdx);

                    Class<?> inferred = inferType(valToken, parser);
                    ColumnVector newVec = ColumnVector.create(fieldName, inferred, Math.max(32, rowCount + 16), true);
                    for (int r = 0; r < rowCount; r++) {
                        newVec.appendNull();
                    }
                    vectors.add(newVec);

                    if (colIdx >= received.length) {
                        boolean[] nr = new boolean[Math.max(colIdx * 2, received.length * 2)];
                        System.arraycopy(received, 0, nr, 0, received.length);
                        received = nr;
                    }
                } else if (colIdx >= received.length) {
                    boolean[] nr = new boolean[Math.max(colIdx * 2, received.length * 2)];
                    System.arraycopy(received, 0, nr, 0, received.length);
                    received = nr;
                }

                received[colIdx] = true;
                ColumnVector vec = vectors.get(colIdx);

                // Promote vector type dynamically if needed
                ColumnVector promoted = checkAndPromote(vec, valToken, parser, rowCount);
                if (promoted != vec) {
                    vectors.set(colIdx, promoted);
                    vec = promoted;
                }

                appendToken(vec, valToken, parser);
            }

            // Fill nulls for unreceived columns
            for (int c = 0; c < vectors.size(); c++) {
                if (!received[c]) {
                    vectors.get(c).appendNull();
                }
            }

            rowCount++;
        }

        if (rowCount == 0) {
            return null;
        }

        ArrayList<Column> finalHeaders = new ArrayList<>(vectors.size());
        for (ColumnVector vec : vectors) {
            finalHeaders.add(new Column(vec.getName(), vec.getType(), true));
        }

        DataFrame df = DataFrame.fromVectors(vectors);
        df.setHeaders(finalHeaders);
        return new CupOfTea(df);
    }

    private static ColumnVector checkAndPromote(ColumnVector vec, JsonToken token, JsonParser parser, int currentRows) throws Exception {
        if (token == null || token == JsonToken.VALUE_NULL) return vec;
        Class<?> currentType = vec.getType();
        if (currentType == null) {
            Class<?> inf = inferType(token, parser);
            if (inf != null) {
                return promote(vec, inf, currentRows);
            }
            return vec;
        }
        if (token == JsonToken.VALUE_NUMBER_INT) {
            if (currentType == Integer.class) {
                try { parser.getIntValue(); }
                catch (Exception e) {
                    try { parser.getLongValue(); return promote(vec, Long.class, currentRows); }
                    catch (Exception e2) { return promote(vec, Double.class, currentRows); }
                }
            } else if (currentType != Long.class && currentType != Double.class && currentType != Float.class) {
                return promote(vec, String.class, currentRows);
            }
        } else if (token == JsonToken.VALUE_NUMBER_FLOAT) {
            if (currentType == Integer.class || currentType == Long.class || currentType == Float.class) {
                return promote(vec, Double.class, currentRows);
            } else if (currentType != Double.class) {
                return promote(vec, String.class, currentRows);
            }
        } else if (token == JsonToken.VALUE_STRING) {
            if (currentType != String.class) {
                return promote(vec, String.class, currentRows);
            }
        } else if (token == JsonToken.VALUE_TRUE || token == JsonToken.VALUE_FALSE) {
            if (currentType != Boolean.class) {
                return promote(vec, String.class, currentRows);
            }
        }
        return vec;
    }

    private static ColumnVector promote(ColumnVector oldVec, Class<?> targetType, int currentRows) {
        ColumnVector newVec = ColumnVector.create(oldVec.getName(), targetType, Math.max(32, currentRows + 16), true);
        for (int r = 0; r < currentRows; r++) {
            if (oldVec.isNull(r)) {
                newVec.appendNull();
            } else {
                Object val = oldVec.getObject(r);
                if (newVec instanceof DoubleColumnVector dcv) {
                    dcv.appendDouble(((Number) val).doubleValue());
                } else if (newVec instanceof LongColumnVector lcv) {
                    lcv.appendLong(((Number) val).longValue());
                } else if (newVec instanceof IntColumnVector icv) {
                    icv.appendInt(((Number) val).intValue());
                } else if (newVec instanceof StringColumnVector scv) {
                    scv.appendString(val.toString());
                } else {
                    newVec.appendObject(val);
                }
            }
        }
        return newVec;
    }

    private static void appendToken(ColumnVector vec, JsonToken token, JsonParser parser) throws Exception {
        if (token == null || token == JsonToken.VALUE_NULL) {
            vec.appendNull();
            return;
        }

        if (vec instanceof IntColumnVector icv) {
            if (token == JsonToken.VALUE_NUMBER_INT) {
                icv.appendInt(parser.getIntValue());
            } else if (token == JsonToken.VALUE_NUMBER_FLOAT) {
                icv.appendInt((int) parser.getDoubleValue());
            } else {
                icv.appendInt(Integer.parseInt(parser.getText().trim()));
            }
        } else if (vec instanceof LongColumnVector lcv) {
            if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
                lcv.appendLong(parser.getLongValue());
            } else {
                lcv.appendLong(Long.parseLong(parser.getText().trim()));
            }
        } else if (vec instanceof DoubleColumnVector dcv) {
            if (token == JsonToken.VALUE_NUMBER_FLOAT || token == JsonToken.VALUE_NUMBER_INT) {
                dcv.appendDouble(parser.getDoubleValue());
            } else {
                dcv.appendDouble(Double.parseDouble(parser.getText().trim()));
            }
        } else if (vec instanceof FloatColumnVector fcv) {
            if (token == JsonToken.VALUE_NUMBER_FLOAT || token == JsonToken.VALUE_NUMBER_INT) {
                fcv.appendFloat(parser.getFloatValue());
            } else {
                fcv.appendFloat(Float.parseFloat(parser.getText().trim()));
            }
        } else if (vec instanceof BooleanColumnVector bcv) {
            if (token == JsonToken.VALUE_TRUE) {
                bcv.appendBoolean(true);
            } else if (token == JsonToken.VALUE_FALSE) {
                bcv.appendBoolean(false);
            } else {
                bcv.appendBoolean(Boolean.parseBoolean(parser.getText().trim()));
            }
        } else if (vec instanceof StringColumnVector scv) {
            if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                scv.appendString(om.readTree(parser).toString());
            } else {
                scv.appendString(parser.getText());
            }
        } else {
            Class<?> type = vec.getType();
            if (type == BigDecimal.class) {
                vec.appendObject(new BigDecimal(parser.getText().trim()));
            } else if (type == LocalDate.class) {
                vec.appendObject(LocalDate.parse(parser.getText().trim()));
            } else if (type == LocalDateTime.class) {
                vec.appendObject(LocalDateTime.parse(parser.getText().trim()));
            } else if (type == Instant.class) {
                vec.appendObject(Instant.parse(parser.getText().trim()));
            } else {
                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                    vec.appendObject(om.readTree(parser).toString());
                } else {
                    vec.appendObject(parser.getText());
                }
            }
        }
    }
}
