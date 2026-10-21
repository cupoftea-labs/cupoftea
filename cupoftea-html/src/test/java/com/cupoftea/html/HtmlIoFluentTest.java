package com.cupoftea.html;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;

public class HtmlIoFluentTest {

    @Test
    @DisplayName("Should read HTML table using CupOfTea.read().html(path)")
    void testReadHtmlFluent() {
        File sample = new File("src/test/resources/testFiles/html_sample.html");
        if (!sample.exists()) {
            sample = new File("cupoftea-html/src/test/resources/testFiles/html_sample.html");
        }

        CupOfTea df = CupOfTea.read().html(sample.getPath());
        assertThat(df.getHeaders()).hasSize(11);
        assertThat(df.size()).isEqualTo(19);
    }
}
