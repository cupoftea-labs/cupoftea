package com.cupoftea.importers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.util.Date;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.importers.configurations.ExcelImporterConfiguration;
import com.cupoftea.table.NA;
import com.cupoftea.table.Schema;
import static com.cupoftea.table.Schema.BOOLEAN;
import static com.cupoftea.table.Schema.DATE;
import static com.cupoftea.table.Schema.DOUBLE;
import static com.cupoftea.table.Schema.INT;
import static com.cupoftea.table.Schema.STRING;

public class ExcelImporterTest {

    
    private File getSampleExcel(String name) {
        File[] candidates = new File[]{
            new File("src/test/resources/testFiles/" + name),
            new File("cupoftea-excel/src/test/resources/testFiles/" + name),
            new File("src/test/java/com/cupoftea/testFiles/" + name),
            new File("src/test/java/com/CupOfTea/testFiles/" + name)
        };
        for (File candidate : candidates) {
            if (candidate.exists()) return candidate;
        }
        return candidates[0];
    }

    @Test
    @DisplayName("Should import DataFrame from XLSX Excel file")
    void test_GivenAXLSXExcelFile_WhenImporting_ShouldReturnACupOfTeaDataFrame() {
        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 4;

        File testFile = getSampleExcel("excel_sample.xlsx");
        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromExcel(testFile, true);

        assertThat(cupOfTea.getHeaders()).hasSize(expectedColumnsNumber);
        assertThat(cupOfTea.getRows()).hasSize(expectedRowsNumber);
        for (com.cupoftea.table.Column col : cupOfTea.getHeaders()) {
            assertThat(col.columnType()).isNotNull();
        }
    }

    @Test
    @DisplayName("Should import DataFrame from XLS Excel file")
    void test_GivenAXLSExcelFile_WhenImporting_ShouldReturnACupOfTeaDataFrame() {
        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 4;

        File testFile = getSampleExcel("excel_sample.xls");
        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromExcel(testFile, true);

        assertThat(cupOfTea.getHeaders()).hasSize(expectedColumnsNumber);
        assertThat(cupOfTea.getRows()).hasSize(expectedRowsNumber);
        for (com.cupoftea.table.Column col : cupOfTea.getHeaders()) {
            assertThat(col.columnType()).isNotNull();
        }
    }

    @Test
    @DisplayName("Should import Excel with explicit Schema and typed cells (boolean, date, numeric)")
    void test_GivenAnExcelFileWithVariousTypes_WhenImportingWithSchema_ShouldApplyTypesCorrectly() throws Exception {
        File tempFile = File.createTempFile("excel_types_test_", ".xlsx");
        tempFile.deleteOnExit();

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Data");
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("name");
            header.createCell(1).setCellValue("year");
            header.createCell(2).setCellValue("price");
            header.createCell(3).setCellValue("active");
            header.createCell(4).setCellValue("birthDate");

            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Item A");
            r1.createCell(1).setCellValue(2021);
            r1.createCell(2).setCellValue(19.99);
            r1.createCell(3).setCellValue(true);
            Cell dateCell1 = r1.createCell(4);
            dateCell1.setCellValue(new Date(124, 0, 15)); // 2024-01-15
            dateCell1.setCellStyle(dateStyle);

            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("Item B");
            r2.createCell(1).setCellValue(2022);
            r2.createCell(2).setCellValue(29.99);
            r2.createCell(3).setCellValue(false);
            Cell dateCell2 = r2.createCell(4);
            dateCell2.setCellValue(new Date(124, 5, 20)); // 2024-06-20
            dateCell2.setCellStyle(dateStyle);

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                wb.write(fos);
            }
        }

        Schema schema = Schema.of("name", STRING, "year", INT, "price", DOUBLE, "active", BOOLEAN, "birthDate", DATE);
        CupOfTea df = new CupOfTea().importFromExcel(tempFile, schema);

        assertThat(df.getHeaders()).hasSize(5);
        assertThat(df.getColumnHeader("year").columnType()).isEqualTo(Integer.class);
        assertThat(df.getColumnHeader("price").columnType()).isEqualTo(Double.class);
        assertThat(df.getColumnHeader("active").columnType()).isEqualTo(Boolean.class);
        assertThat(df.getColumnHeader("birthDate").columnType()).isEqualTo(LocalDate.class);

        assertThat(df.getRow(0).get("year")).isEqualTo(2021);
        assertThat(df.getRow(0).get("price")).isEqualTo(19.99);
        assertThat(df.getRow(0).get("active")).isEqualTo(true);
        assertThat(df.getRow(0).get("birthDate")).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    @DisplayName("Should infer types from Excel sheet without explicit schema")
    void test_GivenAnExcelFile_WhenImportingWithInference_ShouldInferColumnTypes() throws Exception {
        File tempFile = File.createTempFile("excel_infer_test_", ".xlsx");
        tempFile.deleteOnExit();

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Inference");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("quantity");
            header.createCell(1).setCellValue("isAvailable");

            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue(10);
            r1.createCell(1).setCellValue(true);

            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue(25);
            r2.createCell(1).setCellValue(false);

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                wb.write(fos);
            }
        }

        CupOfTea df = new CupOfTea().importFromExcel(tempFile, new ExcelImporterConfiguration().setInferTypes(true));

        assertThat(df.getColumnHeader("quantity").columnType()).isEqualTo(Integer.class);
        assertThat(df.getColumnHeader("isAvailable").columnType()).isEqualTo(Boolean.class);
        assertThat(df.getRow(0).get("quantity")).isEqualTo(10);
        assertThat(df.getRow(0).get("isAvailable")).isEqualTo(true);
    }
}
