package com.cupoftea.arrow;

import java.io.File;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.ArrowExporter;
import com.cupoftea.spi.FormatWriter;
import com.cupoftea.spi.WriteOptions;
import com.cupoftea.table.interfaces.IDataFrame;

public class ArrowFormatWriter implements FormatWriter {

    @Override
    public String format() {
        return "arrow";
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

        ArrowIpcFormat ipcFormat = parseIpcFormat(options.getOption("ipcFormat"));
        if (ipcFormat == null) {
            ipcFormat = ArrowIpcFormat.FILE;
        }

        CupOfTea cot = new CupOfTea(dataFrame);
        ArrowExporter exporter = new ArrowExporter(cot);
        exporter.exportToArrow(file, ipcFormat);
    }

    protected ArrowIpcFormat parseIpcFormat(Object optionVal) {
        if (optionVal instanceof ArrowIpcFormat fmt) {
            return fmt;
        }
        if (optionVal instanceof String str) {
            if ("stream".equalsIgnoreCase(str)) {
                return ArrowIpcFormat.STREAM;
            } else if ("file".equalsIgnoreCase(str) || "feather".equalsIgnoreCase(str)) {
                return ArrowIpcFormat.FILE;
            }
        }
        return null;
    }
}
