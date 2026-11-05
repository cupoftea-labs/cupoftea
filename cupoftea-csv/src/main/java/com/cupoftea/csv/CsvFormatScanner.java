package com.cupoftea.csv;

import java.io.File;

import com.cupoftea.csv.lazy.CsvLazySource;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.configurations.CSVImporterConfiguration;
import com.cupoftea.lazy.LazySource;
import com.cupoftea.spi.FormatScanner;
import com.cupoftea.spi.ScanOptions;

/**
 * {@link FormatScanner} provider for streaming / lazy CSV scans.
 */
public class CsvFormatScanner implements FormatScanner {

    @Override
    public String format() {
        return "csv";
    }

    @Override
    public LazySource scan(ScanOptions options) {
        if (options == null) {
            throw new ImportException("ScanOptions cannot be null");
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
            if (options.getDelimiter() != null) {
                config.setDelimiter(options.getDelimiter());
            }
            if (options.getSchema() != null) {
                config.setSchema(options.getSchema());
            }
        }

        return new CsvLazySource(file, config);
    }
}
