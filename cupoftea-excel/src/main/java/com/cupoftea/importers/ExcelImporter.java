package com.cupoftea.importers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import javax.xml.parsers.SAXParserFactory;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.configurations.ExcelImporterConfiguration;
import com.cupoftea.importers.interfaces.IExcelImporter;
import com.cupoftea.table.Column;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.NA;
import com.cupoftea.table.Schema;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.ObjectColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * Excel importer supporting both high-performance streaming SAX parsing for .xlsx/.xlsm
 * (populating columnar vectors directly with low memory overhead) and POI DOM parsing for legacy .xls files.
 */
public class ExcelImporter implements IExcelImporter {

    private final CupOfTea dataFrame;
    private ExcelImporterConfiguration configuration;

    public ExcelImporter(final CupOfTea dataFrame) {
        this(dataFrame, new ExcelImporterConfiguration());
    }

    public ExcelImporter(final CupOfTea dataFrame, final ExcelImporterConfiguration configuration) {
        if (dataFrame == null) {
            throw new IllegalArgumentException("DataFrame cannot be null");
        }
        if (configuration == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }
        this.dataFrame = dataFrame;
        this.configuration = configuration;
    }

    @Override
    public void importFromExcel(File file, boolean hasHeaders) {
        this.configuration.setHasHeaders(hasHeaders);
        this.importFromExcel(file, this.configuration);
    }

    @Override
    public void importFromExcel(File file, boolean hasHeaders, Schema schema) {
        this.configuration.setHasHeaders(hasHeaders);
        this.configuration.setSchema(schema);
        this.importFromExcel(file, this.configuration);
    }

    @Override
    public void importFromExcel(File file, ExcelImporterConfiguration config) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        if (config == null) {
            throw new ImportException("Configuration cannot be null");
        }
        this.configuration = config;

