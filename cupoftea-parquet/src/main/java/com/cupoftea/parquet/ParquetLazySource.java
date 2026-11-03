package com.cupoftea.parquet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.ColumnReader;
import org.apache.parquet.column.impl.ColumnReaderImpl;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.io.LocalInputFile;
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
import com.cupoftea.table.Schema;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * High-performance streaming and lazy Parquet data source with column projection pushdown.
 */
public class ParquetLazySource implements LazySource {

    private static final PrimitiveConverter DUMMY_CONVERTER = new PrimitiveConverter() {};

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
        LocalInputFile inputFile = new LocalInputFile(file.toPath());
        try (org.apache.parquet.hadoop.ParquetFileReader reader = org.apache.parquet.hadoop.ParquetFileReader.open(inputFile)) {
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
                Set<String> requested = new HashSet<>(projected);
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
        return Collections.singletonList(new ParquetPartitionReader(file, projectedSchema, batchSize));
    }

    private static class ParquetPartitionReader implements PartitionReader {
        private final File file;
        private final MessageType schema;
        private final int batchSize;
        private org.apache.parquet.hadoop.ParquetFileReader reader;
        private PageReadStore currentRowGroup;
        private ColumnReader[] currentColumnReaders;
        private int currentRowGroupRemaining = 0;
        private boolean closed = false;

        public ParquetPartitionReader(File file, MessageType schema, int batchSize) {
            this.file = file;
            this.schema = schema;
            this.batchSize = batchSize;
        }

        private void ensureOpen() throws IOException {
            if (reader == null && !closed) {
                LocalInputFile inputFile = new LocalInputFile(file.toPath());
                this.reader = org.apache.parquet.hadoop.ParquetFileReader.open(inputFile);
                this.reader.setRequestedSchema(schema);
            }
        }

        @Override
        public CupOfTea nextBatch() throws IOException {
            if (closed) {
                return null;
            }
            ensureOpen();

            while (currentRowGroupRemaining <= 0) {
                currentRowGroup = reader.readNextRowGroup();
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
                for (int c = 0; c < cols.size(); c++) {
                    ColumnDescriptor cd = cols.get(c);
                    currentColumnReaders[c] = new ColumnReaderImpl(cd, currentRowGroup.getPageReader(cd), DUMMY_CONVERTER, null);
                }
            }

            int rowsToRead = Math.min(batchSize, currentRowGroupRemaining);
            List<ColumnDescriptor> descriptors = schema.getColumns();
            List<ColumnVector> vectors = new ArrayList<>(descriptors.size());

            for (ColumnDescriptor cd : descriptors) {
                String colName = cd.getPath()[0];
                vectors.add(ParquetImporter.createColumnVector(colName, cd, rowsToRead));
            }

            for (int colIdx = 0; colIdx < descriptors.size(); colIdx++) {
                ColumnDescriptor cd = descriptors.get(colIdx);
                ColumnVector vec = vectors.get(colIdx);
                ColumnReader cr = currentColumnReaders[colIdx];
                int maxDl = cd.getMaxDefinitionLevel();

                for (int r = 0; r < rowsToRead; r++) {
                    if (cr.getCurrentDefinitionLevel() < maxDl) {
                        vec.appendNull();
                    } else {
                        switch (cd.getType()) {
                            case INT32 -> ((IntColumnVector) vec).appendInt(cr.getInteger());
                            case INT64 -> ((LongColumnVector) vec).appendLong(cr.getLong());
                            case FLOAT -> ((FloatColumnVector) vec).appendFloat(cr.getFloat());
                            case DOUBLE -> ((DoubleColumnVector) vec).appendDouble(cr.getDouble());
                            case BOOLEAN -> ((BooleanColumnVector) vec).appendBoolean(cr.getBoolean());
                            case BINARY -> {
                                byte[] bytes = cr.getBinary().getBytes();
                                ((StringColumnVector) vec).appendUtf8Bytes(bytes, 0, bytes.length);
                            }
                            default -> {
                                byte[] bytes = cr.getBinary().getBytes();
                                ((StringColumnVector) vec).appendUtf8Bytes(bytes, 0, bytes.length);
                            }
                        }
                    }
                    cr.consume();
                }
            }

            currentRowGroupRemaining -= rowsToRead;
            return new CupOfTea(DataFrame.fromVectors(vectors));
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
