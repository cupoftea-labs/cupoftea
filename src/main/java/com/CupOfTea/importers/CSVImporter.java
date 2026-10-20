package com.CupOfTea.importers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.exceptions.CsvParseException;
import com.CupOfTea.exceptions.ImportException;
import com.CupOfTea.importers.configurations.CSVImporterConfiguration;
import com.CupOfTea.importers.interfaces.ICSVImporter;
import com.CupOfTea.table.Column;
import com.CupOfTea.table.Schema;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

public class CSVImporter implements ICSVImporter {

    private static final Pattern DOUBLE_PATTERN = Pattern.compile("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?$");

    private final CupOfTea dataFrame;
    private CSVImporterConfiguration configuration;

    public CSVImporter(final CupOfTea dataFrame) {
        this(dataFrame, new CSVImporterConfiguration());
    }

    public CSVImporter(final CupOfTea dataFrame, final CSVImporterConfiguration configuration) {
        if (dataFrame == null) {
            throw new IllegalArgumentException("DataFrame can not be null");
        }
        if (configuration == null) {
            throw new IllegalArgumentException("Configuration can not be null");
        }
        this.dataFrame = dataFrame;
        this.configuration = configuration;
    }

    /**
     * Dumps the data from the CSV file into a CupOfTea data frame using the current configuration.
     * By default, values are imported as {@link String} (unless {@code inferNumericTypes} or
     * {@code inferTypes} is enabled on {@link CSVImporterConfiguration}) and read using UTF-8 encoding.
     * Any previous data in the data frame will be lost!
     *
     * All the lines of the CSV must have the same amount of columns, otherwise an {@link IllegalArgumentException} will be thrown.
     *
     * @param file path to the CSV file. Can't be null.
     */
    @Override
    public void importFromCsv(File file) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }

        this.dataFrame.getRows().clear();
        this.dataFrame.getHeaders().clear();

        CSVParser parser = new CSVParserBuilder()
            .withSeparator(this.configuration.getDelimiter())
            .withQuoteChar(this.configuration.getQuoteChar())
            .withEscapeChar(this.configuration.getEscapeChar())
            .build();

        List<String[]> rawRows = new ArrayList<>();
        String[] headerNames;
        int columnCount;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), this.configuration.getCharset()));
             CSVReader reader = new CSVReaderBuilder(br).withCSVParser(parser).build()) {

            String[] firstLine = reader.readNext();
            if (firstLine == null) {
                return;
            }

            if (firstLine.length > 0 && firstLine[0] != null && firstLine[0].startsWith("\uFEFF")) {
                firstLine[0] = firstLine[0].substring(1);
            }

            columnCount = firstLine.length;
            if (this.configuration.isHasHeaders()) {
                headerNames = firstLine;
            } else {
                headerNames = new String[columnCount];
                for (int i = 0; i < columnCount; i++) {
                    headerNames[i] = String.valueOf(i);
                }
                rawRows.add(firstLine);
            }

            String[] line;
            while ((line = reader.readNext()) != null) {
                if (line.length != columnCount) {
                    throw new CsvParseException(
                        "Row length mismatch: expected " + columnCount + " columns, but got " + line.length
                    );
                }
                rawRows.add(line);
            }
        } catch (IOException e) {
            throw new CsvParseException("Failed to read CSV file: " + file.getPath(), e);
        } catch (Exception e) {
            if (e instanceof CsvParseException) {
                throw (CsvParseException) e;
            }
            if (e instanceof ImportException) {
                throw (ImportException) e;
            }
            throw new CsvParseException("Failed to parse CSV file: " + file.getPath(), e);
        }

        Schema explicitSchema = this.configuration.getSchema() != null
                ? this.configuration.getSchema()
                : this.dataFrame.getSchema();

        Class<?>[] columnTypes = new Class<?>[columnCount];
        for (int i = 0; i < columnCount; i++) {
            String colName = headerNames[i];
            Class<?> schemaType = null;
            if (explicitSchema != null && explicitSchema.hasColumn(colName)) {
                schemaType = explicitSchema.getType(colName);
            } else if (explicitSchema != null && i < explicitSchema.size() && !this.configuration.isHasHeaders()) {
                schemaType = explicitSchema.getColumn(i).columnType();
            }

            if (schemaType != null) {
                columnTypes[i] = schemaType;
            } else if (this.configuration.isInferTypes()) {
                columnTypes[i] = inferFullColumnType(rawRows, i, this.configuration.getNaStrings());
            } else if (this.configuration.isInferNumericTypes()) {
                columnTypes[i] = inferNumericColumnType(rawRows, i, this.configuration.getNaStrings());
            } else {
                columnTypes[i] = String.class;
            }
        }

        ArrayList<Column> columns = new ArrayList<>(columnCount);
        for (int i = 0; i < columnCount; i++) {
            columns.add(new Column(headerNames[i], columnTypes[i]));
        }
        if (!columns.isEmpty()) {
            this.dataFrame.setHeaders(columns);
        }

        for (String[] rawRow : rawRows) {
            HashMap<String, Object> row = new HashMap<>();
            for (int i = 0; i < columnCount; i++) {
                row.put(headerNames[i], convertValue(rawRow[i], columnTypes[i], this.configuration));
            }
            this.dataFrame.addRow(row);
        }
    }

    /**
     * Dumps the data from the CSV file into a CupOfTea data frame.
     * Any previous data in the data frame will be lost!
     * 
     * All the lines of the CSV must have the same amount of columns, otherwise an {@link IllegalArgumentException} will be thrown.
     * 
     * @param file path to the CSV file. Can't be null.
     * @param hasHeaders if true, the first line of the CSV will be used for the headers. If false, auto-generated 0-based sequential column names ("0", "1", ...) will be used.
     */
    @Override
    public void importFromCsv(File file, boolean hasHeaders) {
        this.configuration.setHasHeaders(hasHeaders);
        this.importFromCsv(file);
    }

    @Override
    public void importFromCsv(File file, boolean hasHeaders, Schema schema) {
        this.configuration.setHasHeaders(hasHeaders);
        this.configuration.setSchema(schema);
        this.importFromCsv(file);
    }

    /**
     * Dumps the data from the CSV file into a CupOfTea data frame using the provided configuration.
     * Any previous data in the data frame will be lost!
     *
     * All the lines of the CSV must have the same amount of columns, otherwise an {@link IllegalArgumentException} will be thrown.
     *
     * @param file path to the CSV file. Can't be null.
     * @param configuration configuration for CSV parsing (delimiter, quote, escape, charset, headers, numeric type inference).
     */
    @Override
    public void importFromCsv(File file, CSVImporterConfiguration configuration) {
        if (configuration == null) {
            throw new ImportException("Configuration cannot be null");
        }
        this.configuration = configuration;
        this.importFromCsv(file);
    }

    private static boolean isMissingValue(String raw, Set<String> naStrings) {
        if (raw == null) {
            return true;
        }
        String trimmed = raw.trim();
        return naStrings != null && naStrings.contains(trimmed);
    }

    private static Class<?> inferNumericColumnType(List<String[]> rows, int columnIndex, Set<String> naStrings) {
        if (rows.isEmpty()) {
            return String.class;
        }

        boolean hasNonEmptyValue = false;
        boolean allInteger = true;
        boolean allDouble = true;

        for (String[] row : rows) {
            String raw = row[columnIndex];
            if (raw == null || raw.trim().isEmpty() || isMissingValue(raw, naStrings)) {
                continue;
            }
            hasNonEmptyValue = true;
            String trimmed = raw.trim();

            if (allInteger && !isInteger(trimmed)) {
                allInteger = false;
            }
            if (!isDouble(trimmed)) {
                allDouble = false;
                break;
            }
        }

        if (!hasNonEmptyValue) {
            return String.class;
        }
        if (allInteger) {
            return Integer.class;
        }
        if (allDouble) {
            return Double.class;
        }
        return String.class;
    }

    private static Class<?> inferFullColumnType(List<String[]> rows, int columnIndex, Set<String> naStrings) {
        if (rows.isEmpty()) {
            return String.class;
        }

        boolean hasNonEmptyValue = false;
        boolean allBoolean = true;
        boolean allInteger = true;
        boolean allLong = true;
        boolean allDouble = true;
        boolean allLocalDate = true;
        boolean allLocalDateTime = true;
        boolean allInstant = true;

        for (String[] row : rows) {
            String raw = row[columnIndex];
            if (raw == null || raw.trim().isEmpty() || isMissingValue(raw, naStrings)) {
                continue;
            }
            hasNonEmptyValue = true;
            String trimmed = raw.trim();

            if (allBoolean && !isBoolean(trimmed)) allBoolean = false;
            if (allInteger && !isInteger(trimmed)) allInteger = false;
            if (allLong && !isLong(trimmed)) allLong = false;
            if (allDouble && !isDouble(trimmed)) allDouble = false;
            if (allLocalDate && !isLocalDate(trimmed)) allLocalDate = false;
            if (allLocalDateTime && !isLocalDateTime(trimmed)) allLocalDateTime = false;
            if (allInstant && !isInstant(trimmed)) allInstant = false;
        }

        if (!hasNonEmptyValue) {
            return String.class;
        }
        if (allBoolean) return Boolean.class;
        if (allInteger) return Integer.class;
        if (allLong) return Long.class;
        if (allDouble) return Double.class;
        if (allLocalDate) return LocalDate.class;
        if (allLocalDateTime) return LocalDateTime.class;
        if (allInstant) return Instant.class;

        return String.class;
    }

    private static boolean isBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isLong(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isDouble(String value) {
        if (!DOUBLE_PATTERN.matcher(value).matches()) {
            return false;
        }
        try {
            double parsed = Double.parseDouble(value);
            return !Double.isNaN(parsed) && !Double.isInfinite(parsed);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isLocalDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean isLocalDateTime(String value) {
        try {
            LocalDateTime.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean isInstant(String value) {
        try {
            Instant.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static Object convertValue(String rawValue, Class<?> targetType, CSVImporterConfiguration config) {
        if (rawValue == null) {
            return null;
        }

        if (isMissingValue(rawValue, config.getNaStrings())) {
            return null;
        }

        if (targetType == String.class) {
            if (rawValue.isEmpty() && !config.isPreserveEmptyStrings()) {
                return null;
            }
            return rawValue;
        }

        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (targetType == Integer.class || targetType == int.class) {
            return Integer.valueOf(trimmed);
        }
        if (targetType == Long.class || targetType == long.class) {
            return Long.valueOf(trimmed);
        }
        if (targetType == Double.class || targetType == double.class) {
            return Double.valueOf(trimmed);
        }
        if (targetType == Float.class || targetType == float.class) {
            return Float.valueOf(trimmed);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.valueOf(trimmed);
        }
        if (targetType == BigDecimal.class) {
            return new BigDecimal(trimmed);
        }
        if (targetType == LocalDate.class) {
            return LocalDate.parse(trimmed);
        }
        if (targetType == LocalDateTime.class) {
            return LocalDateTime.parse(trimmed);
        }
        if (targetType == Instant.class) {
            return Instant.parse(trimmed);
        }

        return rawValue;
    }

}
