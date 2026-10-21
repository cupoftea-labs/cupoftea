package com.cupoftea.xml;

import java.io.File;

import org.w3c.dom.Document;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.XmlImporter;
import com.cupoftea.spi.FormatReader;
import com.cupoftea.spi.ReadOptions;
import com.cupoftea.table.interfaces.IDataFrame;

public class XmlFormatReader implements FormatReader {

    @Override
    public String format() {
        return "xml";
    }

    @Override
    public IDataFrame read(ReadOptions options) {
        if (options == null) {
            throw new ImportException("ReadOptions cannot be null");
        }

        CupOfTea target = options.getTargetDataFrame() != null
                ? new CupOfTea(options.getTargetDataFrame())
                : new CupOfTea();

        XmlImporter importer = new XmlImporter(target);

        if (options.getFile() != null) {
            importer.importFromXml(options.getFile());
        } else if (options.getSourceObject() instanceof Document doc) {
            importer.importFromXml(doc);
        } else if (options.getText() != null) {
            importer.importFromXml(options.getText());
        } else if (options.getPath() != null) {
            File file = new File(options.getPath());
            if (file.exists() && file.isFile()) {
                importer.importFromXml(file);
            } else {
                importer.importFromXml(options.getPath());
            }
        } else {
            throw new ImportException("No XML source provided in ReadOptions");
        }

        return target.getDataFrame();
    }
}
