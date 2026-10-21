package com.cupoftea.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;

public class ExcelIoFluentTest {

    @Test
    @DisplayName("Should read Excel workbook using CupOfTea.read().excel(path)")
    void testReadExcelFluent() {
        File sample = new File("src/test/resources/testFiles/excel_sample.xlsx");
        if (!sample.exists()) {
            sample = new File("cupoftea-excel/src/test/resources/testFiles/excel_sample.xlsx");
        }

        CupOfTea df = CupOfTea.read().excel(sample.getPath(), true);
        assertThat(df.getHeaders()).hasSize(3);
        assertThat(df.size()).isEqualTo(4);
    }
}
