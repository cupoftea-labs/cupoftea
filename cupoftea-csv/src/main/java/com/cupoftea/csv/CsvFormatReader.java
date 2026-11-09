package com.cupoftea.csv;

import java.io.File;
import java.io.InputStream;
import java.net.URI;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.CSVImporter;
import com.cupoftea.importers.configurations.CSVImporterConfiguration;
import com.cupoftea.spi.FormatReader;
import com.cupoftea.spi.ReadOptions;
import com.cupoftea.storage.StorageRegistry;
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

        if (options.getUri() != null) {
            try (InputStream in = StorageRegistry.requireFileSystem(options.getUri()).openInputStream(options.getUri())) {
                importer.importFromInputStream(in, options.getUri().toString());
                return target.getDataFrame();
            } catch (Exception e) {
                throw new ImportException("Failed to read CSV from storage URI: " + options.getUri(), e);
            }
        }

        File file = options.getFile();
        if (file == null && options.getPath() != null) {
            if (options.getPath().contains("://")) {
                URI uri = URI.create(options.getPath());
                try (InputStream in = StorageRegistry.requireFileSystem(uri).openInputStream(uri)) {
                    importer.importFromInputStream(in, uri.toString());
                    return target.getDataFrame();
                } catch (Exception e) {
                    throw new ImportException("Failed to read CSV from storage URI: " + uri, e);
                }
            }
            file = new File(options.getPath());
        }
        if (file == null) {
            throw new ImportException("CSV file, URI or path must be provided");
        }

        importer.importFromCsv(file, config);
        return target.getDataFrame();
    }
}
