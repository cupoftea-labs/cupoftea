package com.cupoftea.importers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;

public class XmlImporterTest {

    
    private File getSampleXml() {
        File[] candidates = new File[]{
            new File("src/test/resources/testFiles/xml_sample.xml"),
            new File("cupoftea-xml/src/test/resources/testFiles/xml_sample.xml"),
            new File("src/test/java/com/cupoftea/testFiles/xml_sample.xml"),
            new File("src/test/java/com/CupOfTea/testFiles/xml_sample.xml")
        };
        for (File candidate : candidates) {
            if (candidate.exists()) return candidate;
        }
        return candidates[0];
    }

    @Test
    @DisplayName("Should import DataFrame from XML file")
    void test_GivenAXmlFile_WhenImporting_ShouldReturnACupOfTeaDataFrame() {
        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 16;

        File testFile = getSampleXml();
        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromXml(testFile);

        assertThat(cupOfTea.getHeaders()).hasSize(expectedColumnsNumber);
        assertThat(cupOfTea.getRows()).hasSize(expectedRowsNumber);
    }
}
