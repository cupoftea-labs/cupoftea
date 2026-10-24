package com.cupoftea.html;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.Column;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.NA;
import com.cupoftea.utils.configurations.TableHtmlConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class TableHtmlRendererTest {

    private CupOfTea createSampleFrame() {
        CupOfTea df = new CupOfTea();
        ArrayList<Column> headers = new ArrayList<>();
        headers.add(new Column("id", Integer.class));
        headers.add(new Column("name", String.class));
        headers.add(new Column("score", Double.class));
        headers.add(new Column("notes", String.class));
        df.setHeaders(headers);

        for (int i = 1; i <= 30; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", i);
            row.put("name", "User_" + i);
            row.put("score", i == 2 ? null : 10.5 * i);
            row.put("notes", i == 3 ? NA.NA : "Note <" + i + "> & info");
            df.addRow(row);
        }
        return df;
    }

    @Test
    @DisplayName("Should render clean HTML table with sticky headers, type badges and pagination")
    void testStandardTableRendering() {
        CupOfTea df = createSampleFrame();
        String html = df.toHtml();

        assertThat(html).isNotNull();
        assertThat(html).contains("class=\"cot-container\"");
        assertThat(html).contains("class=\"cot-scroll-box\"");
        assertThat(html).contains("position: sticky; top: 0;");
        assertThat(html).contains("<span class=\"cot-th-name\">id</span>");
        assertThat(html).contains("<span class=\"cot-th-type\">INT</span>");
        assertThat(html).contains("<span class=\"cot-th-type\">STRING</span>");
        assertThat(html).contains("<span class=\"cot-th-type\">DOUBLE</span>");

        // Escaped HTML test
        assertThat(html).contains("Note &lt;1&gt; &amp; info");

        // Null rendering
        assertThat(html).contains("<span class=\"cot-null\">null</span>");

        // Pagination controls
        assertThat(html).contains("30 rows &times; 4 columns");
        assertThat(html).contains("cot-btn-prev");
        assertThat(html).contains("cot-btn-next");
        assertThat(html).contains("Page 1 of 2");
    }

    @Test
    @DisplayName("Should format NA values with cot-na class when present")
    void testNaRendering() {
        CupOfTea df = new CupOfTea();
        ArrayList<Column> headers = new ArrayList<>();
        headers.add(new Column("val", Object.class));
        df.setHeaders(headers);

        Map<String, Object> r1 = new HashMap<>();
        r1.put("val", NA.NA);
        df.addRow(r1);

        String html = df.toHtml(TableHtmlConfiguration.builder().build());
        assertThat(html).contains("<span class=\"cot-null\">null</span>");

        TableHtmlConfiguration customConfig = TableHtmlConfiguration.builder()
                .nullPlaceholder("NA")
                .build();
        assertThat(df.toHtml(customConfig)).contains("<span class=\"cot-null\">NA</span>");
    }

    @Test
    @DisplayName("Should honor maxRows limit in toHtml(int)")
    void testMaxRows() {
        CupOfTea df = createSampleFrame();
        String html = df.toHtml(5);

        assertThat(html).contains("showing first 5");
        assertThat(html).contains("User_5");
        assertThat(html).doesNotContain("User_6");
    }

    @Test
    @DisplayName("Should render full document when requested")
    void testFullDocument() {
        CupOfTea df = createSampleFrame();
        TableHtmlConfiguration config = TableHtmlConfiguration.builder()
                .fullDocument(true)
                .title("My Test Report")
                .build();

        String html = df.toHtml(config);
        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html).contains("<title>My Test Report</title>");
        assertThat(html).endsWith("</html>\n");
    }

    @Test
    @DisplayName("Should handle empty DataFrame gracefully")
    void testEmptyDataFrame() {
        CupOfTea df = new CupOfTea();
        String html = df.toHtml();

        assertThat(html).contains("Empty DataFrame (0 rows)");
    }

    @Test
    @DisplayName("Should support Jupyter / IJava MIME contract methods")
    void testJupyterMimeContracts() {
        CupOfTea df = createSampleFrame();
        TableHtmlConfiguration config = TableHtmlConfiguration.builder().tableId("test-tbl").build();

        String htmlWithConfig = df.toHtml(config);
        assertThat(htmlWithConfig).contains("id=\"test-tbl\"");

        String renderHtml = df.renderHtml();
        assertThat(renderHtml).contains("class=\"cot-container\"");

        Map<String, String> mimeData = df.render();
        assertThat(mimeData).isNotNull();
        assertThat(mimeData).containsKey("text/html");
        assertThat(mimeData.get("text/html")).contains("class=\"cot-container\"");

        // Also test directly on DataFrame instance
        DataFrame innerDf = (DataFrame) df.getDataFrame();
        assertThat(innerDf.renderHtml()).contains("class=\"cot-container\"");
        assertThat(innerDf.render()).containsKey("text/html");
    }
}
