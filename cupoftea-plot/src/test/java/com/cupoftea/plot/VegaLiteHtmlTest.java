package com.cupoftea.plot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.Column;

import static org.assertj.core.api.Assertions.assertThat;

class VegaLiteHtmlTest {

    private CupOfTea df;

    @BeforeEach
    void setUp() {
        df = new CupOfTea();
        ArrayList<Column> headers = new ArrayList<>();
        headers.add(new Column("experience", Double.class));
        headers.add(new Column("salary", Double.class));
        headers.add(new Column("department", String.class));
        df.setHeaders(headers);

        for (int i = 1; i <= 5; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("experience", 1.0 * i);
            row.put("salary", 50000.0 * i);
            row.put("department", "Eng");
            df.addRow(row);
        }
    }

    @Test
    @DisplayName("Should generate embeddable HTML snippet for Jupyter notebooks")
    void testEmbeddableHtml() {
        Chart chart = df.plot().scatter("experience", "salary")
                .title("Salary vs Experience");

        String html = chart.toHtml();

        assertThat(html).isNotNull();
        assertThat(html).contains("class=\"cupoftea-plot\"");
        assertThat(html).contains("vegaEmbed");
        assertThat(html).contains("https://cdn.jsdelivr.net/npm/vega-embed@6");
        assertThat(html).doesNotContain("<!DOCTYPE html>");
    }

    @Test
    @DisplayName("Should generate standalone HTML document")
    void testStandaloneHtml() {
        Chart chart = df.plot().scatter("experience", "salary")
                .title("Salary Report");

        String html = chart.toHtml(true);

        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html).contains("<title>Salary Report</title>");
        assertThat(html).contains("<script src=\"https://cdn.jsdelivr.net/npm/vega@5\"></script>");
        assertThat(html).contains("<script src=\"https://cdn.jsdelivr.net/npm/vega-lite@5\"></script>");
        assertThat(html).contains("<script src=\"https://cdn.jsdelivr.net/npm/vega-embed@6\"></script>");
        assertThat(html).endsWith("</html>\n");
    }

    @Test
    @DisplayName("Should save standalone HTML file to disk")
    void testSaveHtml(@TempDir File tempDir) throws IOException {
        File targetFile = new File(tempDir, "test_chart.html");

        Chart chart = df.plot().bar("department", "salary")
                .title("Department Salaries");

        chart.saveHtml(targetFile);

        assertThat(targetFile).exists();
        String content = Files.readString(targetFile.toPath());
        assertThat(content).startsWith("<!DOCTYPE html>");
        assertThat(content).contains("Department Salaries");
    }

    @Test
    @DisplayName("Should execute show() safely in headless test environment")
    void testShowHeadlessSafety() {
        Chart chart = df.plot().line("experience", "salary");
        Chart result = chart.show();
        assertThat(result).isSameAs(chart);
    }

    @Test
    @DisplayName("Should support notebook MIME rendering contracts")
    void testNotebookMimeContracts() {
        Chart chart = df.plot().hist("salary", 5);

        String renderHtml = chart.renderHtml();
        assertThat(renderHtml).isEqualTo(chart.toHtml());

        Map<String, String> mimeData = chart.render();
        assertThat(mimeData).isNotNull();
        assertThat(mimeData).containsKey("text/html");
        assertThat(mimeData.get("text/html")).isEqualTo(chart.toHtml());
    }
}
