package com.CupOfTea.importers.configurations;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.opencsv.ICSVParser;

import lombok.Getter;

@Getter
public class CSVImporterConfiguration {

    private boolean hasHeaders = true;
    private char delimiter = ICSVParser.DEFAULT_SEPARATOR;
    private char quoteChar = ICSVParser.DEFAULT_QUOTE_CHARACTER;
    private char escapeChar = ICSVParser.DEFAULT_ESCAPE_CHARACTER;
    private Charset charset = StandardCharsets.UTF_8;
    private boolean inferNumericTypes = false;

    public boolean isHasHeaders() {
        return this.hasHeaders;
    }

    public char getDelimiter() {
        return this.delimiter;
    }

    public char getQuoteChar() {
        return this.quoteChar;
    }

    public char getEscapeChar() {
        return this.escapeChar;
    }

    public Charset getCharset() {
        return this.charset;
    }

    public boolean isInferNumericTypes() {
        return this.inferNumericTypes;
    }

    public CSVImporterConfiguration setHasHeaders(boolean hasHeaders) {
        this.hasHeaders = hasHeaders;
        return this;
    }

    public CSVImporterConfiguration setDelimiter(char delimiter) {
        this.delimiter = delimiter;
        return this;
    }

    public CSVImporterConfiguration setDelimiter(String delimiter) {
        if (delimiter == null || delimiter.length() != 1) {
            throw new IllegalArgumentException("Delimiter must be a single character");
        }
        this.delimiter = delimiter.charAt(0);
        return this;
    }

    public CSVImporterConfiguration setQuoteChar(char quoteChar) {
        this.quoteChar = quoteChar;
        return this;
    }

    public CSVImporterConfiguration setQuoteString(String quoteString) {
        if (quoteString == null || quoteString.isEmpty()) {
            this.quoteChar = ICSVParser.NULL_CHARACTER;
        } else if (quoteString.length() == 1) {
            this.quoteChar = quoteString.charAt(0);
        } else {
            throw new IllegalArgumentException("Quote string must be empty or a single character");
        }
        return this;
    }

    public CSVImporterConfiguration setEscapeChar(char escapeChar) {
        this.escapeChar = escapeChar;
        return this;
    }

    public CSVImporterConfiguration setEscapeString(String escapeString) {
        if (escapeString == null || escapeString.isEmpty()) {
            this.escapeChar = ICSVParser.NULL_CHARACTER;
        } else if (escapeString.length() == 1) {
            this.escapeChar = escapeString.charAt(0);
        } else {
            throw new IllegalArgumentException("Escape string must be empty or a single character");
        }
        return this;
    }

    public CSVImporterConfiguration setCharset(Charset charset) {
        if (charset == null) {
            throw new IllegalArgumentException("Charset can not be null");
        }
        this.charset = charset;
        return this;
    }

    public CSVImporterConfiguration setCharset(String charsetName) {
        if (charsetName == null) {
            throw new IllegalArgumentException("Charset can not be null");
        }
        this.charset = Charset.forName(charsetName);
        return this;
    }

    public CSVImporterConfiguration setInferNumericTypes(boolean inferNumericTypes) {
        this.inferNumericTypes = inferNumericTypes;
        return this;
    }

}
