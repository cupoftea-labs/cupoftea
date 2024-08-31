package com.CupOfTea.importers;

import java.io.File;

import com.CupOfTea.CupOfTea;

import junit.framework.TestCase;

public class JsonImporterTest extends TestCase {

    public void test_GivenAJsonFile_WhenImporting_ShouldReturnACupOfTeaDataFrame() throws Exception {

        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 4;

        File testFile = new File("src/test/java/com/CupOfTea/testFiles/json_sample.json");

        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromJson(testFile);

        assertEquals(expectedColumnsNumber, cupOfTea.getHeaders().size());
        assertEquals(expectedRowsNumber, cupOfTea.getRows().size());

    }

}
