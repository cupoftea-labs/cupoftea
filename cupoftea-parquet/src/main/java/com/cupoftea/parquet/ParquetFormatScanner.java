package com.cupoftea.parquet;

import java.io.File;

import com.cupoftea.exceptions.ImportException;
import com.cupoftea.lazy.LazySource;
import com.cupoftea.spi.FormatScanner;
import com.cupoftea.spi.ScanOptions;

/**
 * {@link FormatScanner} provider for lazy Parquet scans with projection pushdown.
 */
public class ParquetFormatScanner implements FormatScanner {

    @Override
    public String format() {
        return "parquet";
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
            throw new ImportException("Parquet file or path must be provided");
        }

        return new ParquetLazySource(file, options);
    }
}
