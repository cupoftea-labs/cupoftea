package com.cupoftea.parquet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.parquet.bytes.DirectByteBufferAllocator;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.ColumnReader;
import org.apache.parquet.column.Dictionary;
import org.apache.parquet.column.impl.ColumnReaderImpl;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.column.statistics.Statistics;
import org.apache.parquet.ParquetReadOptions;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.metadata.BlockMetaData;
import org.apache.parquet.hadoop.metadata.ColumnChunkMetaData;
import org.apache.parquet.io.api.PrimitiveConverter;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.Type;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.ParquetImporter;
import com.cupoftea.lazy.LazySource;
import com.cupoftea.lazy.PartitionReader;
import com.cupoftea.lazy.ScanContext;
import com.cupoftea.spi.ScanOptions;
import com.cupoftea.table.Column;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.Row;
import com.cupoftea.table.Schema;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DictionaryColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;
import com.cupoftea.utils.interfaces.IFilter;

/**
 * High-performance streaming and lazy Parquet data source with column projection pushdown,
 * zero-copy off-heap byte buffer decompression, and lazy predicate pruning.
 */
public class ParquetLazySource implements LazySource {

    private static class CapturingConverter extends PrimitiveConverter {
        private Dictionary dictionary;

        @Override
        public boolean hasDictionarySupport() {
            return true;
        }

        @Override
        public void setDictionary(Dictionary dictionary) {
            this.dictionary = dictionary;
        }

        public Dictionary getDictionary() {
            return dictionary;
        }
    }

    private final File file;
    private final ScanOptions scanOptions;
    private MessageType fileSchema;
    private MessageType projectedSchema;
    private Schema schema;
    private List<Column> headers;
    private long totalRows;
    private List<String> projectedColumnNames;

    public ParquetLazySource(File file, ScanOptions scanOptions) {
        if (file == null || !file.exists()) {
            throw new ImportException("Parquet file does not exist: " + (file != null ? file.getPath() : "null"));
        }
        this.file = file;
        this.scanOptions = scanOptions;
        initMetadata();
    }

