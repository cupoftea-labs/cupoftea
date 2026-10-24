package com.cupoftea.arrow;

import java.io.File;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.ArrowExporter;
import com.cupoftea.spi.WriteOptions;
import com.cupoftea.table.interfaces.IDataFrame;

public class FeatherFormatWriter extends ArrowFormatWriter {

    @Override
    public String format() {
        return "feather";
    }

    @Override
    public void write(IDataFrame dataFrame, WriteOptions options) {
        if (options == null) {
            throw new ExportException("WriteOptions cannot be null");
        }
        File file = options.getFile();
        if (file == null && options.getPath() != null) {
            file = new File(options.getPath());
        }
        if (file == null) {
            throw new ExportException("Target file or path must be provided");
        }

        // Feather is Arrow File format
        ArrowIpcFormat ipcFormat = ArrowIpcFormat.FILE;

        CupOfTea cot = new CupOfTea(dataFrame);
        ArrowExporter exporter = new ArrowExporter(cot);
        exporter.exportToArrow(file, ipcFormat);
    }
}
