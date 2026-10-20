package com.cupoftea.csv;

import java.io.File;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.CSVExporter;
import com.cupoftea.exporters.configurations.CSVExporterConfiguration;
import com.cupoftea.spi.FormatWriter;
import com.cupoftea.spi.WriteOptions;
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
        File file = options.getFile();
        if (file == null && options.getPath() != null) {
            file = new File(options.getPath());
        }
        if (file == null) {
            throw new ExportException("Target file or path must be provided");
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
        exporter.exportToCsv(file, config);
    }
}
