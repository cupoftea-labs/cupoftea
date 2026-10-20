package com.cupoftea.utils.configurations;

import java.io.PrintStream;
import lombok.Getter;

@Getter
public class PrinterConfiguration {

    private int maxRows = 10;
    private int maxColumns = 0;
    private int terminalWidth = detectTerminalWidth();
    private int minColumnWidth = 5;
    private int maxColumnWidth = 22;
    private boolean autoAdjustColumnWidth = true;
    private int defaultColumnWidth = 22;
    private boolean showColumnType = false;
    private boolean showDescription = true;
    private String description = null;
    private String stringNull = "null";
    private PrintStream printStream = System.out;

    public PrinterConfiguration() {
    }

    private static int detectTerminalWidth() {
        try {
            String cols = System.getenv("COLUMNS");
            if (cols != null && !cols.trim().isEmpty()) {
                int width = Integer.parseInt(cols.trim());
                if (width >= 20) {
                    return width;
                }
            }
        } catch (Exception ignored) {
        }
        return 80;
    }

    public int getMaxRows() {
        return this.maxRows;
    }

    public int getMaxColumns() {
        return this.maxColumns;
    }

    public int getTerminalWidth() {
        return this.terminalWidth;
    }

    public int getMinColumnWidth() {
        return this.minColumnWidth;
    }

    public int getMaxColumnWidth() {
        return this.maxColumnWidth;
    }

    public boolean isAutoAdjustColumnWidth() {
        return this.autoAdjustColumnWidth;
    }

    public int getDefaultColumnWidth() {
        return this.defaultColumnWidth;
    }

    public boolean isShowColumnType() {
        return this.showColumnType;
    }

    public boolean isShowDescription() {
        return this.showDescription;
    }

    public String getDescription() {
        return this.description;
    }

    public String getStringNull() {
        return this.stringNull;
    }

    public PrintStream getPrintStream() {
        return this.printStream;
    }

    public PrinterConfiguration setMaxRows(int maxRows) {
        this.maxRows = maxRows;
        return this;
    }

    public PrinterConfiguration setMaxColumns(int maxColumns) {
        this.maxColumns = maxColumns;
        return this;
    }

    public PrinterConfiguration setTerminalWidth(int terminalWidth) {
        this.terminalWidth = terminalWidth;
        return this;
    }

    public PrinterConfiguration setMinColumnWidth(int minColumnWidth) {
        this.minColumnWidth = Math.max(3, minColumnWidth);
        return this;
    }

    public PrinterConfiguration setMaxColumnWidth(int maxColumnWidth) {
        this.maxColumnWidth = Math.max(3, maxColumnWidth);
        return this;
    }

    public PrinterConfiguration setAutoAdjustColumnWidth(boolean autoAdjustColumnWidth) {
        this.autoAdjustColumnWidth = autoAdjustColumnWidth;
        return this;
    }

    public PrinterConfiguration setDefaultColumnWidth(int defaultColumnWidth) {
        this.defaultColumnWidth = Math.max(3, defaultColumnWidth);
        return this;
    }

    public PrinterConfiguration setShowColumnType(boolean showColumnType) {
        this.showColumnType = showColumnType;
        return this;
    }

    public PrinterConfiguration setShowDescription(boolean showDescription) {
        this.showDescription = showDescription;
        return this;
    }

    public PrinterConfiguration setDescription(String description) {
        this.description = description;
        return this;
    }

    public PrinterConfiguration setStringNull(String stringNull) {
        this.stringNull = stringNull == null ? "null" : stringNull;
        return this;
    }

    public PrinterConfiguration setPrintStream(PrintStream printStream) {
        this.printStream = printStream != null ? printStream : System.out;
        return this;
    }
}
