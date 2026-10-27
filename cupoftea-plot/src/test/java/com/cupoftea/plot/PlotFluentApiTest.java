package com.cupoftea.plot;

import java.io.File;
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

class PlotFluentApiTest {

    private CupOfTea df;

    @BeforeEach
    void setUp() {
        df = new CupOfTea();
        ArrayList<Column> headers = new ArrayList<>();
        headers.add(new Column("experience", Double.class));
        headers.add(new Column("salary", Double.class));
        headers.add(new Column("age", Integer.class));
        headers.add(new Column("department", String.class));
        headers.add(new Column("headcount", Integer.class));
        df.setHeaders(headers);

        for (int i = 1; i <= 10; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("experience", 2.0 * i);
            row.put("salary", 45000.0 + i * 5000);
            row.put("age", 20 + i);
            row.put("department", i % 2 == 0 ? "Engineering" : "Product");
            row.put("headcount", 5 * i);
            df.addRow(row);
        }
    }

    @Test
    @DisplayName("Should execute exact snippet from issue description")
    void testIssueExampleSnippet(@TempDir File tempDir) {
        File chartFile = new File(tempDir, "chart.html");

        // df.plot().scatter("experience", "salary").title("Salary vs Experience").show();
        Chart scatter = df.plot().scatter("experience", "salary").title("Salary vs Experience").show();
        assertThat(scatter).isNotNull();

        // df.plot().hist("age", 20).show();
        Chart hist = df.plot().hist("age", 20).show();
        assertThat(hist).isNotNull();

        // df.plot().bar("department", "headcount").saveHtml("chart.html");
        Chart bar = df.plot().bar("department", "headcount").saveHtml(chartFile.getAbsolutePath());
        assertThat(bar).isNotNull();
        assertThat(chartFile).exists();
    }

    @Test
    @DisplayName("Should support Plots static entrypoints")
    void testPlotsStaticEntrypoint() {
        Chart line = Plots.line(df, "experience", "salary");
        assertThat(line).isNotNull();
        assertThat(line.toSpecMap()).containsKey("mark");

        Chart box = Plots.box(df, "department", "salary");
        assertThat(box).isNotNull();

        Chart heatmap = Plots.heatmap(df, "department", "age", "salary");
        assertThat(heatmap).isNotNull();
    }
}
