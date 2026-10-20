package com.CupOfTea.importers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.CupOfTea.CupOfTea;

public class ExcelImporterTest {

    @Test
    @DisplayName("Should import DataFrame from XLSX Excel file")
    void test_GivenAXLSXExcelFile_WhenImporting_ShouldReturnACupOfTeaDataFrame() {
        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 4;

        File testFile = new File("src/test/java/com/CupOfTea/testFiles/excel_sample.xlsx");
        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromExcel(testFile, true);

        assertThat(cupOfTea.getHeaders()).hasSize(expectedColumnsNumber);
        assertThat(cupOfTea.getRows()).hasSize(expectedRowsNumber);
    }

    @Test
    @DisplayName("Should import DataFrame from XLS Excel file")
    void test_GivenAXLSExcelFile_WhenImporting_ShouldReturnACupOfTeaDataFrame() {
        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 4;

        File testFile = new File("src/test/java/com/CupOfTea/testFiles/excel_sample.xls");
        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromExcel(testFile, true);

        assertThat(cupOfTea.getHeaders()).hasSize(expectedColumnsNumber);
        assertThat(cupOfTea.getRows()).hasSize(expectedRowsNumber);
    }
}
