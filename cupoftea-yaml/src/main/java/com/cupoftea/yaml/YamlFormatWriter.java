package com.cupoftea.yaml;

import java.io.File;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.YamlExporter;
import com.cupoftea.spi.FormatWriter;
import com.cupoftea.spi.WriteOptions;
import com.cupoftea.table.interfaces.IDataFrame;

public class YamlFormatWriter implements FormatWriter {

    @Override
    public String format() {
        return "yaml";
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

        CupOfTea cot = new CupOfTea(dataFrame);
        YamlExporter exporter = new YamlExporter(cot);
        exporter.exportToYaml(file);
    }
}
