package com.CupOfTea.importers;

import java.io.File;

import com.CupOfTea.CupOfTea;

import junit.framework.TestCase;

public class YamlImporterTest extends TestCase {


    public void test_GivenAYamlFile_WhenImporting_ShouldReturnACupOfTeaDataFrame() throws Exception {

        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 4;

        File testFile = new File("src/test/java/com/CupOfTea/testFiles/yaml_sample.yaml");

        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromYaml(testFile);

        assertEquals(expectedColumnsNumber, cupOfTea.getHeaders().size());
        assertEquals(expectedRowsNumber, cupOfTea.getRows().size());

    }

}
