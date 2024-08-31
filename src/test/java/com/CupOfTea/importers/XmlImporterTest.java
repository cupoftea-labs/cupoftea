package com.CupOfTea.importers;

import java.io.File;

import com.CupOfTea.CupOfTea;

import junit.framework.TestCase;

public class XmlImporterTest extends TestCase {


    public void test_GivenAXmlFile_WhenImporting_ShouldReturnACupOfTeaDataFrame() throws Exception {

        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 16;

        File testFile = new File("src/test/java/com/CupOfTea/testFiles/xml_sample.xml");

        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromXml(testFile);

        assertEquals(expectedColumnsNumber, cupOfTea.getHeaders().size());
        assertEquals(expectedRowsNumber, cupOfTea.getRows().size());

    }


}
