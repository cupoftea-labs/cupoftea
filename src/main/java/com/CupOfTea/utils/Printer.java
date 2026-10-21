package com.CupOfTea.utils;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.table.Column;
import com.CupOfTea.table.Group;
import com.CupOfTea.table.Row;
import com.CupOfTea.utils.configurations.PrinterConfiguration;
import com.CupOfTea.utils.interfaces.IPrinter;

public class Printer implements IPrinter {

    private final CupOfTea dataFrame;
    private final PrinterConfiguration configuration;

    public Printer(CupOfTea dataFrame) {
        this(dataFrame, new PrinterConfiguration());
    }

    public Printer(CupOfTea dataFrame, PrinterConfiguration configuration) {
        this.dataFrame = dataFrame;
        this.configuration = configuration != null ? configuration : new PrinterConfiguration();
    }

    private static class DisplayColumn {
        final String columnName;
        final String headerText;
        final int width;
        final boolean isEllipsis;

        DisplayColumn(String columnName, String headerText, int width, boolean isEllipsis) {
            this.columnName = columnName;
            this.headerText = headerText;
            this.width = width;
            this.isEllipsis = isEllipsis;
        }
    }

    @Override
    public void print() {
        print(this.configuration);
    }

    @Override
    public void print(PrinterConfiguration config) {
        PrinterConfiguration conf = config != null ? config : this.configuration;
        PrintStream out = conf.getPrintStream() != null ? conf.getPrintStream() : System.out;
        out.print(format(conf));
    }

    @Override
    public String format() {
        return format(this.configuration);
    }

    @Override
    public String format(PrinterConfiguration config) {
        PrinterConfiguration conf = config != null ? config : this.configuration;
        StringBuilder sb = new StringBuilder();

        List<Column> availableHeaders = getPrintableHeaders();
        int totalRows = this.dataFrame != null && this.dataFrame.size() != null ? this.dataFrame.size() : 0;
        int maxRows = conf.getMaxRows();
        int rowsToDisplay = maxRows > 0 ? Math.min(maxRows, totalRows) : totalRows;

        if (availableHeaders.isEmpty()) {
            if (conf.isShowDescription()) {
                String desc = conf.getDescription() != null ? conf.getDescription() : "Empty DataFrame (0 rows, 0 columns)";
                sb.append(desc).append(System.lineSeparator());
            }
            return sb.toString();
        }

        List<DisplayColumn> visibleColumns = selectVisibleColumns(availableHeaders, rowsToDisplay, conf);
        boolean hasTruncatedColumns = visibleColumns.size() < availableHeaders.size()
                || visibleColumns.stream().anyMatch(c -> c.isEllipsis);
        int realVisibleCount = (int) visibleColumns.stream().filter(c -> !c.isEllipsis).count();

        if (conf.isShowDescription()) {
            String desc = buildDescription(totalRows, rowsToDisplay, availableHeaders.size(), realVisibleCount, hasTruncatedColumns, conf);
            sb.append(desc).append(System.lineSeparator());
        }

        sb.append(buildTopBorder(visibleColumns)).append(System.lineSeparator());
        sb.append(buildHeaderRow(visibleColumns)).append(System.lineSeparator());
        sb.append(buildMiddleBorder(visibleColumns)).append(System.lineSeparator());

        for (int i = 0; i < rowsToDisplay; i++) {
            Row row = this.dataFrame.getRow(i);
            sb.append(buildDataRow(visibleColumns, row, conf)).append(System.lineSeparator());
        }

        sb.append(buildBottomBorder(visibleColumns)).append(System.lineSeparator());

        return sb.toString();
    }

    private List<Column> getPrintableHeaders() {
        if (this.dataFrame == null || this.dataFrame.getHeaders() == null) {
            return Collections.emptyList();
        }
        return this.dataFrame.getHeaders().stream()
                .filter(a -> a != null && a.columnType() != Group.class)
                .collect(Collectors.toList());
    }