        String filename = file.getName();
        Optional<String> fileExtension = Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(filename.lastIndexOf(".") + 1));

        if (fileExtension.isEmpty()) {
            throw new ImportException("Not an Excel file: " + filename);
        }

        String ext = fileExtension.get().toLowerCase();
        if (!ext.equals("xls") && !ext.equals("xlsx") && !ext.equals("xlsm")) {
            throw new ImportException("Unsupported Excel file extension: " + ext);
        }

        this.dataFrame.clear();

        if (ext.equals("xlsx") || ext.equals("xlsm")) {
            importXlsxStreaming(file, config);
        } else {
            importLegacyXlsDom(file, config);
        }
    }

    /**
     * Streaming event-driven (SAX) importer for OOXML (.xlsx, .xlsm) workbooks.
     * Keeps heap memory bounded regardless of workbook row count by parsing cell events directly
     * into columnar vectors without creating POI Row/Cell objects.
     */
    private void importXlsxStreaming(File file, ExcelImporterConfiguration config) {
        try (OPCPackage pkg = OPCPackage.open(file, PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            StylesTable styles = reader.getStylesTable();

            XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) reader.getSheetsData();
            InputStream targetStream = null;
            int sheetIdx = 0;
            boolean found = false;

            while (iter.hasNext()) {
                InputStream is = iter.next();
                String sheetName = iter.getSheetName();
                if (config.getSheetName() != null) {
                    if (config.getSheetName().equals(sheetName)) {
                        targetStream = is;
                        found = true;
                        break;
                    }
                } else if (sheetIdx == config.getSheetIndex()) {
                    targetStream = is;
                    found = true;
                    break;
                }
                is.close();
                sheetIdx++;
            }

            if (!found || targetStream == null) {
                if (config.getSheetName() != null) {
                    throw new ImportException("Sheet not found: " + config.getSheetName());
                } else {
                    throw new ImportException("Sheet index out of bounds: " + config.getSheetIndex());
                }
            }

            try (InputStream sheetStream = targetStream) {
                parseSheetSax(sheetStream, styles, strings, config);
            }
        } catch (ImportException e) {
            throw e;
        } catch (Exception e) {
            throw new ImportException("Failed to read Excel file: " + file.getPath(), e);
        }
    }

    private void parseSheetSax(InputStream sheetStream, StylesTable styles, ReadOnlySharedStringsTable strings,
                               ExcelImporterConfiguration config) {
        final Schema explicitSchema = config.getSchema() != null ? config.getSchema() : this.dataFrame.getSchema();
        final boolean hasHeaders = config.isHasHeaders();

        class StreamingSheetHandler implements SheetContentsHandler {
            private int maxCol = -1;
            private int headerRowIdx = -1;
            private final List<String> headerNames = new ArrayList<>();
            private final List<String[]> sampleRows = new ArrayList<>();
            private boolean schemaFinalized = false;
            private List<ColumnVector> vectors = null;
            private int currentRowNum = -1;
            private String[] currentRowBuffer = null;

            @Override
            public void startRow(int rowNum) {
                this.currentRowNum = rowNum;
                this.currentRowBuffer = new String[Math.max(16, maxCol + 1)];
            }

            @Override
            public void cell(String cellReference, String formattedValue, XSSFComment comment) {
                int col = -1;
                if (cellReference != null) {
                    col = new CellReference(cellReference).getCol();
                }
                if (col < 0) {
                    return;
                }
                if (col > maxCol) {
                    maxCol = col;
                }
                if (col >= currentRowBuffer.length) {
                    String[] expanded = new String[Math.max(currentRowBuffer.length * 2, col + 1)];
                    System.arraycopy(currentRowBuffer, 0, expanded, 0, currentRowBuffer.length);
                    currentRowBuffer = expanded;
                }
                currentRowBuffer[col] = formattedValue;
            }

            @Override
            public void endRow(int rowNum) {
                if (hasHeaders && headerRowIdx == -1) {
                    // This is the header row
                    headerRowIdx = rowNum;
                    for (int c = 0; c <= maxCol; c++) {
                        String name = (c < currentRowBuffer.length && currentRowBuffer[c] != null)
                                ? currentRowBuffer[c].trim()
                                : "";
                        if (name.isEmpty()) {
                            name = String.valueOf(c);
                        }
                        headerNames.add(name);
                    }
                    return;
                }

                if (!schemaFinalized) {
                    // If explicit schema is provided or we collected enough sample rows (e.g. 100), finalize schema
                    sampleRows.add(trimBuffer(currentRowBuffer, maxCol + 1));
                    if (explicitSchema != null || sampleRows.size() >= 100) {
                        finalizeSchema();
                    }
                    return;
                }

                // Append row directly into ColumnVectors
                appendRowToVectors(currentRowBuffer);
            }

            @Override
            public void endSheet() {
                if (!schemaFinalized) {
                    finalizeSchema();
                }
            }

            private String[] trimBuffer(String[] buf, int size) {
                String[] res = new String[size];
                for (int i = 0; i < size; i++) {
                    res[i] = (i < buf.length) ? buf[i] : null;
                }
                return res;
            }

            private void finalizeSchema() {
                schemaFinalized = true;
                int colCount = Math.max(maxCol + 1, headerNames.size());
                if (explicitSchema != null && explicitSchema.size() > colCount) {
                    colCount = explicitSchema.size();
                }
                if (colCount == 0) {
                    vectors = new ArrayList<>();
                    return;
                }

                String[] colNames = new String[colCount];
                Class<?>[] colTypes = new Class<?>[colCount];

                for (int c = 0; c < colCount; c++) {
                    if (hasHeaders && c < headerNames.size()) {
                        colNames[c] = headerNames.get(c);
                    } else if (explicitSchema != null && c < explicitSchema.size()) {
                        colNames[c] = explicitSchema.getColumn(c).columnName();
                    } else {
                        colNames[c] = String.valueOf(c);
                    }

                    if (explicitSchema != null && explicitSchema.hasColumn(colNames[c])) {
                        colTypes[c] = explicitSchema.getType(colNames[c]);
                    } else if (explicitSchema != null && c < explicitSchema.size() && !hasHeaders) {
                        colTypes[c] = explicitSchema.getColumn(c).columnType();
                    } else if (config.isInferTypes()) {
                        colTypes[c] = inferTypeFromSample(sampleRows, c);
                    } else {
                        colTypes[c] = Object.class;
                    }
                }

                int estimatedRows = Math.max(16, sampleRows.size());
                vectors = new ArrayList<>(colCount);
                for (int c = 0; c < colCount; c++) {
                    vectors.add(ColumnVector.create(colNames[c], colTypes[c], estimatedRows, true));
                }

                // Replay buffered sample rows
                for (String[] row : sampleRows) {
                    appendRowToVectors(row);
                }
                sampleRows.clear();
            }

            private void appendRowToVectors(String[] row) {
                int colCount = vectors.size();
                for (int c = 0; c < colCount; c++) {
                    ColumnVector vec = vectors.get(c);
                    String raw = (c < row.length) ? row[c] : null;
                    raw = normalizeParsedString(raw, config);

                    if (raw == null) {
                        vec.appendNull();
                        continue;
                    }

                    Class<?> type = vec.getType();
                    try {
                        if (type == Integer.class || type == int.class) {
                            if (vec instanceof IntColumnVector intVec) {
                                intVec.appendInt(parseExcelInteger(raw));
                            } else {
                                vec.appendObject(parseExcelInteger(raw));
                            }
                        } else if (type == Long.class || type == long.class) {
                            if (vec instanceof LongColumnVector longVec) {
                                longVec.appendLong(parseExcelLong(raw));
                            } else {
                                vec.appendObject(parseExcelLong(raw));
                            }
                        } else if (type == Double.class || type == double.class) {
                            double d = parseExcelDouble(raw);
                            if (vec instanceof DoubleColumnVector doubleVec) {
                                doubleVec.appendDouble(d);
                            } else {
                                vec.appendObject(d);
                            }
                        } else if (type == Float.class || type == float.class) {
                            float f = (float) parseExcelDouble(raw);
                            if (vec instanceof FloatColumnVector floatVec) {
                                floatVec.appendFloat(f);
                            } else {
                                vec.appendObject(f);
                            }
                        } else if (type == Boolean.class || type == boolean.class) {
                            boolean b = parseExcelBoolean(raw);
                            if (vec instanceof BooleanColumnVector boolVec) {
                                boolVec.appendBoolean(b);
                            } else {
                                vec.appendObject(b);
                            }
                        } else if (type == LocalDate.class) {
                            vec.appendObject(parseExcelLocalDate(raw));
                        } else if (type == LocalDateTime.class) {
                            vec.appendObject(parseExcelLocalDateTime(raw));
                        } else if (type == BigDecimal.class) {
                            vec.appendObject(new BigDecimal(cleanNumberString(raw)));
                        } else if (type == String.class) {
                            if (vec instanceof StringColumnVector strVec) {
                                strVec.appendString(raw);
                            } else {
                                vec.appendObject(raw);
                            }
                        } else {
                            vec.appendObject(raw);
                        }
                    } catch (Exception e) {
                        vec.appendObject(raw);
                    }
                }
            }

            public List<ColumnVector> getVectors() {
                return vectors != null ? vectors : new ArrayList<>();
            }
        }

        try {
            StreamingSheetHandler contentsHandler = new StreamingSheetHandler();
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XMLReader parser = factory.newSAXParser().getXMLReader();
            XSSFSheetXMLHandler xmlHandler = new XSSFSheetXMLHandler(
                    styles, null, strings, contentsHandler, new org.apache.poi.ss.usermodel.DataFormatter(), false
            );
            parser.setContentHandler(xmlHandler);
            parser.parse(new InputSource(sheetStream));

            List<ColumnVector> vectors = contentsHandler.getVectors();
            if (!vectors.isEmpty()) {
                this.dataFrame.setDataFrame(DataFrame.fromVectors(vectors));
            }
        } catch (Exception e) {
            throw new ImportException("Failed to parse sheet SAX XML: " + e.getMessage(), e);
        }
    }

    private static String normalizeParsedString(String str, ExcelImporterConfiguration config) {
        if (str == null) {
            return null;
        }
        if (config.getNaStrings() != null && config.getNaStrings().contains(str.trim())) {
            return null;
        }
        if (str.isEmpty() && !config.isPreserveEmptyStrings()) {
            return null;
        }
        return str;
    }

    private static String cleanNumberString(String s) {
        String trimmed = s.trim().replace(" ", "").replace("\u00a0", "");
        if (trimmed.contains(",") && !trimmed.contains(".")) {
            trimmed = trimmed.replace(',', '.');
        } else if (trimmed.contains(",") && trimmed.contains(".")) {
            // e.g. 1,234.56 or 1.234,56
            if (trimmed.lastIndexOf(',') > trimmed.lastIndexOf('.')) {
                trimmed = trimmed.replace(".", "").replace(',', '.');
            } else {
                trimmed = trimmed.replace(",", "");
            }
        }
        return trimmed;
    }

    private static int parseExcelInteger(String s) {
        String clean = cleanNumberString(s);
        double d = Double.parseDouble(clean);
        return (int) d;
    }

    private static long parseExcelLong(String s) {
        String clean = cleanNumberString(s);
        double d = Double.parseDouble(clean);
        return (long) d;
    }

    private static double parseExcelDouble(String s) {
        String clean = cleanNumberString(s);
        return Double.parseDouble(clean);
    }

    private static boolean parseExcelBoolean(String s) {
        String t = s.trim();
        if ("TRUE".equalsIgnoreCase(t) || "1".equals(t) || "YES".equalsIgnoreCase(t)) return true;
        if ("FALSE".equalsIgnoreCase(t) || "0".equals(t) || "NO".equalsIgnoreCase(t)) return false;
        return Boolean.parseBoolean(t);
    }

    private static LocalDate parseExcelLocalDate(String s) {
        String t = s.trim();
        try {
            return LocalDate.parse(t);
        } catch (DateTimeParseException ignored) {
        }
        try {
            double d = Double.parseDouble(t);
            Date dt = DateUtil.getJavaDate(d);
            return dt.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        } catch (Exception ignored) {
        }
        return LocalDate.parse(t);
    }

    private static LocalDateTime parseExcelLocalDateTime(String s) {
        String t = s.trim();
        try {
            return LocalDateTime.parse(t);
        } catch (DateTimeParseException ignored) {
        }
        try {
            LocalDate ld = LocalDate.parse(t);
            return ld.atStartOfDay();
        } catch (DateTimeParseException ignored) {
        }
        try {
            double d = Double.parseDouble(t);
            Date dt = DateUtil.getJavaDate(d);
            return dt.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception ignored) {
        }
        return LocalDateTime.parse(t);
    }

    private static Class<?> inferTypeFromSample(List<String[]> sampleRows, int colIndex) {
        if (sampleRows.isEmpty()) {
            return String.class;
        }

        boolean hasNonNull = false;
        boolean allBoolean = true;
        boolean allInteger = true;
        boolean allLong = true;
        boolean allDouble = true;
        boolean allLocalDate = true;
        boolean allLocalDateTime = true;

        for (String[] row : sampleRows) {
            String val = (colIndex < row.length) ? row[colIndex] : null;
            if (val == null || val.trim().isEmpty() || NA.isNA(val)) {
                continue;
            }
            hasNonNull = true;
            String trimmed = val.trim();

            if (!("TRUE".equalsIgnoreCase(trimmed) || "FALSE".equalsIgnoreCase(trimmed))) {
                allBoolean = false;
            }

            try {
                String clean = cleanNumberString(trimmed);
                double d = Double.parseDouble(clean);
                if (d != Math.floor(d) || Double.isInfinite(d)) {
                    allInteger = false;
                    allLong = false;
                } else {
                    if (d < Integer.MIN_VALUE || d > Integer.MAX_VALUE) {
                        allInteger = false;
                    }
                }
            } catch (Exception e) {
                allInteger = false;
                allLong = false;
                allDouble = false;
            }

            try {
                LocalDate.parse(trimmed);
            } catch (Exception e) {
                allLocalDate = false;
            }

            try {
                LocalDateTime.parse(trimmed);
            } catch (Exception e) {
                if (!allLocalDate) {
                    allLocalDateTime = false;
                }
            }
        }

        if (!hasNonNull) {
            return String.class;
        }
        if (allBoolean) return Boolean.class;
        if (allInteger) return Integer.class;
        if (allLong) return Long.class;
        if (allDouble) return Double.class;
        if (allLocalDate) return LocalDate.class;
        if (allLocalDateTime) return LocalDateTime.class;

        return String.class;
    }

    /**
     * Legacy DOM importer for old binary .xls files.
     */
    private void importLegacyXlsDom(File file, ExcelImporterConfiguration config) {
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet;
            if (config.getSheetName() != null) {
                sheet = workbook.getSheet(config.getSheetName());
                if (sheet == null) {
                    throw new ImportException("Sheet not found: " + config.getSheetName());
                }
            } else {
                sheet = workbook.getSheetAt(config.getSheetIndex());
            }

            FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();

            int firstRowNum = sheet.getFirstRowNum();
            int lastRowNum = sheet.getLastRowNum();
            if (firstRowNum < 0 || lastRowNum < firstRowNum) {
                return;
            }

            Row headerRow = null;
            int startRow = firstRowNum;
            if (config.isHasHeaders()) {
                headerRow = sheet.getRow(firstRowNum);
                startRow = firstRowNum + 1;
            }

            int maxCols = 0;
            for (int r = firstRowNum; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row != null && row.getLastCellNum() > maxCols) {
                    maxCols = row.getLastCellNum();
                }
            }

            if (maxCols == 0) {
                return;
            }

            Schema explicitSchema = config.getSchema() != null ? config.getSchema() : this.dataFrame.getSchema();
            String[] columnNames = new String[maxCols];
            for (int c = 0; c < maxCols; c++) {
                if (config.isHasHeaders() && headerRow != null) {
                    Cell cell = headerRow.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell != null) {
                        columnNames[c] = getCellStringValue(cell, formulaEvaluator);
                    } else {
                        columnNames[c] = String.valueOf(c);
                    }
                } else {
                    if (explicitSchema != null && c < explicitSchema.size()) {
                        columnNames[c] = explicitSchema.getColumn(c).columnName();
                    } else {
                        columnNames[c] = String.valueOf(c);
                    }
                }
            }

            List<Object[]> rawRows = new ArrayList<>();
            for (int r = startRow; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                Object[] rowValues = new Object[maxCols];
                boolean hasAnyValue = false;
                for (int c = 0; c < maxCols; c++) {
                    Cell cell = row != null ? row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) : null;
                    Object cellVal = extractCellValue(cell, formulaEvaluator, config);
                    rowValues[c] = cellVal;
                    if (cellVal != null) {
                        hasAnyValue = true;
                    }
                }
                if (hasAnyValue || r <= lastRowNum) {
                    rawRows.add(rowValues);
                }
            }

            Class<?>[] columnTypes = new Class<?>[maxCols];
            for (int c = 0; c < maxCols; c++) {
                String colName = columnNames[c];
                Class<?> schemaType = null;
                if (explicitSchema != null && explicitSchema.hasColumn(colName)) {
                    schemaType = explicitSchema.getType(colName);
                } else if (explicitSchema != null && c < explicitSchema.size() && !config.isHasHeaders()) {
                    schemaType = explicitSchema.getColumn(c).columnType();
                }

                if (schemaType != null) {
                    columnTypes[c] = schemaType;
                } else if (config.isInferTypes()) {
                    columnTypes[c] = inferColumnTypeFromObjects(rawRows, c);
                } else {
                    columnTypes[c] = Object.class;
                }
            }

            ArrayList<Column> columns = new ArrayList<>(maxCols);
            for (int c = 0; c < maxCols; c++) {
                columns.add(new Column(columnNames[c], columnTypes[c]));
            }
            this.dataFrame.getDataFrame().setHeaders(columns);

            for (Object[] rawRow : rawRows) {
                HashMap<String, Object> rowMap = new HashMap<>();
                for (int c = 0; c < maxCols; c++) {
                    Object val = rawRow[c];
                    rowMap.put(columnNames[c], coerceValue(val, columnTypes[c]));
                }
                this.dataFrame.getDataFrame().addRow(rowMap);
            }

        } catch (IOException e) {
            throw new ImportException("Failed to read Excel file: " + file.getPath(), e);
        } catch (Exception e) {
            if (e instanceof ImportException) {
                throw (ImportException) e;
            }
            throw new ImportException("Failed to parse Excel file: " + file.getPath(), e);
        }
    }

    private static String getCellStringValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA && evaluator != null) {
            CellValue evaluated = evaluator.evaluate(cell);
            if (evaluated == null) return "";
            return switch (evaluated.getCellType()) {
                case STRING -> evaluated.getStringValue();
                case NUMERIC -> String.valueOf(evaluated.getNumberValue());
                case BOOLEAN -> String.valueOf(evaluated.getBooleanValue());
                default -> "";
            };
        }
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double num = cell.getNumericCellValue();
                if (num == Math.floor(num) && !Double.isInfinite(num)) {
                    yield String.valueOf((long) num);
                }
                yield String.valueOf(num);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private static Object extractCellValue(Cell cell, FormulaEvaluator evaluator, ExcelImporterConfiguration config) {
        if (cell == null) {
            return null;
        }

        CellType cellType = cell.getCellType();
        if (cellType == CellType.FORMULA && evaluator != null) {
            CellValue evaluated = evaluator.evaluate(cell);
            if (evaluated == null) return null;
            return extractEvaluatedValue(evaluated, cell, config);
        }

        return extractRawCellValue(cell, cellType, config);
    }

    private static Object extractEvaluatedValue(CellValue evaluated, Cell cell, ExcelImporterConfiguration config) {
        return switch (evaluated.getCellType()) {
            case BOOLEAN -> evaluated.getBooleanValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield toDateTime(cell.getDateCellValue());
                }
                double num = evaluated.getNumberValue();
                if (num == Math.floor(num) && !Double.isInfinite(num)) {
                    if (num >= Integer.MIN_VALUE && num <= Integer.MAX_VALUE) {
                        yield (int) num;
                    }
                    yield (long) num;
                }
                yield num;
            }
            case STRING -> normalizeString(evaluated.getStringValue(), config);
            case BLANK, ERROR -> null;
            default -> null;
        };
    }

    private static Object extractRawCellValue(Cell cell, CellType cellType, ExcelImporterConfiguration config) {
        return switch (cellType) {
            case BOOLEAN -> cell.getBooleanCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield toDateTime(cell.getDateCellValue());
                }
                double num = cell.getNumericCellValue();
                if (num == Math.floor(num) && !Double.isInfinite(num)) {
                    if (num >= Integer.MIN_VALUE && num <= Integer.MAX_VALUE) {
                        yield (int) num;
                    }
                    yield (long) num;
                }
                yield num;
            }
            case STRING -> normalizeString(cell.getStringCellValue(), config);
            case BLANK, ERROR, _NONE -> null;
            default -> null;
        };
    }

    private static Object normalizeString(String str, ExcelImporterConfiguration config) {
        if (str == null) {
            return null;
        }
        if (config.getNaStrings() != null && config.getNaStrings().contains(str.trim())) {
            return null;
        }
        if (str.isEmpty() && !config.isPreserveEmptyStrings()) {
            return null;
        }
        return str;
    }

    private static Object toDateTime(Date date) {
        if (date == null) {
            return null;
        }
        LocalDateTime ldt = date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        if (ldt.getHour() == 0 && ldt.getMinute() == 0 && ldt.getSecond() == 0 && ldt.getNano() == 0) {
            return ldt.toLocalDate();
        }
        return ldt;
    }

    private static Class<?> inferColumnTypeFromObjects(List<Object[]> rows, int colIndex) {
        if (rows.isEmpty()) {
            return String.class;
        }

        boolean hasNonNull = false;
        boolean allBoolean = true;
        boolean allInteger = true;
        boolean allLong = true;
        boolean allDouble = true;
        boolean allLocalDate = true;
        boolean allLocalDateTime = true;

        for (Object[] row : rows) {
            Object val = row[colIndex];
            if (val == null || NA.isNA(val)) {
                continue;
            }
            hasNonNull = true;

            if (!(val instanceof Boolean)) {
                allBoolean = false;
            }
            if (!(val instanceof Integer)) {
                allInteger = false;
            }
            if (!(val instanceof Long) && !(val instanceof Integer)) {
                allLong = false;
            }
            if (!(val instanceof Number)) {
                allDouble = false;
            }
            if (!(val instanceof LocalDate)) {
                allLocalDate = false;
            }
            if (!(val instanceof LocalDateTime) && !(val instanceof LocalDate)) {
                allLocalDateTime = false;
            }
        }

        if (!hasNonNull) {
            return String.class;
        }
        if (allBoolean) return Boolean.class;
        if (allInteger) return Integer.class;
        if (allLong) return Long.class;
        if (allDouble) return Double.class;
        if (allLocalDate) return LocalDate.class;
        if (allLocalDateTime) return LocalDateTime.class;

        return String.class;
    }

    private static Object coerceValue(Object value, Class<?> targetType) {
        if (value == null || targetType == null || targetType.isInstance(value)) {
            return value;
        }

        if (targetType == Integer.class) {
            if (value instanceof Number num) return num.intValue();
            if (value instanceof String str && !str.trim().isEmpty()) return Integer.valueOf(str.trim());
        }
        if (targetType == Long.class) {
            if (value instanceof Number num) return num.longValue();
            if (value instanceof String str && !str.trim().isEmpty()) return Long.valueOf(str.trim());
        }
        if (targetType == Double.class) {
            if (value instanceof Number num) return num.doubleValue();
            if (value instanceof String str && !str.trim().isEmpty()) return Double.valueOf(str.trim());
        }
        if (targetType == Float.class) {
            if (value instanceof Number num) return num.floatValue();
            if (value instanceof String str && !str.trim().isEmpty()) return Float.valueOf(str.trim());
        }
        if (targetType == Boolean.class) {
            if (value instanceof String str) return Boolean.valueOf(str.trim());
            if (value instanceof Number num) return num.intValue() != 0;
        }
        if (targetType == BigDecimal.class) {
            if (value instanceof BigDecimal bd) return bd;
            if (value != null) return new BigDecimal(value.toString().trim());
        }
        if (targetType == LocalDate.class) {
            if (value instanceof LocalDateTime ldt) return ldt.toLocalDate();
            if (value instanceof Date d) return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            if (value instanceof String str && !str.trim().isEmpty()) return LocalDate.parse(str.trim());
        }
        if (targetType == LocalDateTime.class) {
            if (value instanceof LocalDate ld) return ld.atStartOfDay();
            if (value instanceof Date d) return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            if (value instanceof String str && !str.trim().isEmpty()) return LocalDateTime.parse(str.trim());
        }
        if (targetType == String.class) {
            return value.toString();
        }

        return value;
    }
}
