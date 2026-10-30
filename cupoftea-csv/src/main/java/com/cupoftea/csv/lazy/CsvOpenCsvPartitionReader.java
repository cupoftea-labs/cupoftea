package com.cupoftea.csv.lazy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.cupoftea.CupOfTea;
import com.cupoftea.importers.configurations.CSVImporterConfiguration;
import com.cupoftea.lazy.PartitionReader;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.Row;
import com.cupoftea.table.Schema;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

/**
 * Fallback streaming partition reader using OpenCSV for complex quoting or non-standard encodings.
 */
public class CsvOpenCsvPartitionReader implements PartitionReader {

    private final CSVReader reader;
    private final BufferedReader bufferedReader;
    private final String[] headerNames;
    private final Class<?>[] colTypes;
    private final int batchSize;
    private final Schema schema;

    public CsvOpenCsvPartitionReader(File file, CSVImporterConfiguration config, int batchSize) throws IOException {
        this.batchSize = batchSize > 0 ? batchSize : 65_536;

        CSVParser parser = new CSVParserBuilder()
                .withSeparator(config.getDelimiter())
                .withQuoteChar(config.getQuoteChar())
                .withEscapeChar(config.getEscapeChar())
                .build();

        this.bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), config.getCharset()));
        this.reader = new CSVReaderBuilder(bufferedReader).withCSVParser(parser).build();

        String[] firstLine = null;
        try {
            firstLine = reader.readNext();
        } catch (Exception e) {
            throw new IOException("Failed reading CSV header", e);
        }

        if (firstLine == null) {
            this.headerNames = new String[0];
            this.colTypes = new Class<?>[0];
            this.schema = Schema.empty();
            return;
        }

        int colCount = firstLine.length;
        this.headerNames = new String[colCount];
        this.colTypes = new Class<?>[colCount];

        Schema explicitSchema = config.getSchema();

        if (config.isHasHeaders()) {
            for (int i = 0; i < colCount; i++) {
                headerNames[i] = firstLine[i] != null ? firstLine[i].trim() : String.valueOf(i);
                if (explicitSchema != null && explicitSchema.hasColumn(headerNames[i])) {
                    colTypes[i] = explicitSchema.getType(headerNames[i]);
                } else {
                    colTypes[i] = String.class;
                }
            }
        } else {
            for (int i = 0; i < colCount; i++) {
                headerNames[i] = String.valueOf(i);
                if (explicitSchema != null && i < explicitSchema.size()) {
                    colTypes[i] = explicitSchema.getColumn(i).columnType();
                } else {
                    colTypes[i] = String.class;
                }
            }
        }

        Schema.Builder sb = Schema.builder();
        for (int i = 0; i < colCount; i++) {
            sb.add(headerNames[i], colTypes[i] != null ? colTypes[i] : String.class);
        }
        this.schema = sb.build();
    }

    @Override
    public CupOfTea nextBatch() throws IOException {
        List<Row> rows = new ArrayList<>(batchSize);
        int count = 0;

        try {
            String[] line;
            while (count < batchSize && (line = reader.readNext()) != null) {
                if (line.length == 0 || (line.length == 1 && line[0].isEmpty())) {
                    continue;
                }
                Row row = new Row();
                for (int i = 0; i < Math.min(line.length, headerNames.length); i++) {
                    row.put(headerNames[i], line[i]);
                }
                rows.add(row);
                count++;
            }
        } catch (Exception e) {
            throw new IOException("Error reading CSV rows", e);
        }

        if (rows.isEmpty()) {
            return null;
        }

        DataFrame df = new DataFrame(schema);
        for (Row r : rows) {
            df.addRow(r);
        }
        return new CupOfTea(df);
    }

    @Override
    public void close() throws IOException {
        try {
            reader.close();
        } finally {
            bufferedReader.close();
        }
    }
}
