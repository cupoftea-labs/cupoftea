package com.cupoftea.importers.interfaces;

import java.io.File;

import com.cupoftea.importers.configurations.ExcelImporterConfiguration;
import com.cupoftea.table.Schema;

public interface IExcelImporter {

    void importFromExcel(File file, boolean hasHeaders);

    default void importFromExcel(File file) {
        importFromExcel(file, true);
    }

    default void importFromExcel(File file, Schema schema) {
        importFromExcel(file, true, schema);
    }

    void importFromExcel(File file, boolean hasHeaders, Schema schema);

    void importFromExcel(File file, ExcelImporterConfiguration configuration);

}
