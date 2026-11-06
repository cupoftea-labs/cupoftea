package com.cupoftea.xml;

import java.io.File;

import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.configurations.XmlImporterConfiguration;
import com.cupoftea.lazy.LazySource;
import com.cupoftea.spi.FormatScanner;
import com.cupoftea.spi.ScanOptions;
import com.cupoftea.xml.lazy.XmlLazySource;

public class XmlFormatScanner implements FormatScanner {

    @Override
    public String format() {
        return "xml";
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
            throw new ImportException("XML file or path must be provided");
        }

        XmlImporterConfiguration config = null;
        Object rawConfig = options.getOption("configuration");
        if (rawConfig instanceof XmlImporterConfiguration) {
            config = (XmlImporterConfiguration) rawConfig;
        } else {
            config = new XmlImporterConfiguration();
            if (options.getSchema() != null) {
                config.setSchema(options.getSchema());
            }
            Object rowElement = options.getOption("rowElement");
            if (rowElement instanceof String s) {
                config.setRowElement(s);
            }
        }

        return new XmlLazySource(file, config, options);
    }
}
