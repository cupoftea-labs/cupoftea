package com.cupoftea.plot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.Column;
import com.cupoftea.table.NA;

import static org.assertj.core.api.Assertions.assertThat;

class VegaLiteSpecTest {

    private CupOfTea df;

    @BeforeEach
    void setUp() {
        df = new CupOfTea();
        ArrayList<Column> headers = new ArrayList<>();
        headers.add(new Column("department", String.class));
        headers.add(new Column("experience", Double.class));
        headers.add(new Column("salary", Double.class));
        headers.add(new Column("age", Integer.class));
        headers.add(new Column("join_date", LocalDate.class));
        headers.add(new Column("headcount", Integer.class));
        headers.add(new Column("performance_score", Double.class));
        df.setHeaders(headers);

        String[] depts = {"Engineering", "Sales", "Marketing", "HR"};
        for (int i = 1; i <= 20; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("department", depts[i % depts.length]);
            row.put("experience", 1.5 * i);
            row.put("salary", 50000.0 + i * 2500);
            row.put("age", 22 + i);
            row.put("join_date", LocalDate.of(2020, 1, 1).plusMonths(i));
            row.put("headcount", 10 + i * 2);
            row.put("performance_score", i == 5 ? NA.NA : 75.0 + (i % 25));
            df.addRow(row);
        }
    }

