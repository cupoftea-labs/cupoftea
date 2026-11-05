package com.cupoftea.exporters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.ColumnWriteStore;
import org.apache.parquet.column.ColumnWriter;
import org.apache.parquet.column.ParquetProperties;
import org.apache.parquet.hadoop.CodecFactory;
import org.apache.parquet.hadoop.ColumnChunkPageWriteStore;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.LocalOutputFile;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.apache.parquet.schema.Types;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.interfaces.IParquetExporter;
import com.cupoftea.table.Column;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DictionaryColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.ParallelVectorEngine;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * High-performance Parquet exporter that consumes primitive ColumnVectors directly
 * without Avro or Hadoop GenericRecord row-by-row object allocations,
 * and parallelizes row group encoding across available cores.
 */
public class ParquetExporter implements IParquetExporter {

    private static final int PARALLEL_ROW_THRESHOLD = 16_384;
    private static final int MIN_CHUNK_ROW_SIZE = 16_384;

    private final CupOfTea dataFrame;

    public ParquetExporter(CupOfTea dataFrame) {
        this.dataFrame = dataFrame;
    }

    /**
     * Create a PARQUET file from the data frame using direct columnar writing.
     * 
     * @param file path to your PARQUET file. Can't be null.
     */
    @Override
    public void exportToParquet(File file) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }

        try {
            IDataFrame df = this.dataFrame.getDataFrame();
            int rowCount = df.size();
            List<Column> headers = this.dataFrame.getHeaders();

            Types.MessageTypeBuilder builder = Types.buildMessage();
            for (Column col : headers) {
                Class<?> columnType = col.columnType();
                if (columnType == Integer.class || columnType == int.class) {
                    builder.optional(PrimitiveTypeName.INT32).named(col.columnName());
                } else if (columnType == Long.class || columnType == long.class) {
                    builder.optional(PrimitiveTypeName.INT64).named(col.columnName());
                } else if (columnType == Double.class || columnType == double.class) {
                    builder.optional(PrimitiveTypeName.DOUBLE).named(col.columnName());
                } else if (columnType == Float.class || columnType == float.class) {
                    builder.optional(PrimitiveTypeName.FLOAT).named(col.columnName());
                } else if (columnType == Boolean.class || columnType == boolean.class) {
                    builder.optional(PrimitiveTypeName.BOOLEAN).named(col.columnName());
                } else {
                    builder.optional(PrimitiveTypeName.BINARY).as(LogicalTypeAnnotation.stringType()).named(col.columnName());
                }
            }
            MessageType schema = builder.named("DataFrame");

            ParquetFileWriter fileWriter = new ParquetFileWriter(
                    new LocalOutputFile(file.toPath()),
                    schema,
                    ParquetFileWriter.Mode.CREATE,
                    ParquetWriter.DEFAULT_BLOCK_SIZE,
                    ParquetWriter.MAX_PADDING_SIZE_DEFAULT
            );
            fileWriter.start();

            ParquetProperties props = ParquetProperties.builder().build();

            ExecutorService executor = df != null ? df.getExecutor() : null;
            if (executor == null) {
                executor = ForkJoinPool.commonPool();
            }

            int parallelism = ParallelVectorEngine.getParallelism(executor);
            if (rowCount >= PARALLEL_ROW_THRESHOLD && parallelism > 1) {
                exportParallelRowGroups(df, headers, schema, props, rowCount, fileWriter, executor, parallelism);
            } else {
                exportSingleRowGroup(df, headers, schema, props, 0, rowCount, fileWriter);
            }

            fileWriter.end(Collections.emptyMap());
            fileWriter.close();

        } catch (IOException e) {
            throw new ExportException("Failed to export to Parquet file: " + file.getPath(), e);
        } catch (Exception e) {
            if (e instanceof ExportException) {
                throw (ExportException) e;
            }
            throw new ExportException("Failed to export to Parquet file: " + file.getPath(), e);
        }
    }

    private static class EncodedRowGroup {
        final ColumnChunkPageWriteStore pageStore;
        final ColumnWriteStore columnStore;
        final CodecFactory codecFactory;
        final int startRow;
        final int endRow;

        EncodedRowGroup(ColumnChunkPageWriteStore pageStore, ColumnWriteStore columnStore, CodecFactory codecFactory, int startRow, int endRow) {
            this.pageStore = pageStore;
            this.columnStore = columnStore;
            this.codecFactory = codecFactory;
            this.startRow = startRow;
            this.endRow = endRow;
        }

        void close() {
            try {
                columnStore.close();
                pageStore.close();
                codecFactory.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void exportParallelRowGroups(IDataFrame df, List<Column> headers, MessageType schema,
                                         ParquetProperties props, int rowCount, ParquetFileWriter fileWriter,
                                         ExecutorService executor, int parallelism) throws IOException {
        int chunkSize = Math.max(MIN_CHUNK_ROW_SIZE, (rowCount + parallelism - 1) / parallelism);
        int numChunks = (rowCount + chunkSize - 1) / chunkSize;
        List<Callable<EncodedRowGroup>> tasks = new ArrayList<>(numChunks);

        for (int i = 0; i < numChunks; i++) {
            final int start = i * chunkSize;
            final int end = Math.min(rowCount, (i + 1) * chunkSize);
            if (start >= end) continue;

            tasks.add(new Callable<EncodedRowGroup>() {
                @Override
                public EncodedRowGroup call() throws IOException {
                    return encodeRowGroup(df, headers, schema, props, start, end);
                }
            });
        }

        try {
            List<Future<EncodedRowGroup>> futures = executor.invokeAll(tasks);
            for (Future<EncodedRowGroup> future : futures) {
                EncodedRowGroup rg = future.get();
                try {
                    int count = rg.endRow - rg.startRow;
                    fileWriter.startBlock(count);
                    rg.pageStore.flushToFileWriter(fileWriter);
                    fileWriter.endBlock();
                } finally {
                    rg.close();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Parquet row group encoding interrupted", e);
        } catch (Exception e) {
            throw new IOException("Failed to encode Parquet row groups in parallel", e);
        }
    }

    private EncodedRowGroup encodeRowGroup(IDataFrame df, List<Column> headers, MessageType schema,
                                           ParquetProperties props, int start, int end) {
        Configuration conf = new Configuration();
        CodecFactory codecFactory = new CodecFactory(conf, props.getPageSizeThreshold());
        CodecFactory.BytesCompressor compressor = codecFactory.getCompressor(CompressionCodecName.SNAPPY);

        ColumnChunkPageWriteStore pageStore = new ColumnChunkPageWriteStore(
                compressor,
                schema,
                props.getAllocator(),
                props.getColumnIndexTruncateLength(),
                props.getPageWriteChecksumEnabled()
        );

        ColumnWriteStore columnStore = props.newColumnWriteStore(schema, pageStore);
        int count = end - start;

        int colIdx = 0;
        for (Column col : headers) {
            ColumnDescriptor cd = schema.getColumns().get(colIdx++);
            ColumnWriter cw = columnStore.getColumnWriter(cd);
            ColumnVector vec = df.getColumnVector(col.columnName());

            if (vec instanceof IntColumnVector iv) {
                int[] data = iv.getRawData();
                for (int r = start; r < end; r++) {
                    if (iv.isNull(r)) {
                        cw.writeNull(0, 0);
                    } else {
                        cw.write(data[r], 0, 1);
                    }
                }
            } else if (vec instanceof LongColumnVector lv) {
                long[] data = lv.getRawData();
                for (int r = start; r < end; r++) {
                    if (lv.isNull(r)) {
                        cw.writeNull(0, 0);
                    } else {
                        cw.write(data[r], 0, 1);
                    }
                }
            } else if (vec instanceof DoubleColumnVector dv) {
                double[] data = dv.getRawData();
                for (int r = start; r < end; r++) {
                    if (dv.isNull(r)) {
                        cw.writeNull(0, 0);
                    } else {
                        cw.write(data[r], 0, 1);
                    }
                }
            } else if (vec instanceof FloatColumnVector fv) {
                float[] data = fv.getRawData();
                for (int r = start; r < end; r++) {
                    if (fv.isNull(r)) {
                        cw.writeNull(0, 0);
                    } else {
                        cw.write(data[r], 0, 1);
                    }
                }
            } else if (vec instanceof BooleanColumnVector bv) {
                for (int r = start; r < end; r++) {
                    if (bv.isNull(r)) {
                        cw.writeNull(0, 0);
                    } else {
                        cw.write(bv.getBoolean(r), 0, 1);
                    }
                }
            } else if (vec instanceof DictionaryColumnVector dcv) {
                int dictSize = dcv.getDictionarySize();
                Binary[] dictBinaries = new Binary[dictSize];
                String[] rawDict = dcv.getDictionary();
                for (int d = 0; d < dictSize; d++) {
                    String s = rawDict[d];
                    dictBinaries[d] = s != null ? Binary.fromString(s) : null;
                }
                for (int r = start; r < end; r++) {
                    if (dcv.isNull(r)) {
                        cw.writeNull(0, 0);
                    } else {
                        int code = dcv.getCodeUnchecked(r);
                        Binary bin = dictBinaries[code];
                        if (bin != null) {
                            cw.write(bin, 0, 1);
                        } else {
                            cw.writeNull(0, 0);
                        }
                    }
                }
            } else if (vec instanceof StringColumnVector sv) {
                byte[] rawBytes = sv.getValues();
                int[] offsets = sv.getOffsets();
                for (int r = start; r < end; r++) {
                    if (sv.isNull(r)) {
                        cw.writeNull(0, 0);
                    } else {
                        int s = offsets[r];
                        int len = offsets[r + 1] - s;
                        cw.write(Binary.fromByteArray(rawBytes, s, len), 0, 1);
                    }
                }
            } else {
                for (int r = start; r < end; r++) {
                    Object val = vec != null ? vec.getObject(r) : null;
                    if (val == null) {
                        cw.writeNull(0, 0);
                    } else {
                        cw.write(Binary.fromString(val.toString()), 0, 1);
                    }
                }
            }
        }

        for (int r = 0; r < count; r++) {
            columnStore.endRecord();
        }
        columnStore.flush();

        return new EncodedRowGroup(pageStore, columnStore, codecFactory, start, end);
    }

    private void exportSingleRowGroup(IDataFrame df, List<Column> headers, MessageType schema,
                                      ParquetProperties props, int start, int end,
                                      ParquetFileWriter fileWriter) throws IOException {
        EncodedRowGroup rg = encodeRowGroup(df, headers, schema, props, start, end);
        try {
            int count = end - start;
            fileWriter.startBlock(count);
            rg.pageStore.flushToFileWriter(fileWriter);
            fileWriter.endBlock();
        } finally {
            rg.close();
        }
    }
}

