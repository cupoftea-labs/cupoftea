package com.cupoftea.excel;

import java.io.File;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.ExcelImporter;
import com.cupoftea.spi.FormatReader;
import com.cupoftea.spi.ReadOptions;
import com.cupoftea.table.interfaces.IDataFrame;

public class ExcelFormatReader implements FormatReader {

    @Override
    public String format() {
        return "excel";
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
            throw new ImportException("Excel file or path must be provided");
        }

        CupOfTea target = options.getTargetDataFrame() != null
                ? new CupOfTea(options.getTargetDataFrame())
                : new CupOfTea();

        ExcelImporter importer = new ExcelImporter(target);
        Object rawConfig = options.getOption("configuration");
        if (rawConfig instanceof com.cupoftea.importers.configurations.ExcelImporterConfiguration) {
            importer.importFromExcel(file, (com.cupoftea.importers.configurations.ExcelImporterConfiguration) rawConfig);
        } else {
            Object schemaOpt = options.getOption("schema");
            if (schemaOpt instanceof com.cupoftea.table.Schema) {
                importer.importFromExcel(file, options.hasHeaders(), (com.cupoftea.table.Schema) schemaOpt);
            } else {
                importer.importFromExcel(file, options.hasHeaders());
            }
        }
        return target.getDataFrame();
    }
}
