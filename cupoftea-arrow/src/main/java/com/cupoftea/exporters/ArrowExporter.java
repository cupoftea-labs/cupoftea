package com.cupoftea.exporters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowFileWriter;
import org.apache.arrow.vector.ipc.ArrowStreamWriter;
import org.apache.arrow.vector.types.pojo.Schema;

import com.cupoftea.CupOfTea;
import com.cupoftea.arrow.ArrowIpcFormat;
import com.cupoftea.arrow.ArrowSchemaConverter;
import com.cupoftea.arrow.ArrowVectorMapper;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.interfaces.IArrowExporter;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.vector.ColumnVector;

public class ArrowExporter implements IArrowExporter {

    private final CupOfTea cupOfTea;

    public ArrowExporter(CupOfTea cupOfTea) {
        this.cupOfTea = cupOfTea;
    }

    @Override
    public void exportToArrow(File file) {
        exportToArrow(file, ArrowIpcFormat.FILE);
    }

    @Override
    public void exportToArrow(File file, ArrowIpcFormat format) {
        if (file == null) {
            throw new ExportException("Target file cannot be null");
        }
        if (cupOfTea == null || cupOfTea.getDataFrame() == null) {
            throw new ExportException("No DataFrame available to export");
        }

        IDataFrame df = cupOfTea.getDataFrame();
        ArrowIpcFormat resolvedFormat = format != null ? format : ArrowIpcFormat.FILE;
        Schema arrowSchema = ArrowSchemaConverter.toArrowSchema(df);

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (BufferAllocator allocator = new RootAllocator();
             VectorSchemaRoot root = VectorSchemaRoot.create(arrowSchema, allocator)) {

            int rowCount = df.size();
            for (FieldVector fv : root.getFieldVectors()) {
                ColumnVector cv = df.getColumnVector(fv.getName());
                if (cv != null) {
                    ArrowVectorMapper.populateArrowVector(fv, cv, rowCount);
                }
            }
            root.setRowCount(rowCount);

            if (resolvedFormat == ArrowIpcFormat.STREAM) {
                try (FileOutputStream fos = new FileOutputStream(file);
                     ArrowStreamWriter writer = new ArrowStreamWriter(root, null, fos.getChannel())) {
                    writer.start();
                    writer.writeBatch();
                    writer.end();
                }
            } else {
                try (FileOutputStream fos = new FileOutputStream(file);
                     ArrowFileWriter writer = new ArrowFileWriter(root, null, fos.getChannel())) {
                    writer.start();
                    writer.writeBatch();
                    writer.end();
                }
            }
        } catch (ExportException e) {
            throw e;
        } catch (IOException e) {
            throw new ExportException("Failed to write Arrow data to file: " + file.getPath(), e);
        } catch (Exception e) {
            throw new ExportException("Error while exporting DataFrame to Arrow: " + e.getMessage(), e);
        }
    }
}
