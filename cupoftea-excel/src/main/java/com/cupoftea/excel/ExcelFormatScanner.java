package com.cupoftea.excel;

import java.io.File;

import com.cupoftea.excel.lazy.ExcelLazySource;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.lazy.LazySource;
import com.cupoftea.spi.FormatScanner;
import com.cupoftea.spi.ScanOptions;

public class ExcelFormatScanner implements FormatScanner {

    @Override
    public String format() {
        return "excel";
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
            throw new ImportException("Excel file or path must be provided");
        }

        return new ExcelLazySource(file, options);
    }
}
