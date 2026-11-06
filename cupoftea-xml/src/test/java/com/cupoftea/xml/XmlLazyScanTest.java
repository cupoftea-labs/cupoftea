package com.cupoftea.xml;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cupoftea.CupOfTea;
import com.cupoftea.importers.configurations.XmlImporterConfiguration;
import com.cupoftea.lazy.LazyCupOfTea;
import com.cupoftea.table.Schema;

public class XmlLazyScanTest {

    @TempDir
    File tempDir;

    private File getSampleXml() {
        File[] candidates = new File[]{
            new File("src/test/resources/testFiles/xml_sample.xml"),
            new File("cupoftea-xml/src/test/resources/testFiles/xml_sample.xml")
        };
        for (File candidate : candidates) {
            if (candidate.exists()) return candidate;
        }
        return candidates[0];
    }

    @Test
    @DisplayName("Should scan standard XML file lazily and collect results")
    void testScanXmlFile() {
        File sample = getSampleXml();

        LazyCupOfTea lazy = CupOfTea.scanXml(sample);
        assertThat(lazy).isNotNull();
        assertThat(lazy.getSchema()).isNotNull();

        CupOfTea collected = lazy.collect();
        assertThat(collected.size()).isEqualTo(16);
        assertThat(collected.getHeaders()).hasSize(4);
    }

    @Test
    @DisplayName("Should scan XML lazily with projection pushdown")
    void testScanXmlProjectionPushdown() {
        File sample = getSampleXml();

        CupOfTea result = CupOfTea.scanXml(sample)
                .select("groupId", "artifactId")
                .collect();

        assertThat(result.size()).isEqualTo(16);
        assertThat(result.getHeaders()).hasSize(2);
        assertThat(result.getHeaders().get(0).columnName()).isEqualTo("groupId");
        assertThat(result.getHeaders().get(1).columnName()).isEqualTo("artifactId");
    }

    @Test
    @DisplayName("Should scan XML lazily with limit/head")
    void testScanXmlLimit() {
        File sample = getSampleXml();

        CupOfTea result = CupOfTea.scanXml(sample)
                .limit(5)
                .collect();

        assertThat(result.size()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should scan large generated XML with custom batch size")
    void testScanGeneratedXmlBatches() throws IOException {
        File generatedXml = new File(tempDir, "large.xml");
        try (FileWriter fw = new FileWriter(generatedXml)) {
            fw.write("<people>\n");
            for (int i = 0; i < 500; i++) {
                fw.write("  <person id=\"" + i + "\">\n");
                fw.write("    <name>User_" + i + "</name>\n");
                fw.write("    <age>" + (20 + (i % 50)) + "</age>\n");
                fw.write("  </person>\n");
            }
            fw.write("</people>\n");
        }

        XmlImporterConfiguration config = XmlImporterConfiguration.builder()
                .batchSize(50)
                .rowElement("person");

        LazyCupOfTea lazy = CupOfTea.scan().xml(generatedXml, config);
        CupOfTea collected = lazy.collect();

        assertThat(collected.size()).isEqualTo(500);
        assertThat(collected.getHeaders()).extracting("columnName")
                .contains("person.id", "name", "age");

        // Filter and collect
        CupOfTea filtered = CupOfTea.scan().xml(generatedXml, config)
                .filter(row -> ((Number) row.get("age")).intValue() > 60)
                .collect();

        assertThat(filtered.size()).isGreaterThan(0);
        assertThat(filtered.size()).isLessThan(500);
    }
}
