package com.cupoftea.importers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;

public class YamlImporterTest {

    
    private File getSampleYaml() {
        File[] candidates = new File[]{
            new File("src/test/resources/testFiles/yaml_sample.yaml"),
            new File("cupoftea-yaml/src/test/resources/testFiles/yaml_sample.yaml"),
            new File("src/test/java/com/cupoftea/testFiles/yaml_sample.yaml"),
            new File("src/test/java/com/CupOfTea/testFiles/yaml_sample.yaml")
        };
        for (File candidate : candidates) {
            if (candidate.exists()) return candidate;
        }
        return candidates[0];
    }

    @Test
    @DisplayName("Should import DataFrame from YAML file")
    void test_GivenAYamlFile_WhenImporting_ShouldReturnACupOfTeaDataFrame() {
        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 4;

        File testFile = getSampleYaml();
        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromYaml(testFile);

        assertThat(cupOfTea.getHeaders()).hasSize(expectedColumnsNumber);
        assertThat(cupOfTea.getRows()).hasSize(expectedRowsNumber);
    }
}
