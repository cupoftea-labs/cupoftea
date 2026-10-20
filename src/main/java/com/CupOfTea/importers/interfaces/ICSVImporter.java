package com.CupOfTea.importers.interfaces;

import java.io.File;

import com.CupOfTea.importers.configurations.CSVImporterConfiguration;

public interface ICSVImporter {
    
    public void importFromCsv(File file);

    public void importFromCsv(File file, boolean hasHeaders);

    public void importFromCsv(File file, CSVImporterConfiguration configuration);

}
