package com.cupoftea.csv;

import java.io.File;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.CSVImporter;
import com.cupoftea.importers.configurations.CSVImporterConfiguration;
import com.cupoftea.spi.FormatReader;
import com.cupoftea.spi.ReadOptions;
import com.cupoftea.table.interfaces.IDataFrame;

public class CsvFormatReader implements FormatReader {

    @Override
    public String format() {
        return "csv";
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
            throw new ImportException("CSV file or path must be provided");
        }

        CSVImporterConfiguration config;
        Object rawConfig = options.getOption("configuration");
        if (rawConfig instanceof CSVImporterConfiguration) {
            config = (CSVImporterConfiguration) rawConfig;
        } else {
            config = new CSVImporterConfiguration();
            config.setHasHeaders(options.hasHeaders());
            Object schemaOpt = options.getOption("schema");
            if (schemaOpt instanceof com.cupoftea.table.Schema) {
                config.setSchema((com.cupoftea.table.Schema) schemaOpt);
            } else if (options.getSchema() != null && !options.getSchema().isEmpty()) {
                com.cupoftea.table.Schema.Builder sb = com.cupoftea.table.Schema.builder();
                options.getSchema().forEach(sb::add);
                config.setSchema(sb.build());
            }
        }

        CupOfTea target = options.getTargetDataFrame() != null
                ? new CupOfTea(options.getTargetDataFrame())
                : new CupOfTea();

        CSVImporter importer = new CSVImporter(target, config);
        importer.importFromCsv(file, config);
        return target.getDataFrame();
    }
}
