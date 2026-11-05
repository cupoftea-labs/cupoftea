package com.cupoftea.importers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.parquet.bytes.DirectByteBufferAllocator;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.ColumnReader;
import org.apache.parquet.column.impl.ColumnReaderImpl;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.ParquetReadOptions;
import org.apache.parquet.io.api.PrimitiveConverter;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.OriginalType;
import org.apache.parquet.schema.PrimitiveType;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.interfaces.IParquetImporter;
import com.cupoftea.parquet.NioChannelInputFile;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

public class ParquetImporter implements IParquetImporter {

    private static final PrimitiveConverter DUMMY_CONVERTER = new PrimitiveConverter() {};

    private final CupOfTea dataFrame;

    public ParquetImporter(final CupOfTea dataFrame) {
        this.dataFrame = dataFrame;
    }

    /**
     * Dumps data from a Parquet file into the CupOfTea data frame using columnar vectorized reading.
     * 
     * @param file Parquet file.
     */
    @Override
    public void importFromParquet(final File file) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        if (!file.exists()) {
            throw new ImportException("File does not exist: " + file.getPath());
        }

        NioChannelInputFile inputFile = new NioChannelInputFile(file);
        ParquetReadOptions options = ParquetReadOptions.builder()
                .withAllocator(DirectByteBufferAllocator.getInstance())
                .useOffHeapDecryptBuffer(true)
                .build();
        try (ParquetFileReader reader = ParquetFileReader.open(inputFile, options)) {
            MessageType schema = reader.getFileMetaData().getSchema();
            int totalRows = (int) reader.getRecordCount();
            List<ColumnDescriptor> descriptors = schema.getColumns();
            List<ColumnVector> vectors = new ArrayList<>(descriptors.size());

            for (ColumnDescriptor cd : descriptors) {
                String colName = cd.getPath()[0];
                vectors.add(createColumnVector(colName, cd, totalRows));
            }

            PageReadStore rowGroup;
            while ((rowGroup = reader.readNextRowGroup()) != null) {
                int rgRows = (int) rowGroup.getRowCount();
                for (int colIdx = 0; colIdx < descriptors.size(); colIdx++) {
                    ColumnDescriptor cd = descriptors.get(colIdx);
                    ColumnVector vec = vectors.get(colIdx);
                    ColumnReader cr = new ColumnReaderImpl(cd, rowGroup.getPageReader(cd), DUMMY_CONVERTER, null);
                    int maxDl = cd.getMaxDefinitionLevel();

                    for (int r = 0; r < rgRows; r++) {
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
            }

            DataFrame loaded = DataFrame.fromVectors(vectors);
            if (this.dataFrame != null) {
                this.dataFrame.setDataFrame(loaded);
            }
        } catch (IOException e) {
            throw new ImportException("Failed to read Parquet file: " + file.getPath(), e);
        } catch (Exception e) {
            if (e instanceof ImportException) {
                throw (ImportException) e;
            }
            throw new ImportException("Failed to read Parquet file: " + file.getPath(), e);
        }
    }

    public static Class<?> resolveColumnClass(ColumnDescriptor cd) {
        PrimitiveType pt = cd.getPrimitiveType();
        LogicalTypeAnnotation lta = pt.getLogicalTypeAnnotation();
        if (lta instanceof LogicalTypeAnnotation.DateLogicalTypeAnnotation) {
            return java.time.LocalDate.class;
        }
        if (lta instanceof LogicalTypeAnnotation.TimestampLogicalTypeAnnotation) {
            return java.time.Instant.class;
        }
        if (lta instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation) {
            return java.math.BigDecimal.class;
        }

        OriginalType ot = pt.getOriginalType();
        if (ot != null) {
            switch (ot) {
                case UTF8, JSON, BSON, ENUM -> { return String.class; }
                case DATE -> { return java.time.LocalDate.class; }
                case TIME_MILLIS, TIME_MICROS, TIMESTAMP_MILLIS, TIMESTAMP_MICROS -> { return java.time.Instant.class; }
                case DECIMAL -> { return java.math.BigDecimal.class; }
                default -> {}
            }
        }

        switch (cd.getType()) {
            case INT32 -> { return Integer.class; }
            case INT64 -> { return Long.class; }
            case FLOAT -> { return Float.class; }
            case DOUBLE -> { return Double.class; }
            case BOOLEAN -> { return Boolean.class; }
            case BINARY -> { return String.class; }
            default -> { return String.class; }
        }
    }

    public static ColumnVector createColumnVector(String colName, ColumnDescriptor cd, int initialCapacity) {
        switch (cd.getType()) {
            case INT32 -> { return new IntColumnVector(colName, initialCapacity, true); }
            case INT64 -> { return new LongColumnVector(colName, initialCapacity, true); }
            case FLOAT -> { return new FloatColumnVector(colName, initialCapacity, true); }
            case DOUBLE -> { return new DoubleColumnVector(colName, initialCapacity, true); }
            case BOOLEAN -> { return new BooleanColumnVector(colName, initialCapacity, true); }
            case BINARY -> { return new StringColumnVector(colName, initialCapacity, true); }
            default -> { return new StringColumnVector(colName, initialCapacity, true); }
        }
    }
}
