package com.cupoftea.exporters.configurations;

import static com.cupoftea.exporters.configurations.CSVExporterConfiguration.HeadersPlacement.TOP;
import static com.cupoftea.exporters.configurations.CSVExporterConfiguration.QuoteBehaviour.ALL;

import lombok.Getter;

@Getter
public class CSVExporterConfiguration {

    public enum QuoteBehaviour {
        NONE,
        ALL,
        ALL_BUT_NUMBERS,
        ALL_BUT_NULL,
        STRING_ONLY,
        HEADERS_ONLY,
        ; 
    }

    public enum HeadersPlacement {
        TOP,
        BOTTOM,
        NO_HEADER,
        ;
    }

    private String delimiter = ",";
    private QuoteBehaviour quoteBehaviour = ALL;
    private HeadersPlacement headersPlacement = TOP;
    private String quoteString = "";
    private String lineEndString = "\n";
    private String escapeString = "\\";
    private String stringNull = "null";

    public String getDelimiter() {
        return delimiter;
    }

    public QuoteBehaviour getQuoteBehaviour() {
        return quoteBehaviour;
    }

    public HeadersPlacement getHeadersPlacement() {
        return headersPlacement;
    }

    public String getQuoteString() {
        return quoteString;
    }

    public String getLineEndString() {
        return lineEndString;
    }

    public String getEscapeString() {
        return escapeString;
    }

    public String getStringNull() {
        return stringNull;
    }


    public CSVExporterConfiguration setDelimiter (String delimiter) {

        this.delimiter = (delimiter == null ? "" : delimiter);

        return this;
    }

    public CSVExporterConfiguration setQuoteBehaviour (QuoteBehaviour quoteBehaviour) {

        this.quoteBehaviour = quoteBehaviour;

        return this;

    }

    public CSVExporterConfiguration setHeadersPlacement (HeadersPlacement headersPlacement) {
        this.headersPlacement = headersPlacement;
        
        return this;
    }

    public CSVExporterConfiguration setQuoteString (String quoteString) {

        this.quoteString = (quoteString == null ? "" : quoteString);

        return this;

    }

    public CSVExporterConfiguration setLineEndString (String lineEndString) {

        this.lineEndString = (lineEndString == null ? "" : lineEndString);

        return this;

    }

    public CSVExporterConfiguration setEscapeString (String escapeString) {

        this.escapeString = (escapeString == null ? "" : escapeString);

        return this; 

    }

    public CSVExporterConfiguration setStringNull(String stringNull) {
        this.stringNull = stringNull;

        return this;
    }

}
