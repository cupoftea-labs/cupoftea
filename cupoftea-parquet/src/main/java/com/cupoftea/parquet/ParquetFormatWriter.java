package com.cupoftea.parquet;

import java.io.File;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.ParquetExporter;
import com.cupoftea.spi.FormatWriter;
import com.cupoftea.spi.WriteOptions;
import com.cupoftea.table.interfaces.IDataFrame;

public class ParquetFormatWriter implements FormatWriter {

    @Override
    public String format() {
        return "parquet";
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
        ParquetExporter exporter = new ParquetExporter(cot);
        exporter.exportToParquet(file);
    }
}
