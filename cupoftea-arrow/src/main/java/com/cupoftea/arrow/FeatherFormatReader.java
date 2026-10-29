package com.cupoftea.arrow;

import java.io.File;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.ArrowImporter;
import com.cupoftea.spi.ReadOptions;
import com.cupoftea.table.interfaces.IDataFrame;

public class FeatherFormatReader extends ArrowFormatReader {

    @Override
    public String format() {
        return "feather";
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
            throw new ImportException("Feather file or path must be provided");
        }

        // Feather is Arrow File format
        ArrowIpcFormat ipcFormat = parseIpcFormat(options.getOption("ipcFormat"));
        if (ipcFormat == null) {
            ipcFormat = ArrowIpcFormat.FILE;
        }

        CupOfTea target = options.getTargetDataFrame() != null
                ? new CupOfTea(options.getTargetDataFrame())
                : new CupOfTea();

        ArrowImporter importer = new ArrowImporter(target);
        importer.importFromArrow(file, ipcFormat);
        return target.getDataFrame();
    }
}
