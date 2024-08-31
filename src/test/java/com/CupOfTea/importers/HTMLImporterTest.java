package com.CupOfTea.importers;

import java.io.File;

import com.CupOfTea.CupOfTea;

import junit.framework.TestCase;

public class HTMLImporterTest extends TestCase {

    public void test_GivenAHTMLFile_WhenImporting_ShouldReturnACupOfTeaDataFrame() throws Exception {

        int expectedColumnsNumber = 11;
        int expectedRowsNumber = 19;

        File testFile = new File("src/test/java/com/CupOfTea/testFiles/html_sample.html");

        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromHtml(testFile);

        assertEquals(expectedColumnsNumber, cupOfTea.getHeaders().size());
        assertEquals(expectedRowsNumber, cupOfTea.getRows().size());

    }

}
