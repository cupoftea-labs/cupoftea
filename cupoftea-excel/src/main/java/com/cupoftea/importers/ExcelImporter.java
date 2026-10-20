package com.cupoftea.importers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.configurations.ExcelImporterConfiguration;
import com.cupoftea.importers.interfaces.IExcelImporter;
import com.cupoftea.table.Column;
import com.cupoftea.table.NA;
import com.cupoftea.table.Schema;

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

            // Determine max column count across sheet
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
                    columnNames[c] = String.valueOf(c);
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

            Schema explicitSchema = config.getSchema() != null ? config.getSchema() : this.dataFrame.getSchema();
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
                    columnTypes[c] = inferColumnType(rawRows, c);
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

    private static Class<?> inferColumnType(List<Object[]> rows, int colIndex) {
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
