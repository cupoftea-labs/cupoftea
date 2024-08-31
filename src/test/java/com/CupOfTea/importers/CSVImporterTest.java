package com.CupOfTea.importers;


import java.io.File;

import com.CupOfTea.CupOfTea;

import junit.framework.TestCase;

public class CSVImporterTest extends TestCase {


    public void test_GivenACSVFileWithHeaders_WhenImporting_ShouldReturnACupOfTeaDataFrame() throws Exception {

        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 500;

        File testFile = new File("src/test/java/com/CupOfTea/testFiles/csv_sample.csv");

        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromCsv(testFile, true);

        assertEquals(expectedColumnsNumber, cupOfTea.getHeaders().size());
        assertEquals(expectedRowsNumber, cupOfTea.getRows().size());

    }

}
