package com.cupoftea.exporters.configurations;

public class ExcelExporterConfiguration {

    private int rowAccessWindowSize = 100;
    private boolean compressTempFiles = true;
    private boolean writeHeader = true;
    private String sheetName = "Sheet1";
    private String dateFormat = "yyyy-mm-dd";
    private String dateTimeFormat = "yyyy-mm-dd hh:mm:ss";

    public ExcelExporterConfiguration() {
    }

    public int getRowAccessWindowSize() {
        return rowAccessWindowSize;
    }

    public ExcelExporterConfiguration setRowAccessWindowSize(int rowAccessWindowSize) {
        if (rowAccessWindowSize <= 0) {
            throw new IllegalArgumentException("rowAccessWindowSize must be positive");
        }
        this.rowAccessWindowSize = rowAccessWindowSize;
        return this;
    }

    public boolean isCompressTempFiles() {
        return compressTempFiles;
    }

    public ExcelExporterConfiguration setCompressTempFiles(boolean compressTempFiles) {
        this.compressTempFiles = compressTempFiles;
        return this;
    }

    public boolean isWriteHeader() {
        return writeHeader;
    }

    public ExcelExporterConfiguration setWriteHeader(boolean writeHeader) {
        this.writeHeader = writeHeader;
        return this;
    }

    public String getSheetName() {
        return sheetName;
    }

    public ExcelExporterConfiguration setSheetName(String sheetName) {
        this.sheetName = (sheetName != null && !sheetName.isBlank()) ? sheetName : "Sheet1";
        return this;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public ExcelExporterConfiguration setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
        return this;
    }

    public String getDateTimeFormat() {
        return dateTimeFormat;
    }

    public ExcelExporterConfiguration setDateTimeFormat(String dateTimeFormat) {
        this.dateTimeFormat = dateTimeFormat;
        return this;
    }
}
