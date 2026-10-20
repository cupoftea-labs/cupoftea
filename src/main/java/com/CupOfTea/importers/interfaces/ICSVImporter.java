package com.CupOfTea.importers.interfaces;

import java.io.File;

import com.CupOfTea.importers.configurations.CSVImporterConfiguration;

public interface ICSVImporter {
    
    public void importFromCsv(File file) throws Exception;

    public void importFromCsv(File file, boolean hasHeaders) throws Exception;

    public void importFromCsv(File file, CSVImporterConfiguration configuration) throws Exception;

}
