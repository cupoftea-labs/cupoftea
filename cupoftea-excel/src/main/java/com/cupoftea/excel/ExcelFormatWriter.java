package com.cupoftea.excel;

import java.io.File;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.ExcelExporter;
import com.cupoftea.exporters.configurations.ExcelExporterConfiguration;
import com.cupoftea.spi.FormatWriter;
import com.cupoftea.spi.WriteOptions;
import com.cupoftea.table.interfaces.IDataFrame;

public class ExcelFormatWriter implements FormatWriter {

    @Override
    public String format() {
        return "excel";
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

        ExcelExporterConfiguration config;
        Object rawConfig = options.getOption("configuration");
        if (rawConfig instanceof ExcelExporterConfiguration) {
            config = (ExcelExporterConfiguration) rawConfig;
        } else {
            config = new ExcelExporterConfiguration();
        }

        CupOfTea cot = new CupOfTea(dataFrame);
        ExcelExporter exporter = new ExcelExporter(cot);
        exporter.exportToExcel(file, config);
    }
}
