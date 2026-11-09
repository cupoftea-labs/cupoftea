package com.cupoftea.csv;

import java.io.File;
import java.io.OutputStream;
import java.net.URI;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.CSVExporter;
import com.cupoftea.exporters.configurations.CSVExporterConfiguration;
import com.cupoftea.spi.FormatWriter;
import com.cupoftea.spi.WriteOptions;
import com.cupoftea.storage.StorageRegistry;
import com.cupoftea.table.interfaces.IDataFrame;

public class CsvFormatWriter implements FormatWriter {

    @Override
    public String format() {
        return "csv";
    }

    @Override
    public void write(IDataFrame dataFrame, WriteOptions options) {
        if (options == null) {
            throw new ExportException("WriteOptions cannot be null");
        }

        CSVExporterConfiguration config;
        Object rawConfig = options.getOption("configuration");
        if (rawConfig instanceof CSVExporterConfiguration) {
            config = (CSVExporterConfiguration) rawConfig;
        } else {
            config = new CSVExporterConfiguration();
        }

        CupOfTea cot = new CupOfTea(dataFrame);
        CSVExporter exporter = new CSVExporter(cot);

        if (options.getUri() != null) {
            try (OutputStream out = StorageRegistry.requireFileSystem(options.getUri()).openOutputStream(options.getUri())) {
                exporter.exportToOutputStream(out, config);
                return;
            } catch (Exception e) {
                throw new ExportException("Failed to write CSV to storage URI: " + options.getUri(), e);
            }
        }

        File file = options.getFile();
        if (file == null && options.getPath() != null) {
            if (options.getPath().contains("://")) {
                URI uri = URI.create(options.getPath());
                try (OutputStream out = StorageRegistry.requireFileSystem(uri).openOutputStream(uri)) {
                    exporter.exportToOutputStream(out, config);
                    return;
                } catch (Exception e) {
                    throw new ExportException("Failed to write CSV to storage URI: " + uri, e);
                }
            }
            file = new File(options.getPath());
        }
        if (file == null) {
            throw new ExportException("Target file, URI or path must be provided");
        }

        exporter.exportToCsv(file, config);
    }
}
