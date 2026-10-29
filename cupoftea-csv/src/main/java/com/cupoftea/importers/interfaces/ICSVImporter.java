package com.cupoftea.importers.interfaces;

import java.io.File;

import com.cupoftea.importers.configurations.CSVImporterConfiguration;
import com.cupoftea.table.Schema;

public interface ICSVImporter {
    
    void importFromCsv(File file);

    void importFromCsv(File file, boolean hasHeaders);

    default void importFromCsv(File file, Schema schema) {
        importFromCsv(file, true, schema);
    }

    void importFromCsv(File file, boolean hasHeaders, Schema schema);

    void importFromCsv(File file, CSVImporterConfiguration configuration);

}
