package com.cupoftea.exporters;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Collectors;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.configurations.CSVExporterConfiguration;
import static com.cupoftea.exporters.configurations.CSVExporterConfiguration.HeadersPlacement.*;
import com.cupoftea.table.Row;

public class CSVExporter {

    private final CupOfTea dataFrame;
    private CSVExporterConfiguration configuration;

    public CSVExporter(final CupOfTea dataFrame){
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

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            String header = null;
            
            if(this.configuration.getHeadersPlacement() != NO_HEADER) header = String.join(
                this.configuration.getDelimiter(),
                this.dataFrame.getHeaders().stream()
                    .map(a ->  this.prepareFieldValue(a.columnName(), a.columnType(), true))
                    .toList()
                ) + this.configuration.getLineEndString()
            ;

            if (this.configuration.getHeadersPlacement() == TOP) writer.write(header);
            
            for (Row data : this.dataFrame.getRows()){
                ArrayList<String> dataArr = (ArrayList<String>) this.dataFrame.getHeaders().stream()
                    .map(i -> this.prepareFieldValue(data.get(i.columnName()) != null ? data.get(i.columnName()).toString() : null, i.columnType(), false))
                    .collect(Collectors.toList())
                    ;
                
                String dataStr = String.join(this.configuration.getDelimiter(), dataArr.toArray(String[]::new)) + this.configuration.getLineEndString();
                writer.write(dataStr);
            }

            if (this.configuration.getHeadersPlacement() == BOTTOM) writer.write(header);
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
     * Create a CSV file from the data frame.
     * 
     * @param file path to your CSV file. Can't be null.
     */
    public void exportToCsv(File file, CSVExporterConfiguration configuration) {
        this.configuration = configuration;
        this.exportToCsv(file);
    }

    private String prepareFieldValue(String fieldValue, Class<?> columnType, Boolean isHeader){
        String quote;

        switch(this.configuration.getQuoteBehaviour()) {
            case ALL -> quote = this.configuration.getQuoteString();
            case ALL_BUT_NUMBERS -> quote = (columnType != null && Number.class.isAssignableFrom(columnType) && !isHeader ? "" : this.configuration.getQuoteString());
            case ALL_BUT_NULL -> quote = (fieldValue == null ?  "" : this.configuration.getQuoteString());
            case STRING_ONLY -> quote = columnType == String.class ? this.configuration.getQuoteString() : "";
            case HEADERS_ONLY -> quote = (isHeader ? this.configuration.getQuoteString() : "");
            case NONE -> quote = "";
            default -> quote = "";
        }

        String field = quote + (fieldValue == null ? this.configuration.getStringNull() : fieldValue) + quote;

        return field;
    }

}
