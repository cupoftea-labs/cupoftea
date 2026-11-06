package com.cupoftea.json;

import java.io.File;

import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.configurations.JsonImporterConfiguration;
import com.cupoftea.lazy.LazySource;
import com.cupoftea.spi.FormatScanner;
import com.cupoftea.spi.ScanOptions;

/**
 * {@link FormatScanner} provider for streaming and lazy JSON / NDJSON scans.
 */
public class JsonFormatScanner implements FormatScanner {

    @Override
    public String format() {
        return "json";
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
            throw new ImportException("JSON file or path must be provided");
        }

        JsonImporterConfiguration config;
        Object rawConfig = options.getOption("configuration");
        if (rawConfig instanceof JsonImporterConfiguration) {
            config = (JsonImporterConfiguration) rawConfig;
        } else {
            config = new JsonImporterConfiguration();
            if (options.getSchema() != null) {
                config.setSchema(options.getSchema());
            }
            if (options.getBatchSize() > 0) {
                config.setBatchSize(options.getBatchSize());
            }
        }

        return new JsonLazySource(file, config, options);
    }
}