    private void initMetadata() {
        NioChannelInputFile inputFile = new NioChannelInputFile(file);
        ParquetReadOptions options = ParquetReadOptions.builder()
                .withAllocator(DirectByteBufferAllocator.getInstance())
                .useOffHeapDecryptBuffer(true)
                .build();
        try (ParquetFileReader reader = ParquetFileReader.open(inputFile, options)) {
            this.fileSchema = reader.getFileMetaData().getSchema();
            this.totalRows = reader.getRecordCount();

            List<String> projected = null;
            if (scanOptions != null) {
                Object opt = scanOptions.getOption("projectedColumns");
                if (opt instanceof List<?> list) {
                    projected = new ArrayList<>();
                    for (Object item : list) {
                        if (item != null) projected.add(item.toString());
                    }
                }
            }

            if (projected != null && !projected.isEmpty()) {
                List<Type> fields = new ArrayList<>();
                List<String> validProjectedNames = new ArrayList<>();
                for (String name : projected) {
                    if (fileSchema.containsField(name)) {
                        fields.add(fileSchema.getType(name));
                        validProjectedNames.add(name);
                    }
                }
                if (!fields.isEmpty()) {
                    this.projectedSchema = new MessageType(fileSchema.getName(), fields);
                    this.projectedColumnNames = validProjectedNames;
                } else {
                    this.projectedSchema = fileSchema;
                    this.projectedColumnNames = null;
                }
            } else {
                this.projectedSchema = fileSchema;
                this.projectedColumnNames = null;
            }

            List<Column> headerList = new ArrayList<>();
            for (ColumnDescriptor cd : projectedSchema.getColumns()) {
                String colName = cd.getPath()[0];
                Class<?> colClass = ParquetImporter.resolveColumnClass(cd);
                headerList.add(new Column(colName, colClass));
            }
            this.headers = Collections.unmodifiableList(headerList);
            this.schema = new Schema(headerList);
        } catch (IOException e) {
            throw new ImportException("Failed to read Parquet metadata: " + file.getPath(), e);
        }
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
    public long estimatedSize() {
        return totalRows;
    }

    @Override
    public List<PartitionReader> getPartitions(ScanContext context) {
        int batchSize = (context != null && context.getBatchSize() > 0) ? context.getBatchSize() : 65_536;
        MessageType effectiveSchema = projectedSchema;

        if (context != null && context.getProjectedColumns() != null && !context.getProjectedColumns().isEmpty()) {
            List<String> projCols = context.getProjectedColumns();
            List<Type> fields = new ArrayList<>();
            for (String name : projCols) {
                if (fileSchema.containsField(name)) {
                    fields.add(fileSchema.getType(name));
                }
            }
            if (!fields.isEmpty()) {
                effectiveSchema = new MessageType(fileSchema.getName(), fields);
            }
        }

        List<IFilter> filters = context != null ? context.getFilters() : null;
        return Collections.singletonList(new ParquetPartitionReader(file, effectiveSchema, batchSize, filters));
    }

    private static class ParquetPartitionReader implements PartitionReader {
        private final File file;
        private final MessageType schema;
        private final int batchSize;
        private final List<IFilter> filters;
        private ParquetFileReader reader;
        private PageReadStore currentRowGroup;
        private ColumnReader[] currentColumnReaders;
        private CapturingConverter[] currentConverters;
        private int currentRowGroupRemaining = 0;
        private int currentRowGroupIndex = 0;
        private boolean closed = false;

        public ParquetPartitionReader(File file, MessageType schema, int batchSize, List<IFilter> filters) {
            this.file = file;
            this.schema = schema;
            this.batchSize = batchSize;
            this.filters = filters != null && !filters.isEmpty() ? filters : null;
        }

        @Override
        public boolean hasAppliedFilters() {
            return filters != null && !filters.isEmpty();
        }

        private void ensureOpen() throws IOException {
            if (reader == null && !closed) {
                NioChannelInputFile inputFile = new NioChannelInputFile(file);
                ParquetReadOptions options = ParquetReadOptions.builder()
                        .withAllocator(DirectByteBufferAllocator.getInstance())
                        .useOffHeapDecryptBuffer(true)
                        .useStatsFilter(true)
                        .build();
                this.reader = ParquetFileReader.open(inputFile, options);
                this.reader.setRequestedSchema(schema);
            }
        }

        private boolean canPruneRowGroup(BlockMetaData block) {
            if (filters == null || filters.isEmpty() || block == null) {
                return false;
            }
            List<ColumnChunkMetaData> chunks = block.getColumns();
            int numChunks = chunks.size();
            Map<String, Integer> colMap = new LinkedHashMap<>(numChunks * 2);
            List<String> colOrder = new ArrayList<>(numChunks);
            Object[] minVals = new Object[numChunks];
            Object[] maxVals = new Object[numChunks];
            boolean anyStat = false;

            for (int i = 0; i < numChunks; i++) {
                ColumnChunkMetaData cc = chunks.get(i);
                String colName = cc.getPath().toDotString();
                colMap.put(colName, i);
                colOrder.add(colName);
                Statistics<?> stats = cc.getStatistics();
                if (stats != null && stats.hasNonNullValue()) {
                    anyStat = true;
                    switch (cc.getType()) {
                        case INT32, INT64, FLOAT, DOUBLE, BOOLEAN -> {
                            minVals[i] = stats.genericGetMin();
                            maxVals[i] = stats.genericGetMax();
                        }
                        case BINARY -> {
                            minVals[i] = stats.genericGetMin() != null ? ((org.apache.parquet.io.api.Binary) stats.genericGetMin()).toStringUsingUTF8() : null;
                            maxVals[i] = stats.genericGetMax() != null ? ((org.apache.parquet.io.api.Binary) stats.genericGetMax()).toStringUsingUTF8() : null;
                        }
                    }
                }
            }
            if (!anyStat) {
                return false;
            }

            ColumnVector[] minVecs = new ColumnVector[numChunks];
            ColumnVector[] maxVecs = new ColumnVector[numChunks];
            for (int i = 0; i < numChunks; i++) {
                final int idx = i;
                minVecs[i] = new com.cupoftea.table.vector.ObjectColumnVector(colOrder.get(i), Object.class, 1, true) {
                    @Override public double getDouble(int r) { return minVals[idx] != null ? ((Number) minVals[idx]).doubleValue() : 0.0; }
                    @Override public int getInt(int r) { return minVals[idx] != null ? ((Number) minVals[idx]).intValue() : 0; }
                    @Override public long getLong(int r) { return minVals[idx] != null ? ((Number) minVals[idx]).longValue() : 0L; }
                    @Override public float getFloat(int r) { return minVals[idx] != null ? ((Number) minVals[idx]).floatValue() : 0.0f; }
                    @Override public boolean getBoolean(int r) { return minVals[idx] != null ? (Boolean) minVals[idx] : false; }
                    @Override public String getString(int r) { return minVals[idx] != null ? (String) minVals[idx] : ""; }
                    @Override public Object getObject(int r) { return minVals[idx]; }
                };
                maxVecs[i] = new com.cupoftea.table.vector.ObjectColumnVector(colOrder.get(i), Object.class, 1, true) {
                    @Override public double getDouble(int r) { return maxVals[idx] != null ? ((Number) maxVals[idx]).doubleValue() : 0.0; }
                    @Override public int getInt(int r) { return maxVals[idx] != null ? ((Number) maxVals[idx]).intValue() : 0; }
                    @Override public long getLong(int r) { return maxVals[idx] != null ? ((Number) maxVals[idx]).longValue() : 0L; }
                    @Override public float getFloat(int r) { return maxVals[idx] != null ? ((Number) maxVals[idx]).floatValue() : 0.0f; }
                    @Override public boolean getBoolean(int r) { return maxVals[idx] != null ? (Boolean) maxVals[idx] : false; }
                    @Override public String getString(int r) { return maxVals[idx] != null ? (String) maxVals[idx] : ""; }
                    @Override public Object getObject(int r) { return maxVals[idx]; }
                };
            }
            Row minRow = new Row(minVecs, colMap, colOrder, 0);
            Row maxRow = new Row(maxVecs, colMap, colOrder, 0);

            for (IFilter filter : filters) {
                try {
                    Boolean minP = filter.filter(minRow);
                    Boolean maxP = filter.filter(maxRow);
                    if (Boolean.FALSE.equals(minP) && Boolean.FALSE.equals(maxP)) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }
            return false;
        }

        @Override
        public CupOfTea nextBatch() throws IOException {
            if (closed) {
                return null;
            }
            ensureOpen();

            while (true) {
                while (currentRowGroupRemaining <= 0) {
                    List<BlockMetaData> blocks = reader.getRowGroups();
                    if (currentRowGroupIndex < blocks.size()) {
                        BlockMetaData block = blocks.get(currentRowGroupIndex);
                        if (canPruneRowGroup(block)) {
                            reader.skipNextRowGroup();
                            currentRowGroupIndex++;
                            continue;
                        }
                    }

                    currentRowGroup = reader.readNextRowGroup();
                    currentRowGroupIndex++;
                    if (currentRowGroup == null) {
                        close();
                        return null;
                    }
                    long count = currentRowGroup.getRowCount();
                    if (count == 0) {
                        continue;
                    }
                    currentRowGroupRemaining = (int) count;
                    List<ColumnDescriptor> cols = schema.getColumns();
                    currentColumnReaders = new ColumnReader[cols.size()];
                    currentConverters = new CapturingConverter[cols.size()];
                    for (int c = 0; c < cols.size(); c++) {
                        ColumnDescriptor cd = cols.get(c);
                        currentConverters[c] = new CapturingConverter();
                        currentColumnReaders[c] = new ColumnReaderImpl(cd, currentRowGroup.getPageReader(cd), currentConverters[c], null);
                    }
                }

                int rowsToRead = Math.min(batchSize, currentRowGroupRemaining);
                currentRowGroupRemaining -= rowsToRead;

                List<ColumnDescriptor> descriptors = schema.getColumns();
                int numCols = descriptors.size();
                Map<String, Integer> colIndexMap = new LinkedHashMap<>(numCols * 2);
                List<String> colOrder = new ArrayList<>(numCols);
                for (int c = 0; c < numCols; c++) {
                    String colName = descriptors.get(c).getPath()[0];
                    colIndexMap.put(colName, c);
                    colOrder.add(colName);
                }

                ColumnVector[] vectors = new ColumnVector[numCols];

                if (filters == null || filters.isEmpty()) {
                    for (int colIdx = 0; colIdx < numCols; colIdx++) {
                        vectors[colIdx] = readVector(colIdx, rowsToRead, null, 0);
                    }
                    return new CupOfTea(DataFrame.fromVectors(List.of(vectors)));
                }

                // Vectorized filter pushdown with adaptive column access probing:
                ColumnVector[] probeVectors = new ColumnVector[numCols];
                boolean[] accessed = new boolean[numCols];
                for (int c = 0; c < numCols; c++) {
                    final int colIdx = c;
                    ColumnDescriptor cd = descriptors.get(c);
                    Class<?> cls = ParquetImporter.resolveColumnClass(cd);
                    probeVectors[c] = new com.cupoftea.table.vector.ObjectColumnVector(cd.getPath()[0], cls, 1, true) {
                        @Override public double getDouble(int r) { accessed[colIdx] = true; return 0.0; }
                        @Override public int getInt(int r) { accessed[colIdx] = true; return 0; }
                        @Override public long getLong(int r) { accessed[colIdx] = true; return 0L; }
                        @Override public float getFloat(int r) { accessed[colIdx] = true; return 0.0f; }
                        @Override public boolean getBoolean(int r) { accessed[colIdx] = true; return false; }
                        @Override public String getString(int r) { accessed[colIdx] = true; return ""; }
                        @Override public Object getObject(int r) { accessed[colIdx] = true; return null; }
                    };
                }
                Row probeRow = new Row(probeVectors, colIndexMap, colOrder, 0);
                for (int f = 0; f < filters.size(); f++) {
                    try {
                        filters.get(f).filter(probeRow);
                    } catch (Exception ignored) {}
                }

                List<Integer> filterColIndices = new ArrayList<>();
                for (int c = 0; c < numCols; c++) {
                    if (accessed[c]) {
                        filterColIndices.add(c);
                    }
                }
                if (filterColIndices.isEmpty()) {
                    for (int c = 0; c < numCols; c++) {
                        filterColIndices.add(c);
                    }
                }

                // Read filter column(s) first
                ColumnVector[] filterRowVectors = new ColumnVector[numCols];
                for (int fIdx : filterColIndices) {
                    filterRowVectors[fIdx] = readVector(fIdx, rowsToRead, null, 0);
                }

                // Evaluate filter predicates across rows
                java.util.BitSet matchMask = new java.util.BitSet(rowsToRead);
                int matchCount = 0;
                for (int r = 0; r < rowsToRead; r++) {
                    Row row = new Row(filterRowVectors, colIndexMap, colOrder, r);
                    boolean pass = true;
                    for (int f = 0; f < filters.size(); f++) {
                        if (!Boolean.TRUE.equals(filters.get(f).filter(row))) {
                            pass = false;
                            break;
                        }
                    }
                    if (pass) {
                        matchMask.set(r);
                        matchCount++;
                    }
                }

                if (matchCount == 0) {
                    for (int c = 0; c < numCols; c++) {
                        if (!filterColIndices.contains(c)) {
                            ColumnReader cr = currentColumnReaders[c];
                            for (int r = 0; r < rowsToRead; r++) {
                                cr.skip();
                            }
                        }
                    }
                    continue;
                }

                // Populate result vectors: filter columns are sliced/filtered; unread columns are decoded with skip()
                for (int c = 0; c < numCols; c++) {
                    if (filterColIndices.contains(c)) {
                        vectors[c] = filterRowVectors[c].filter(matchMask, matchCount);
                    } else {
                        vectors[c] = readVector(c, rowsToRead, matchMask, matchCount);
                    }
                }

                return new CupOfTea(DataFrame.fromVectors(List.of(vectors)));
            }
        }

        private ColumnVector readVector(int colIdx, int totalRows, java.util.BitSet matchMask, int matchCount) {
            ColumnDescriptor cd = schema.getColumns().get(colIdx);
            String colName = cd.getPath()[0];
            ColumnReader cr = currentColumnReaders[colIdx];
            CapturingConverter conv = currentConverters[colIdx];
            int maxDl = cd.getMaxDefinitionLevel();
            int outSize = (matchMask != null) ? matchCount : totalRows;

            switch (cd.getType()) {
                case INT32 -> {
                    int[] data = new int[outSize];
                    java.util.BitSet nulls = new java.util.BitSet(outSize);
                    if (matchMask == null) {
                        for (int r = 0; r < totalRows; r++) {
                            if (cr.getCurrentDefinitionLevel() < maxDl) {
                                nulls.set(r);
                            } else {
                                data[r] = cr.getInteger();
                            }
                            cr.consume();
                        }
                    } else {
                        int outIdx = 0;
                        for (int r = 0; r < totalRows; r++) {
                            if (matchMask.get(r)) {
                                if (cr.getCurrentDefinitionLevel() < maxDl) {
                                    nulls.set(outIdx);
                                } else {
                                    data[outIdx] = cr.getInteger();
                                }
                                outIdx++;
                                cr.consume();
                            } else {
                                cr.skip();
                            }
                        }
                    }
                    return new IntColumnVector(colName, true, data, nulls, outSize);
                }
                case INT64 -> {
                    long[] data = new long[outSize];
                    java.util.BitSet nulls = new java.util.BitSet(outSize);
                    if (matchMask == null) {
                        for (int r = 0; r < totalRows; r++) {
                            if (cr.getCurrentDefinitionLevel() < maxDl) {
                                nulls.set(r);
                            } else {
                                data[r] = cr.getLong();
                            }
                            cr.consume();
                        }
                    } else {
                        int outIdx = 0;
                        for (int r = 0; r < totalRows; r++) {
                            if (matchMask.get(r)) {
                                if (cr.getCurrentDefinitionLevel() < maxDl) {
                                    nulls.set(outIdx);
                                } else {
                                    data[outIdx] = cr.getLong();
                                }
                                outIdx++;
                                cr.consume();
                            } else {
                                cr.skip();
                            }
                        }
                    }
                    return new LongColumnVector(colName, true, data, nulls, outSize);
                }
                case FLOAT -> {
                    float[] data = new float[outSize];
                    java.util.BitSet nulls = new java.util.BitSet(outSize);
                    if (matchMask == null) {
                        for (int r = 0; r < totalRows; r++) {
                            if (cr.getCurrentDefinitionLevel() < maxDl) {
                                nulls.set(r);
                            } else {
                                data[r] = cr.getFloat();
                            }
                            cr.consume();
                        }
                    } else {
                        int outIdx = 0;
                        for (int r = 0; r < totalRows; r++) {
                            if (matchMask.get(r)) {
                                if (cr.getCurrentDefinitionLevel() < maxDl) {
                                    nulls.set(outIdx);
                                } else {
                                    data[outIdx] = cr.getFloat();
                                }
                                outIdx++;
                                cr.consume();
                            } else {
                                cr.skip();
                            }
                        }
                    }
                    return new FloatColumnVector(colName, true, data, nulls, outSize);
                }
                case DOUBLE -> {
                    double[] data = new double[outSize];
                    java.util.BitSet nulls = new java.util.BitSet(outSize);
                    if (matchMask == null) {
                        for (int r = 0; r < totalRows; r++) {
                            if (cr.getCurrentDefinitionLevel() < maxDl) {
                                nulls.set(r);
                            } else {
                                data[r] = cr.getDouble();
                            }
                            cr.consume();
                        }
                    } else {
                        int outIdx = 0;
                        for (int r = 0; r < totalRows; r++) {
                            if (matchMask.get(r)) {
                                if (cr.getCurrentDefinitionLevel() < maxDl) {
                                    nulls.set(outIdx);
                                } else {
                                    data[outIdx] = cr.getDouble();
                                }
                                outIdx++;
                                cr.consume();
                            } else {
                                cr.skip();
                            }
                        }
                    }
                    return new DoubleColumnVector(colName, true, data, nulls, outSize);
                }
                case BOOLEAN -> {
                    boolean[] data = new boolean[outSize];
                    java.util.BitSet nulls = new java.util.BitSet(outSize);
                    if (matchMask == null) {
                        for (int r = 0; r < totalRows; r++) {
                            if (cr.getCurrentDefinitionLevel() < maxDl) {
                                nulls.set(r);
                            } else {
                                data[r] = cr.getBoolean();
                            }
                            cr.consume();
                        }
                    } else {
                        int outIdx = 0;
                        for (int r = 0; r < totalRows; r++) {
                            if (matchMask.get(r)) {
                                if (cr.getCurrentDefinitionLevel() < maxDl) {
                                    nulls.set(outIdx);
                                } else {
                                    data[outIdx] = cr.getBoolean();
                                }
                                outIdx++;
                                cr.consume();
                            } else {
                                cr.skip();
                            }
                        }
                    }
                    return new BooleanColumnVector(colName, true, data, nulls, outSize);
                }
                case BINARY -> {
                    if (conv.getDictionary() != null) {
                        Dictionary dict = conv.getDictionary();
                        int dictSize = dict.getMaxId() + 1;
                        String[] dictEntries = new String[dictSize];
                        for (int d = 0; d < dictSize; d++) {
                            dictEntries[d] = dict.decodeToBinary(d).toStringUsingUTF8();
                        }
                        int[] codes = new int[outSize];
                        java.util.BitSet nulls = new java.util.BitSet(outSize);
                        if (matchMask == null) {
                            for (int r = 0; r < totalRows; r++) {
                                if (cr.getCurrentDefinitionLevel() < maxDl) {
                                    codes[r] = -1;
                                    nulls.set(r);
                                } else {
                                    codes[r] = cr.getCurrentValueDictionaryID();
                                }
                                cr.consume();
                            }
                        } else {
                            int outIdx = 0;
                            for (int r = 0; r < totalRows; r++) {
                                if (matchMask.get(r)) {
                                    if (cr.getCurrentDefinitionLevel() < maxDl) {
                                        codes[outIdx] = -1;
                                        nulls.set(outIdx);
                                    } else {
                                        codes[outIdx] = cr.getCurrentValueDictionaryID();
                                    }
                                    outIdx++;
                                    cr.consume();
                                } else {
                                    cr.skip();
                                }
                            }
                        }
                        return new DictionaryColumnVector(colName, true, dictEntries, codes, nulls, outSize);
                    } else {
                        StringColumnVector scv = new StringColumnVector(colName, outSize, true);
                        if (matchMask == null) {
                            for (int r = 0; r < totalRows; r++) {
                                if (cr.getCurrentDefinitionLevel() < maxDl) {
                                    scv.appendNull();
                                } else {
                                    byte[] bytes = cr.getBinary().getBytes();
                                    scv.appendUtf8Bytes(bytes, 0, bytes.length);
                                }
                                cr.consume();
                            }
                        } else {
                            for (int r = 0; r < totalRows; r++) {
                                if (matchMask.get(r)) {
                                    if (cr.getCurrentDefinitionLevel() < maxDl) {
                                        scv.appendNull();
                                    } else {
                                        byte[] bytes = cr.getBinary().getBytes();
                                        scv.appendUtf8Bytes(bytes, 0, bytes.length);
                                    }
                                    cr.consume();
                                } else {
                                    cr.skip();
                                }
                            }
                        }
                        return scv;
                    }
                }
                default -> {
                    StringColumnVector scv = new StringColumnVector(colName, outSize, true);
                    if (matchMask == null) {
                        for (int r = 0; r < totalRows; r++) {
                            if (cr.getCurrentDefinitionLevel() < maxDl) {
                                scv.appendNull();
                            } else {
                                byte[] bytes = cr.getBinary().getBytes();
                                scv.appendUtf8Bytes(bytes, 0, bytes.length);
                            }
                            cr.consume();
                        }
                    } else {
                        for (int r = 0; r < totalRows; r++) {
                            if (matchMask.get(r)) {
                                if (cr.getCurrentDefinitionLevel() < maxDl) {
                                    scv.appendNull();
                                } else {
                                    byte[] bytes = cr.getBinary().getBytes();
                                    scv.appendUtf8Bytes(bytes, 0, bytes.length);
                                }
                                cr.consume();
                            } else {
                                cr.skip();
                            }
                        }
                    }
                    return scv;
                }
            }
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                if (reader != null) {
                    reader.close();
                    reader = null;
                }
            }
        }
    }
}
