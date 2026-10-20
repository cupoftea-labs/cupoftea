package com.cupoftea.xml;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;

public class XmlIoFluentTest {

    @Test
    @DisplayName("Should read XML document using CupOfTea.read().xml(path)")
    void testReadXmlFluent() {
        File sample = new File("src/test/resources/testFiles/xml_sample.xml");
        if (!sample.exists()) {
            sample = new File("cupoftea-xml/src/test/resources/testFiles/xml_sample.xml");
        }

        CupOfTea df = CupOfTea.read().xml(sample.getPath());
        assertThat(df.getHeaders()).hasSize(3);
        assertThat(df.size()).isEqualTo(16);
    }
}