    private String getColumnHeaderText(Column column, PrinterConfiguration conf) {
        String name = column.columnName() != null ? column.columnName() : "";
        if (conf.isShowColumnType() && column.columnType() != null) {
            return name + " (" + column.columnType().getSimpleName() + ")";
        }
        return name;
    }

    private int computeColumnWidth(Column column, int rowsToDisplay, PrinterConfiguration conf) {
        if (!conf.isAutoAdjustColumnWidth()) {
            int w = conf.getDefaultColumnWidth();
            if (conf.getTerminalWidth() > 0) {
                w = Math.min(w, Math.max(conf.getMinColumnWidth(), conf.getTerminalWidth() - 4));
            }
            return Math.max(conf.getMinColumnWidth(), w);
        }

        String headerText = getColumnHeaderText(column, conf);
        int maxLen = headerText.length();

        for (int i = 0; i < rowsToDisplay; i++) {
            Row row = this.dataFrame.getRow(i);
            Object value = row != null ? row.get(column.columnName()) : null;
            String text = value == null ? conf.getStringNull() : value.toString();
            if (text.length() > maxLen) {
                maxLen = text.length();
            }
        }

        // Inner content width + 2 for 1 space padding on left and right
        int width = maxLen + 2;
        width = Math.max(width, conf.getMinColumnWidth());
        width = Math.min(width, conf.getMaxColumnWidth());
        if (conf.getTerminalWidth() > 0) {
            width = Math.min(width, Math.max(conf.getMinColumnWidth(), conf.getTerminalWidth() - 4));
        }
        return width;
    }

    private List<DisplayColumn> selectVisibleColumns(List<Column> headers, int rowsToDisplay, PrinterConfiguration conf) {
        int n = headers.size();
        List<DisplayColumn> all = new ArrayList<>(n);
        for (Column header : headers) {
            int w = computeColumnWidth(header, rowsToDisplay, conf);
            all.add(new DisplayColumn(header.columnName(), getColumnHeaderText(header, conf), w, false));
        }

        int terminalWidth = conf.getTerminalWidth();
        int maxCols = conf.getMaxColumns() > 0 ? conf.getMaxColumns() : n;

        int totalWidthAll = computeTableWidth(all);
        if ((terminalWidth <= 0 || totalWidthAll <= terminalWidth) && n <= maxCols) {
            return all;
        }

        int ellipsisWidth = Math.max(5, conf.getMinColumnWidth());
        DisplayColumn ellipsisCol = new DisplayColumn("...", "...", ellipsisWidth, true);

        if (n <= 1) {
            return all;
        }

        List<DisplayColumn> candidate = new ArrayList<>();
        candidate.add(all.get(0));
        candidate.add(ellipsisCol);
        candidate.add(all.get(n - 1));

        int maxWidth = terminalWidth > 0 ? terminalWidth : Integer.MAX_VALUE;

        if (computeTableWidth(candidate) <= maxWidth && 3 <= maxCols) {
            int leftCount = 1;
            int rightCount = 1;

            while (leftCount + rightCount < n) {
                if (leftCount <= rightCount) {
                    List<DisplayColumn> nextAttempt = buildSelection(all, leftCount + 1, rightCount, ellipsisCol);
                    if (computeTableWidth(nextAttempt) <= maxWidth && nextAttempt.size() <= maxCols + 1) {
                        leftCount++;
                    } else {
                        break;
                    }
                } else {
                    List<DisplayColumn> nextAttempt = buildSelection(all, leftCount, rightCount + 1, ellipsisCol);
                    if (computeTableWidth(nextAttempt) <= maxWidth && nextAttempt.size() <= maxCols + 1) {
                        rightCount++;
                    } else {
                        break;
                    }
                }
            }

            return buildSelection(all, leftCount, rightCount, ellipsisCol);
        }

        // Narrow terminal: fit left columns plus ellipsis, or at least 1 column
        List<DisplayColumn> leftOnly = new ArrayList<>();
        leftOnly.add(all.get(0));
        for (int i = 1; i < n; i++) {
            List<DisplayColumn> attempt = new ArrayList<>(leftOnly);
            attempt.add(ellipsisCol);
            if (terminalWidth > 0 && computeTableWidth(attempt) > terminalWidth) {
                break;
            }
            if (attempt.size() > maxCols + 1) {
                break;
            }
            leftOnly.add(all.get(i));
        }

        if (leftOnly.size() < n) {
            leftOnly.add(ellipsisCol);
        }
        return leftOnly;
    }

