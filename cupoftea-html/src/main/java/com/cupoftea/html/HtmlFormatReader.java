package com.cupoftea.html;

import java.io.File;
import java.net.URL;

import org.jsoup.nodes.Document;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.HTMLImporter;
import com.cupoftea.spi.FormatReader;
import com.cupoftea.spi.ReadOptions;
import com.cupoftea.table.interfaces.IDataFrame;

public class HtmlFormatReader implements FormatReader {

    @Override
    public String format() {
        return "html";
    }

    @Override
    public IDataFrame read(ReadOptions options) {
        if (options == null) {
            throw new ImportException("ReadOptions cannot be null");
        }

        CupOfTea target = options.getTargetDataFrame() != null
                ? new CupOfTea(options.getTargetDataFrame())
                : new CupOfTea();

        int timeoutMillis = options.getOption("timeoutMillis", HTMLImporter.DEFAULT_TIMEOUT_MILLIS);
        int maxBodySizeBytes = options.getOption("maxBodySizeBytes", HTMLImporter.DEFAULT_MAX_BODY_SIZE_BYTES);

        HTMLImporter importer = new HTMLImporter(target, timeoutMillis, maxBodySizeBytes);

        if (options.getFile() != null) {
            importer.importFromHtml(options.getFile());
        } else if (options.getUrl() != null) {
            importer.importFromHtml(options.getUrl(), timeoutMillis, maxBodySizeBytes);
        } else if (options.getSourceObject() instanceof Document doc) {
            importer.importFromHtml(doc);
        } else if (options.getText() != null) {
            importer.importFromHtml(options.getText());
        } else if (options.getPath() != null) {
            File file = new File(options.getPath());
            if (file.exists() && file.isFile()) {
                importer.importFromHtml(file);
            } else {
                importer.importFromHtml(options.getPath());
            }
        } else {
            throw new ImportException("No HTML source provided in ReadOptions");
        }

        return target.getDataFrame();
    }
}
