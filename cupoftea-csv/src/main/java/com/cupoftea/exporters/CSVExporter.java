package com.cupoftea.exporters;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.configurations.CSVExporterConfiguration;
import static com.cupoftea.exporters.configurations.CSVExporterConfiguration.HeadersPlacement.BOTTOM;
import static com.cupoftea.exporters.configurations.CSVExporterConfiguration.HeadersPlacement.NO_HEADER;
import static com.cupoftea.exporters.configurations.CSVExporterConfiguration.HeadersPlacement.TOP;
import com.cupoftea.table.Column;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

public class CSVExporter {

    private final CupOfTea dataFrame;
    private CSVExporterConfiguration configuration;

    public CSVExporter(final CupOfTea dataFrame) {
        this.dataFrame = dataFrame;
        this.configuration = new CSVExporterConfiguration();
    }

    /**
     * Create a CSV file from the data frame.
     * 
     * @param file path to your CSV file. Can't be null.
     */
    public void exportToCsv(File file) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        final int bufferSize = 64 * 1024;
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8), bufferSize)) {
            exportToWriter(writer);
        } catch (IOException e) {
            throw new ExportException("Failed to export to CSV file: " + file.getPath(), e);
        } catch (Exception e) {
            if (e instanceof ExportException) {
                throw (ExportException) e;
            }
            throw new ExportException("Failed to export to CSV file: " + file.getPath(), e);
        }
    }

    /**
     * Create a CSV file from the data frame with specific configuration.
     * 
     * @param file path to your CSV file. Can't be null.
     * @param configuration CSV export configuration options.
     */
    public void exportToCsv(File file, CSVExporterConfiguration configuration) {
        this.configuration = configuration;
        this.exportToCsv(file);
    }

    private void exportToWriter(Writer writer) throws IOException {
        IDataFrame df = this.dataFrame.getDataFrame();
        List<Column> headers = this.dataFrame.getHeaders();
        int numCols = headers.size();
        int rowCount = this.dataFrame.size();

        String delimiter = this.configuration.getDelimiter();
        String lineEnd = this.configuration.getLineEndString();
        String stringNull = this.configuration.getStringNull();
        String quoteStr = this.configuration.getQuoteString();
        var quoteBehaviour = this.configuration.getQuoteBehaviour();

        // Precompute header string if needed
        String headerLine = null;
        if (this.configuration.getHeadersPlacement() != NO_HEADER && numCols > 0) {
            StringBuilder sbHeader = new StringBuilder();
            for (int c = 0; c < numCols; c++) {
                if (c > 0) {
                    sbHeader.append(delimiter);
                }
                Column col = headers.get(c);
                sbHeader.append(prepareFieldValue(col.columnName(), col.columnType(), true));
            }
            sbHeader.append(lineEnd);
            headerLine = sbHeader.toString();
        }

        if (this.configuration.getHeadersPlacement() == TOP && headerLine != null) {
            writer.write(headerLine);
        }

        // Fast columnar access setup
        ColumnVector[] vectors = new ColumnVector[numCols];
        boolean[] isNumCol = new boolean[numCols];
        boolean[] isStrCol = new boolean[numCols];

        for (int c = 0; c < numCols; c++) {
            Column col = headers.get(c);
            ColumnVector vec = df != null ? df.getColumnVector(col.columnName()) : null;
            vectors[c] = vec;
            Class<?> type = col.columnType();
            isNumCol[c] = type != null && Number.class.isAssignableFrom(type);
            isStrCol[c] = type == String.class;
        }

        // Reusable row buffer
        StringBuilder lineBuf = new StringBuilder(Math.max(128, numCols * 24));

        for (int r = 0; r < rowCount; r++) {
            lineBuf.setLength(0);
            for (int c = 0; c < numCols; c++) {
                if (c > 0) {
                    lineBuf.append(delimiter);
                }

                ColumnVector vec = vectors[c];
                boolean isNull = (vec != null) ? vec.isNull(r) : false;

                if (isNull) {
                    boolean quote;
                    switch (quoteBehaviour) {
                        case ALL, HEADERS_ONLY: // For row data, HEADERS_ONLY quote is false
                            quote = quoteBehaviour == CSVExporterConfiguration.QuoteBehaviour.ALL;
                            break;
                        case ALL_BUT_NUMBERS:
                            quote = !isNumCol[c];
                            break;
                        case ALL_BUT_NULL, STRING_ONLY, NONE:
                        default:
                            quote = false;
                            break;
                    }
                    if (quote) {
                        lineBuf.append(quoteStr).append(stringNull).append(quoteStr);
                    } else {
                        lineBuf.append(stringNull);
                    }
                } else if (vec instanceof IntColumnVector intVec) {
                    boolean quote = (quoteBehaviour == CSVExporterConfiguration.QuoteBehaviour.ALL);
                    if (quote) {
                        lineBuf.append(quoteStr).append(intVec.getInt(r)).append(quoteStr);
                    } else {
                        lineBuf.append(intVec.getInt(r));
                    }
                } else if (vec instanceof LongColumnVector longVec) {
                    boolean quote = (quoteBehaviour == CSVExporterConfiguration.QuoteBehaviour.ALL);
                    if (quote) {
                        lineBuf.append(quoteStr).append(longVec.getLong(r)).append(quoteStr);
                    } else {
                        lineBuf.append(longVec.getLong(r));
                    }
                } else if (vec instanceof DoubleColumnVector dblVec) {
                    boolean quote = (quoteBehaviour == CSVExporterConfiguration.QuoteBehaviour.ALL);
                    if (quote) {
                        lineBuf.append(quoteStr).append(dblVec.getDouble(r)).append(quoteStr);
                    } else {
                        lineBuf.append(dblVec.getDouble(r));
                    }
                } else if (vec instanceof FloatColumnVector fltVec) {
                    boolean quote = (quoteBehaviour == CSVExporterConfiguration.QuoteBehaviour.ALL);
                    if (quote) {
                        lineBuf.append(quoteStr).append(fltVec.getFloat(r)).append(quoteStr);
                    } else {
                        lineBuf.append(fltVec.getFloat(r));
                    }
                } else if (vec instanceof BooleanColumnVector boolVec) {
                    boolean quote;
                    switch (quoteBehaviour) {
                        case ALL, ALL_BUT_NUMBERS, ALL_BUT_NULL -> quote = true;
                        default -> quote = false;
                    }
                    if (quote) {
                        lineBuf.append(quoteStr).append(boolVec.getBoolean(r)).append(quoteStr);
                    } else {
                        lineBuf.append(boolVec.getBoolean(r));
                    }
                } else if (vec instanceof StringColumnVector strVec) {
                    String strVal = strVec.getString(r);
                    boolean quote;
                    switch (quoteBehaviour) {
                        case ALL, ALL_BUT_NUMBERS, ALL_BUT_NULL, STRING_ONLY -> quote = true;
                        default -> quote = false;
                    }
                    if (quote) {
                        lineBuf.append(quoteStr).append(strVal != null ? strVal : stringNull).append(quoteStr);
                    } else {
                        lineBuf.append(strVal != null ? strVal : stringNull);
                    }
                } else if (vec != null) {
                    Object obj = vec.getObject(r);
                    String val = (obj != null) ? obj.toString() : null;
                    boolean quote;
                    switch (quoteBehaviour) {
                        case ALL -> quote = true;
                        case ALL_BUT_NUMBERS -> quote = !isNumCol[c];
                        case ALL_BUT_NULL -> quote = val != null;
                        case STRING_ONLY -> quote = isStrCol[c];
                        default -> quote = false;
                    }
                    if (quote) {
                        lineBuf.append(quoteStr).append(val != null ? val : stringNull).append(quoteStr);
                    } else {
                        lineBuf.append(val != null ? val : stringNull);
                    }
                } else {
                    // Fallback to row object if no column vector
                    Object obj = this.dataFrame.getRow(r).get(headers.get(c).columnName());
                    String val = (obj != null) ? obj.toString() : null;
                    lineBuf.append(prepareFieldValue(val, headers.get(c).columnType(), false));
                }
            }
            lineBuf.append(lineEnd);
            writer.write(lineBuf.toString());
        }

        if (this.configuration.getHeadersPlacement() == BOTTOM && headerLine != null) {
            writer.write(headerLine);
        }
    }

    private String prepareFieldValue(String fieldValue, Class<?> columnType, Boolean isHeader) {
        String quote;

        switch (this.configuration.getQuoteBehaviour()) {
            case ALL -> quote = this.configuration.getQuoteString();
            case ALL_BUT_NUMBERS -> quote = (columnType != null && Number.class.isAssignableFrom(columnType) && !isHeader ? "" : this.configuration.getQuoteString());
            case ALL_BUT_NULL -> quote = (fieldValue == null ? "" : this.configuration.getQuoteString());
            case STRING_ONLY -> quote = columnType == String.class ? this.configuration.getQuoteString() : "";
            case HEADERS_ONLY -> quote = (isHeader ? this.configuration.getQuoteString() : "");
            case NONE -> quote = "";
            default -> quote = "";
        }

        return quote + (fieldValue == null ? this.configuration.getStringNull() : fieldValue) + quote;
    }
}