    private List<DisplayColumn> buildSelection(List<DisplayColumn> all, int leftCount, int rightCount, DisplayColumn ellipsis) {
        int n = all.size();
        if (leftCount + rightCount >= n) {
            return new ArrayList<>(all);
        }
        List<DisplayColumn> result = new ArrayList<>(leftCount + rightCount + 1);
        for (int i = 0; i < leftCount; i++) {
            result.add(all.get(i));
        }
        result.add(ellipsis);
        for (int i = n - rightCount; i < n; i++) {
            result.add(all.get(i));
        }
        return result;
    }

    private int computeTableWidth(List<DisplayColumn> columns) {
        if (columns.isEmpty()) {
            return 0;
        }
        int total = 1; // initial border char
        for (DisplayColumn col : columns) {
            total += col.width + 1; // dashes + separator border
        }
        return total;
    }

    private String buildTopBorder(List<DisplayColumn> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append('\u250C');
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append('\u252C');
            }
            sb.append(String.valueOf('\u2500').repeat(Math.max(1, columns.get(i).width)));
        }
        sb.append('\u2510');
        return sb.toString();
    }

    private String buildMiddleBorder(List<DisplayColumn> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append('\u251C');
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append('\u253C');
            }
            sb.append(String.valueOf('\u2500').repeat(Math.max(1, columns.get(i).width)));
        }
        sb.append('\u2524');
        return sb.toString();
    }

    private String buildBottomBorder(List<DisplayColumn> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append('\u2514');
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append('\u2534');
            }
            sb.append(String.valueOf('\u2500').repeat(Math.max(1, columns.get(i).width)));
        }
        sb.append('\u2518');
        return sb.toString();
    }

    private String buildHeaderRow(List<DisplayColumn> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append('\u2502');
        for (DisplayColumn col : columns) {
            sb.append(formatCell(col.headerText, col.width));
            sb.append('\u2502');
        }
        return sb.toString();
    }

    private String buildDataRow(List<DisplayColumn> columns, Row row, PrinterConfiguration conf) {
        StringBuilder sb = new StringBuilder();
        sb.append('\u2502');
        for (DisplayColumn col : columns) {
            String cellText;
            if (col.isEllipsis) {
                cellText = "...";
            } else {
                Object val = row != null ? row.get(col.columnName) : null;
                cellText = val == null ? conf.getStringNull() : val.toString();
            }
            sb.append(formatCell(cellText, col.width));
            sb.append('\u2502');
        }
        return sb.toString();
    }

    private String formatCell(String rawText, int columnWidth) {
        String text = rawText != null ? rawText : "";
        int available = Math.max(1, columnWidth - 2);

        if (text.length() > available) {
            if (available >= 4) {
                text = text.substring(0, available - 3) + "...";
            } else {
                text = text.substring(0, available);
            }
        }

        int spaces = Math.max(0, available - text.length());
        return " " + text + " ".repeat(spaces) + " ";
    }

    private String buildDescription(int totalRows, int displayedRows, int totalCols, int displayedCols,
                                    boolean hasTruncatedColumns, PrinterConfiguration conf) {
        if (conf.getDescription() != null) {
            return conf.getDescription();
        }

        StringBuilder sb = new StringBuilder();
        if (totalRows == 0) {
            sb.append("Showing 0 registers");
        } else if (displayedRows < totalRows) {
            sb.append("Showing only the first ").append(displayedRows).append(" registers");
        } else {
            sb.append("Showing ").append(totalRows).append(" registers");
        }

        if (hasTruncatedColumns) {
            sb.append(" (").append(displayedCols).append(" of ").append(totalCols).append(" columns)");
        }
        return sb.toString();
    }
}
