package com.cupoftea.parquet;

import java.io.File;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.ParquetImporter;
import com.cupoftea.spi.FormatReader;
import com.cupoftea.spi.ReadOptions;
import com.cupoftea.table.interfaces.IDataFrame;

public class ParquetFormatReader implements FormatReader {

    @Override
    public String format() {
        return "parquet";
    }

    @Override
    public IDataFrame read(ReadOptions options) {
        if (options == null) {
            throw new ImportException("ReadOptions cannot be null");
        }

        CupOfTea target = options.getTargetDataFrame() != null
                ? new CupOfTea(options.getTargetDataFrame())
                : new CupOfTea();

        ParquetImporter importer = new ParquetImporter(target);

        if (options.getUri() != null) {
            importer.importFromUri(options.getUri());
            return target.getDataFrame();
        }

        File file = options.getFile();
        if (file == null && options.getPath() != null) {
            if (options.getPath().contains("://")) {
                importer.importFromUri(java.net.URI.create(options.getPath()));
                return target.getDataFrame();
            }
            file = new File(options.getPath());
        }
        if (file == null) {
            throw new ImportException("Parquet file, URI or path must be provided");
        }

        importer.importFromParquet(file);
        return target.getDataFrame();
    }
}
