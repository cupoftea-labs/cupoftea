package com.CupOfTea.exporters.configurations;

import static com.CupOfTea.exporters.configurations.CSVExporterConfiguration.QuoteBehaviour.ALL;

import lombok.Getter;

@Getter
public class CSVExporterConfiguration {

    public enum QuoteBehaviour {

        ALL,
        ALL_BUT_NUMBERS; 

    }


    private String separator = ",";
    private QuoteBehaviour quoteBehaviour = ALL;
    private String quoteString = null;
    private String lineEndString = "\n";
    private String escapeString = "\\";
    

    public CSVExporterConfiguration setSeparator (String separator) {

        this.separator = (separator == null ? "" : separator);

        return this;
    }

    public CSVExporterConfiguration setFieldWrapperBehaviour (QuoteBehaviour quoteBehaviour) {

        this.quoteBehaviour = quoteBehaviour;

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

}