    @Test
    @DisplayName("Should build valid Line chart specification")
    void testLineChartSpec() {
        Chart chart = df.plot().line("experience", "salary")
                .title("Salary Growth")
                .xLabel("Years")
                .yLabel("Salary (USD)");

        Map<String, Object> spec = chart.toSpecMap();
        assertThat(spec.get("$schema")).isEqualTo("https://vega.github.io/schema/vega-lite/v5.json");

        @SuppressWarnings("unchecked")
        Map<String, Object> mark = (Map<String, Object>) spec.get("mark");
        assertThat(mark.get("type")).isEqualTo("line");
        assertThat(mark.get("point")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> encoding = (Map<String, Object>) spec.get("encoding");
        @SuppressWarnings("unchecked")
        Map<String, Object> xEnc = (Map<String, Object>) encoding.get("x");
        @SuppressWarnings("unchecked")
        Map<String, Object> yEnc = (Map<String, Object>) encoding.get("y");

        assertThat(xEnc.get("field")).isEqualTo("experience");
        assertThat(xEnc.get("type")).isEqualTo("quantitative");
        assertThat(xEnc.get("title")).isEqualTo("Years");

        assertThat(yEnc.get("field")).isEqualTo("salary");
        assertThat(yEnc.get("type")).isEqualTo("quantitative");
        assertThat(yEnc.get("title")).isEqualTo("Salary (USD)");
    }

    @Test
    @DisplayName("Should build valid Bar chart specification with category encoding")
    void testBarChartSpec() {
        Chart chart = df.plot().bar("department", "headcount", "department")
                .title("Headcount by Dept")
                .color("#3b82f6");

        Map<String, Object> spec = chart.toSpecMap();

        @SuppressWarnings("unchecked")
        Map<String, Object> mark = (Map<String, Object>) spec.get("mark");
        assertThat(mark.get("type")).isEqualTo("bar");

        @SuppressWarnings("unchecked")
        Map<String, Object> encoding = (Map<String, Object>) spec.get("encoding");
        @SuppressWarnings("unchecked")
        Map<String, Object> xEnc = (Map<String, Object>) encoding.get("x");
        @SuppressWarnings("unchecked")
        Map<String, Object> yEnc = (Map<String, Object>) encoding.get("y");
        @SuppressWarnings("unchecked")
        Map<String, Object> colorEnc = (Map<String, Object>) encoding.get("color");

        assertThat(xEnc.get("field")).isEqualTo("department");
        assertThat(xEnc.get("type")).isEqualTo("nominal");
        assertThat(yEnc.get("field")).isEqualTo("headcount");
        assertThat(yEnc.get("type")).isEqualTo("quantitative");
        assertThat(colorEnc.get("field")).isEqualTo("department");
    }

    @Test
    @DisplayName("Should build valid Scatter plot with color and size encodings")
    void testScatterPlotSpec() {
        Chart chart = df.plot().scatter("experience", "salary", "department", "age")
                .title("Salary vs Experience")
                .width(800)
                .height(500);

        Map<String, Object> spec = chart.toSpecMap();
        assertThat(spec.get("width")).isEqualTo(800);
        assertThat(spec.get("height")).isEqualTo(500);

        @SuppressWarnings("unchecked")
        Map<String, Object> mark = (Map<String, Object>) spec.get("mark");
        assertThat(mark.get("type")).isEqualTo("circle");

        @SuppressWarnings("unchecked")
        Map<String, Object> encoding = (Map<String, Object>) spec.get("encoding");
        assertThat(((Map<?, ?>) encoding.get("x")).get("field")).isEqualTo("experience");
        assertThat(((Map<?, ?>) encoding.get("y")).get("field")).isEqualTo("salary");
        assertThat(((Map<?, ?>) encoding.get("color")).get("field")).isEqualTo("department");
        assertThat(((Map<?, ?>) encoding.get("size")).get("field")).isEqualTo("age");
    }

    @Test
    @DisplayName("Should build valid Histogram specification with binning")
    void testHistogramSpec() {
        Chart chart = df.plot().hist("age", 10)
                .title("Age Distribution");

        Map<String, Object> spec = chart.toSpecMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> mark = (Map<String, Object>) spec.get("mark");
        assertThat(mark.get("type")).isEqualTo("bar");

        @SuppressWarnings("unchecked")
        Map<String, Object> encoding = (Map<String, Object>) spec.get("encoding");
        @SuppressWarnings("unchecked")
        Map<String, Object> xEnc = (Map<String, Object>) encoding.get("x");
        @SuppressWarnings("unchecked")
        Map<String, Object> yEnc = (Map<String, Object>) encoding.get("y");

        assertThat(xEnc.get("field")).isEqualTo("age");
        assertThat(xEnc.get("type")).isEqualTo("quantitative");
        @SuppressWarnings("unchecked")
        Map<String, Object> bin = (Map<String, Object>) xEnc.get("bin");
        assertThat(bin.get("maxbins")).isEqualTo(10);

        assertThat(yEnc.get("aggregate")).isEqualTo("count");
        assertThat(yEnc.get("title")).isEqualTo("Count");
    }

    @Test
    @DisplayName("Should build valid Box plot specification")
    void testBoxPlotSpec() {
        Chart chart = df.plot().box("department", "salary")
                .title("Salary Spread");

        Map<String, Object> spec = chart.toSpecMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> mark = (Map<String, Object>) spec.get("mark");
        assertThat(mark.get("type")).isEqualTo("boxplot");
        assertThat(mark.get("extent")).isEqualTo(1.5);

        @SuppressWarnings("unchecked")
        Map<String, Object> encoding = (Map<String, Object>) spec.get("encoding");
        assertThat(((Map<?, ?>) encoding.get("x")).get("field")).isEqualTo("department");
        assertThat(((Map<?, ?>) encoding.get("y")).get("field")).isEqualTo("salary");
    }

    @Test
    @DisplayName("Should build valid Area chart specification")
    void testAreaChartSpec() {
        Chart chart = df.plot().area("join_date", "salary")
                .title("Salary Timeline");

        Map<String, Object> spec = chart.toSpecMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> mark = (Map<String, Object>) spec.get("mark");
        assertThat(mark.get("type")).isEqualTo("area");

        @SuppressWarnings("unchecked")
        Map<String, Object> encoding = (Map<String, Object>) spec.get("encoding");
        assertThat(((Map<?, ?>) encoding.get("x")).get("type")).isEqualTo("temporal");
        assertThat(((Map<?, ?>) encoding.get("y")).get("type")).isEqualTo("quantitative");
    }

    @Test
    @DisplayName("Should build valid Heatmap specification")
    void testHeatmapSpec() {
        Chart chart = df.plot().heatmap("department", "age", "salary")
                .title("Salary Heatmap");

        Map<String, Object> spec = chart.toSpecMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> mark = (Map<String, Object>) spec.get("mark");
        assertThat(mark.get("type")).isEqualTo("rect");

        @SuppressWarnings("unchecked")
        Map<String, Object> encoding = (Map<String, Object>) spec.get("encoding");
        assertThat(((Map<?, ?>) encoding.get("x")).get("field")).isEqualTo("department");
        assertThat(((Map<?, ?>) encoding.get("y")).get("field")).isEqualTo("age");
        assertThat(((Map<?, ?>) encoding.get("color")).get("field")).isEqualTo("salary");
    }

    @Test
    @DisplayName("Should serialize specification to valid Vega-Lite JSON")
    void testJsonSerialization() {
        Chart chart = df.plot().scatter("experience", "salary")
                .title("Experience vs Salary")
                .colors(List.of("#ef4444", "#3b82f6"))
                .theme("quartz");

        String json = chart.toVegaLiteJson();
        assertThat(json).isNotNull();
        assertThat(json).contains("\"$schema\" : \"https://vega.github.io/schema/vega-lite/v5.json\"");
        assertThat(json).contains("\"field\" : \"experience\"");
        assertThat(json).contains("\"field\" : \"salary\"");
        assertThat(json).contains("\"theme\" : \"quartz\"");
    }
}
