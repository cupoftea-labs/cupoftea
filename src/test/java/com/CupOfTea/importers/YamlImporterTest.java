package com.CupOfTea.importers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.CupOfTea.CupOfTea;

public class YamlImporterTest {

    @Test
    @DisplayName("Should import DataFrame from YAML file")
    void test_GivenAYamlFile_WhenImporting_ShouldReturnACupOfTeaDataFrame() {
        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 4;

        File testFile = new File("src/test/java/com/CupOfTea/testFiles/yaml_sample.yaml");
        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromYaml(testFile);

        assertThat(cupOfTea.getHeaders()).hasSize(expectedColumnsNumber);
        assertThat(cupOfTea.getRows()).hasSize(expectedRowsNumber);
    }
}
