package com.cupoftea.exporters.interfaces;

import java.io.File;
import java.io.OutputStream;

import com.cupoftea.exporters.configurations.ExcelExporterConfiguration;

public interface IExcelExporter {

    void exportToExcel(File file);

    void exportToExcel(File file, ExcelExporterConfiguration configuration);

    void exportToExcel(OutputStream outputStream);

    void exportToExcel(OutputStream outputStream, ExcelExporterConfiguration configuration);
}
