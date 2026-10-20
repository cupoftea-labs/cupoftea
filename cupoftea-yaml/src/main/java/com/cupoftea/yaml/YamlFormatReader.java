package com.cupoftea.yaml;

import java.io.File;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.YamlImporter;
import com.cupoftea.spi.FormatReader;
import com.cupoftea.spi.ReadOptions;
import com.cupoftea.table.interfaces.IDataFrame;

public class YamlFormatReader implements FormatReader {

    @Override
    public String format() {
        return "yaml";
    }

    @Override
    public IDataFrame read(ReadOptions options) {
        if (options == null) {
            throw new ImportException("ReadOptions cannot be null");
        }

        CupOfTea target = options.getTargetDataFrame() != null
                ? new CupOfTea(options.getTargetDataFrame())
                : new CupOfTea();

        YamlImporter importer = new YamlImporter(target);

        if (options.getFile() != null) {
            importer.importFromYaml(options.getFile());
        } else if (options.getText() != null) {
            importer.importFromYaml(options.getText());
        } else if (options.getPath() != null) {
            File file = new File(options.getPath());
            if (file.exists() && file.isFile()) {
                importer.importFromYaml(file);
            } else {
                importer.importFromYaml(options.getPath());
            }
        } else {
            throw new ImportException("No YAML source provided in ReadOptions");
        }

        return target.getDataFrame();
    }
}
