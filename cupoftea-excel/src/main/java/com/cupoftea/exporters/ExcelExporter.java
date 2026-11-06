package com.cupoftea.exporters;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.configurations.ExcelExporterConfiguration;
import com.cupoftea.exporters.interfaces.IExcelExporter;
import com.cupoftea.table.Column;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * High-performance streaming Excel exporter powered by Apache POI {@link SXSSFWorkbook}.
 * Flushes rows continuously to temporary disk storage via a sliding window, keeping heap footprint constant
 * even for massive spreadsheets.
 */
public class ExcelExporter implements IExcelExporter {

    private final CupOfTea dataFrame;

    public ExcelExporter(final CupOfTea dataFrame) {
        this.dataFrame = Objects.requireNonNull(dataFrame, "dataFrame cannot be null");
    }

    @Override
    public void exportToExcel(File file) {
        exportToExcel(file, new ExcelExporterConfiguration());
    }

    @Override
    public void exportToExcel(File file, ExcelExporterConfiguration configuration) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }
        ExcelExporterConfiguration config = configuration != null ? configuration : new ExcelExporterConfiguration();
        try (OutputStream fos = new BufferedOutputStream(new FileOutputStream(file))) {
            exportToExcel(fos, config);
        } catch (IOException e) {
            throw new ExportException("Failed to export Excel to file: " + file.getPath(), e);
        }
    }

    @Override
    public void exportToExcel(OutputStream outputStream) {
        exportToExcel(outputStream, new ExcelExporterConfiguration());
    }

    @Override
    public void exportToExcel(OutputStream outputStream, ExcelExporterConfiguration configuration) {
        if (outputStream == null) {
            throw new ExportException("OutputStream cannot be null");
        }
        ExcelExporterConfiguration config = configuration != null ? configuration : new ExcelExporterConfiguration();

        List<ColumnVector> vectors = this.dataFrame.getDataFrame().getColumnVectors();
        List<Column> headers = this.dataFrame.getHeaders();
        int totalCols = vectors.size();
        int totalRows = this.dataFrame.size();

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(config.getRowAccessWindowSize())) {
            workbook.setCompressTempFiles(config.isCompressTempFiles());
            SXSSFSheet sheet = workbook.createSheet(config.getSheetName());

            CreationHelper createHelper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(createHelper.createDataFormat().getFormat(config.getDateFormat()));

            CellStyle dateTimeStyle = workbook.createCellStyle();
            dateTimeStyle.setDataFormat(createHelper.createDataFormat().getFormat(config.getDateTimeFormat()));

            int currentExcelRow = 0;

            if (config.isWriteHeader() && !headers.isEmpty()) {
                Row headerRow = sheet.createRow(currentExcelRow++);
                for (int c = 0; c < totalCols; c++) {
                    Cell cell = headerRow.createCell(c);
                    cell.setCellValue(headers.get(c).columnName());
                }
            }

            ColumnVector[] vecArr = vectors.toArray(new ColumnVector[0]);

            for (int r = 0; r < totalRows; r++) {
                Row row = sheet.createRow(currentExcelRow++);
                for (int c = 0; c < totalCols; c++) {
                    ColumnVector vec = vecArr[c];
                    if (vec.isNull(r)) {
                        continue;
                    }

                    Cell cell = row.createCell(c);

                    if (vec instanceof IntColumnVector intVec) {
                        cell.setCellValue(intVec.getInt(r));
                    } else if (vec instanceof LongColumnVector longVec) {
                        cell.setCellValue(longVec.getLong(r));
                    } else if (vec instanceof DoubleColumnVector doubleVec) {
                        cell.setCellValue(doubleVec.getDouble(r));
                    } else if (vec instanceof FloatColumnVector floatVec) {
                        cell.setCellValue(floatVec.getFloat(r));
                    } else if (vec instanceof BooleanColumnVector boolVec) {
                        cell.setCellValue(boolVec.getBoolean(r));
                    } else if (vec instanceof StringColumnVector strVec) {
                        cell.setCellValue(strVec.getString(r));
                    } else {
                        Object val = vec.getObject(r);
                        if (val == null) {
                            continue;
                        }
                        if (val instanceof Number num) {
                            if (num instanceof BigDecimal bd) {
                                cell.setCellValue(bd.doubleValue());
                            } else {
                                cell.setCellValue(num.doubleValue());
                            }
                        } else if (val instanceof Boolean b) {
                            cell.setCellValue(b);
                        } else if (val instanceof LocalDate ld) {
                            cell.setCellValue(ld);
                            cell.setCellStyle(dateStyle);
                        } else if (val instanceof LocalDateTime ldt) {
                            cell.setCellValue(ldt);
                            cell.setCellStyle(dateTimeStyle);
                        } else if (val instanceof Date d) {
                            cell.setCellValue(d);
                            cell.setCellStyle(dateTimeStyle);
                        } else {
                            cell.setCellValue(val.toString());
                        }
                    }
                }
            }

            workbook.write(outputStream);
            workbook.dispose();
        } catch (IOException e) {
            throw new ExportException("Failed to write Excel workbook: " + e.getMessage(), e);
        }
    }
}
