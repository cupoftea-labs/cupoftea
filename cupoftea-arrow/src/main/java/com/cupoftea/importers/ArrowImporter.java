package com.cupoftea.importers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowFileReader;
import org.apache.arrow.vector.ipc.ArrowStreamReader;
import org.apache.arrow.vector.types.pojo.Field;

import com.cupoftea.CupOfTea;
import com.cupoftea.arrow.ArrowIpcFormat;
import com.cupoftea.arrow.ArrowSchemaConverter;
import com.cupoftea.arrow.ArrowVectorMapper;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.interfaces.IArrowImporter;
import com.cupoftea.table.Column;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.vector.ColumnVector;

public class ArrowImporter implements IArrowImporter {

    private static final byte[] ARROW_MAGIC = "ARROW1".getBytes(StandardCharsets.US_ASCII);

    private final CupOfTea target;

    public ArrowImporter(CupOfTea target) {
        this.target = target;
    }

    @Override
    public void importFromArrow(File file) {
        importFromArrow(file, null);
    }

    @Override
    public void importFromArrow(File file, ArrowIpcFormat format) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        if (!file.exists()) {
            throw new ImportException("Arrow file not found: " + file.getPath());
        }

        ArrowIpcFormat resolvedFormat = format != null ? format : detectFormat(file);

        try (BufferAllocator allocator = new RootAllocator()) {
            if (resolvedFormat == ArrowIpcFormat.FILE) {
                readFileFormat(file, allocator);
            } else {
                readStreamFormat(file, allocator);
            }
        } catch (ImportException e) {
            throw e;
        } catch (Exception e) {
            throw new ImportException("Failed to read Arrow data from file: " + file.getPath(), e);
        }
    }

    private void readFileFormat(File file, BufferAllocator allocator) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ArrowFileReader reader = new ArrowFileReader(fis.getChannel(), allocator)) {

            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            List<ColumnVector> columnVectors = null;

            while (reader.loadNextBatch()) {
                int rowCount = root.getRowCount();
                if (columnVectors == null) {
                    columnVectors = new ArrayList<>();
                    for (FieldVector fv : root.getFieldVectors()) {
                        columnVectors.add(ArrowVectorMapper.toColumnVector(fv));
                    }
                } else {
                    for (int i = 0; i < root.getFieldVectors().size(); i++) {
                        ArrowVectorMapper.appendBatch(columnVectors.get(i), root.getFieldVectors().get(i), rowCount);
                    }
                }
            }

            if (columnVectors == null) {
                columnVectors = new ArrayList<>();
                for (Field field : root.getSchema().getFields()) {
                    Column col = ArrowSchemaConverter.toCupOfTeaColumn(field);
                    columnVectors.add(ColumnVector.create(col, 0));
                }
            }

            DataFrame df = new DataFrame(columnVectors);
            this.target.setDataFrame(df);
        }
    }

    private void readStreamFormat(File file, BufferAllocator allocator) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ArrowStreamReader reader = new ArrowStreamReader(fis.getChannel(), allocator)) {

            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            List<ColumnVector> columnVectors = null;

            while (reader.loadNextBatch()) {
                int rowCount = root.getRowCount();
                if (columnVectors == null) {
                    columnVectors = new ArrayList<>();
                    for (FieldVector fv : root.getFieldVectors()) {
                        columnVectors.add(ArrowVectorMapper.toColumnVector(fv));
                    }
                } else {
                    for (int i = 0; i < root.getFieldVectors().size(); i++) {
                        ArrowVectorMapper.appendBatch(columnVectors.get(i), root.getFieldVectors().get(i), rowCount);
                    }
                }
            }

            if (columnVectors == null) {
                columnVectors = new ArrayList<>();
                for (Field field : root.getSchema().getFields()) {
                    Column col = ArrowSchemaConverter.toCupOfTeaColumn(field);
                    columnVectors.add(ColumnVector.create(col, 0));
                }
            }

            DataFrame df = new DataFrame(columnVectors);
            this.target.setDataFrame(df);
        }
    }

    private ArrowIpcFormat detectFormat(File file) {
        if (file.length() < ARROW_MAGIC.length) {
            return ArrowIpcFormat.STREAM;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[ARROW_MAGIC.length];
            int read = fis.read(header);
            if (read == ARROW_MAGIC.length && matchesMagic(header)) {
                return ArrowIpcFormat.FILE;
            }
        } catch (IOException ignored) {
            // fallback to STREAM
        }
        return ArrowIpcFormat.STREAM;
    }

    private static boolean matchesMagic(byte[] header) {
        for (int i = 0; i < ARROW_MAGIC.length; i++) {
            if (header[i] != ARROW_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
