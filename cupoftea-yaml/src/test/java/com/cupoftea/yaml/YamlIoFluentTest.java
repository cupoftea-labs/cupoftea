package com.cupoftea.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;

public class YamlIoFluentTest {

    @Test
    @DisplayName("Should read YAML using CupOfTea.read().yaml(path)")
    void testReadYamlFluent() {
        File sample = new File("src/test/resources/testFiles/yaml_sample.yaml");
        if (!sample.exists()) {
            sample = new File("cupoftea-yaml/src/test/resources/testFiles/yaml_sample.yaml");
        }

        CupOfTea df = CupOfTea.read().yaml(sample.getPath());
        assertThat(df.getHeaders()).hasSize(3);
        assertThat(df.size()).isEqualTo(4);
    }
}
