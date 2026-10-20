package com.CupOfTea.importers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.CupOfTea.CupOfTea;

public class XmlImporterTest {

    @Test
    @DisplayName("Should import DataFrame from XML file")
    void test_GivenAXmlFile_WhenImporting_ShouldReturnACupOfTeaDataFrame() {
        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 16;

        File testFile = new File("src/test/java/com/CupOfTea/testFiles/xml_sample.xml");
        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromXml(testFile);

        assertThat(cupOfTea.getHeaders()).hasSize(expectedColumnsNumber);
        assertThat(cupOfTea.getRows()).hasSize(expectedRowsNumber);
    }
}
