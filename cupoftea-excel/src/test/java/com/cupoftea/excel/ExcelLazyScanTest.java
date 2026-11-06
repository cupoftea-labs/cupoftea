package com.cupoftea.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.lazy.LazyCupOfTea;

public class ExcelLazyScanTest {

    @Test
    @DisplayName("Should scan Excel workbook lazily and collect results")
    void testExcelLazyScanPipeline() throws Exception {
        File temp = File.createTempFile("lazy_excel_", ".xlsx");
        temp.deleteOnExit();

        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            Sheet sheet = wb.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("id");
            header.createCell(1).setCellValue("dept");
            header.createCell(2).setCellValue("salary");

            for (int i = 0; i < 500; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(i);
                row.createCell(1).setCellValue("DEPT_" + (i % 5));
                row.createCell(2).setCellValue(50000 + (i * 100));
            }

            try (FileOutputStream fos = new FileOutputStream(temp)) {
                wb.write(fos);
            }
            wb.dispose();
        }

        LazyCupOfTea lazy = CupOfTea.scan().excel(temp);
        assertThat(lazy).isNotNull();

        CupOfTea collected = lazy.collect();
        assertThat(collected.size()).isEqualTo(500);
        assertThat(collected.getHeaders()).hasSize(3);
        assertThat(collected.getRow(0).get("id")).isEqualTo(0);
        assertThat(collected.getRow(0).get("dept")).isEqualTo("DEPT_0");

        // Projection
        CupOfTea projected = CupOfTea.scanExcel(temp).select("dept", "salary").collect();
        assertThat(projected.getHeaders()).hasSize(2);
        assertThat(projected.getHeaders().get(0).columnName()).isEqualTo("dept");
        assertThat(projected.getHeaders().get(1).columnName()).isEqualTo("salary");
        assertThat(projected.size()).isEqualTo(500);
    }
}
