package com.CupOfTea.importers;







import java.io.File;

import com.CupOfTea.CupOfTea;

import junit.framework.TestCase;

public class ExcelImporterTest extends TestCase {

    public void test_GivenAXLSXExcelFile_WhenImporting_ShouldReturnACupOfTeaDataFrame() throws Exception {

        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 4;

        File testFile = new File("src/test/java/com/CupOfTea/testFiles/excel_sample.xlsx");

        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromExcel(testFile, true);

        assertEquals(expectedColumnsNumber, cupOfTea.getHeaders().size());
        assertEquals(expectedRowsNumber, cupOfTea.getRows().size());

    }

    public void test_GivenAXLSExcelFile_WhenImporting_ShouldReturnACupOfTeaDataFrame() throws Exception {

        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 4;

        File testFile = new File("src/test/java/com/CupOfTea/testFiles/excel_sample.xls");

        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromExcel(testFile, true);

        assertEquals(expectedColumnsNumber, cupOfTea.getHeaders().size());
        assertEquals(expectedRowsNumber, cupOfTea.getRows().size());

    }

}
