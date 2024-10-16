package com.CupOfTea.exporters.interfaces;

import java.io.File;

import com.CupOfTea.exporters.configurations.CSVExporterConfiguration;

public interface ICSVExporter {

    public void exportToCsv(File file) throws Exception;
    public void exportToCsv(File file, CSVExporterConfiguration configuration) throws Exception;
    
}
