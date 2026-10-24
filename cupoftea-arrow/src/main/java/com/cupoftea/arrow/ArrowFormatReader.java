package com.cupoftea.arrow;

import java.io.File;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.ArrowImporter;
import com.cupoftea.spi.FormatReader;
import com.cupoftea.spi.ReadOptions;
import com.cupoftea.table.interfaces.IDataFrame;

public class ArrowFormatReader implements FormatReader {

    @Override
    public String format() {
        return "arrow";
    }

    @Override
    public IDataFrame read(ReadOptions options) {
        if (options == null) {
            throw new ImportException("ReadOptions cannot be null");
        }
        File file = options.getFile();
        if (file == null && options.getPath() != null) {
            file = new File(options.getPath());
        }
        if (file == null) {
            throw new ImportException("Arrow file or path must be provided");
        }

        ArrowIpcFormat ipcFormat = parseIpcFormat(options.getOption("ipcFormat"));

        CupOfTea target = options.getTargetDataFrame() != null
                ? new CupOfTea(options.getTargetDataFrame())
                : new CupOfTea();

        ArrowImporter importer = new ArrowImporter(target);
        importer.importFromArrow(file, ipcFormat);
        return target.getDataFrame();
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
