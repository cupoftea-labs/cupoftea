package com.cupoftea.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.Column;
import com.cupoftea.table.Group;
import com.cupoftea.utils.configurations.PrinterConfiguration;

public class PrinterTest {

    private CupOfTea createSampleDataFrame(int rowCount, int columnCount) {
        CupOfTea df = new CupOfTea();
        for (int c = 0; c < columnCount; c++) {
            final String colName = "col_" + c;
            df = df.addColumn(colName, Integer.class, row -> 0);
        }
        for (int r = 0; r < rowCount; r++) {
            HashMap<String, Object> row = new HashMap<>();
            for (int c = 0; c < columnCount; c++) {
                row.put("col_" + c, "val_" + r + "_" + c);
            }
            df = df.addRow(row);
        }
        return df;
    }

    @Test
    @DisplayName("Should print small DataFrame with default configuration")
    void test_defaultPrint_smallDataFrame() {
        CupOfTea df = createSampleDataFrame(3, 2);
        String output = df.printToString();

        assertThat(output).isNotNull();
        assertThat(output).contains("Showing 3 registers");
        assertThat(output).contains("col_0");
        assertThat(output).contains("col_1");
        assertThat(output).contains("val_0_0");
    }

    @Test
    @DisplayName("Should ensure table border and data lines have strictly equal lengths")
    void test_lineLengths_areStrictlyEqual() {
        CupOfTea df = createSampleDataFrame(5, 3);
        String output = df.printToString();

        String[] lines = output.split(System.lineSeparator());
        assertThat(lines[0]).isEqualTo("Showing 5 registers");

        int expectedLength = lines[1].length();
        for (int i = 1; i < lines.length; i++) {
            assertThat(lines[i].length())
                .withFailMessage("Line %d length mismatch: %s", i, lines[i])
                .isEqualTo(expectedLength);
        }
    }

    @Test
    @DisplayName("Should truncate columns with ellipsis when terminal width is constrained")
    void test_terminalWidth_truncatesColumnsWithEllipsis() {
        CupOfTea df = createSampleDataFrame(5, 15);

        PrinterConfiguration config = new PrinterConfiguration()
                .setTerminalWidth(60);

        String output = df.printToString(config);
        String[] lines = output.split(System.lineSeparator());

        assertThat(lines[0]).contains("columns)");
        assertThat(output).contains("...");

        for (String line : lines) {
            assertThat(line.length())
                .withFailMessage("Line exceeds terminal width: length %d > 60: %s", line.length(), line)
                .isLessThanOrEqualTo(60);
        }
    }

    @Test
    @DisplayName("Should limit displayed columns with maxColumns configuration")
    void test_maxColumns_limitsDisplayedColumns() {
        CupOfTea df = createSampleDataFrame(5, 10);

        PrinterConfiguration config = new PrinterConfiguration()
                .setTerminalWidth(200)
                .setMaxColumns(4);

        String output = df.printToString(config);
        assertThat(output).contains("...");
        assertThat(output).contains("(4 of 10 columns)");
    }

    @Test
    @DisplayName("Should limit displayed rows with maxRows configuration")
    void test_maxRows_limitsDisplayedRows() {
        CupOfTea df = createSampleDataFrame(20, 2);

        PrinterConfiguration config = new PrinterConfiguration()
                .setMaxRows(5);

        String output = df.printToString(config);
        assertThat(output).contains("Showing only the first 5 registers");
        assertThat(output).contains("val_4_0");
        assertThat(output).doesNotContain("val_5_0");
    }

    @Test
    @DisplayName("Should display column type in header when showColumnType is enabled")
    void test_showColumnType_displaysTypeInHeader() {
        CupOfTea df = new CupOfTea();
        df = df.addColumn("name", String.class, row -> "Alice");
        df = df.addColumn("age", Integer.class, row -> 30);

        HashMap<String, Object> r1 = new HashMap<>();
        r1.put("name", "Alice");
        r1.put("age", 30);
        df = df.addRow(r1);

        PrinterConfiguration config = new PrinterConfiguration()
                .setShowColumnType(true);

        String output = df.printToString(config);
        assertThat(output).contains("name (String)");
        assertThat(output).contains("age (Integer)");
    }

    @Test
    @DisplayName("Should respect fixed defaultColumnWidth when autoAdjustColumnWidth is disabled")
    void test_autoAdjustColumnWidth_disabled() {
        CupOfTea df = createSampleDataFrame(2, 2);

        PrinterConfiguration config = new PrinterConfiguration()
                .setAutoAdjustColumnWidth(false)
                .setDefaultColumnWidth(15);

        String output = df.printToString(config);
        String[] lines = output.split(System.lineSeparator());
        assertThat(lines[1].length()).isEqualTo(33);
    }

    @Test
    @DisplayName("Should print message for empty DataFrame with no columns")
    void test_emptyDataFrame_noColumns() {
        CupOfTea df = new CupOfTea();
        String output = df.printToString();

        assertThat(output).contains("Empty DataFrame (0 rows, 0 columns)");
    }

    @Test
    @DisplayName("Should print headers for empty DataFrame with columns and zero rows")
    void test_emptyDataFrame_withColumnsZeroRows() {
        CupOfTea df = new CupOfTea();
        df = df.addColumn("id", Integer.class, row -> 0)
               .addColumn("title", String.class, row -> "");

        String output = df.printToString();
        assertThat(output).contains("Showing 0 registers");
        assertThat(output).contains("id");
        assertThat(output).contains("title");
    }

    @Test
    @DisplayName("Should render null values according to configuration")
    void test_nullValues_renderedAsConfiguredString() {
        CupOfTea df = new CupOfTea();
        df = df.addColumn("col", String.class, row -> null);

        HashMap<String, Object> r1 = new HashMap<>();
        r1.put("col", null);
        df = df.addRow(r1);

        PrinterConfiguration configDefault = new PrinterConfiguration();
        String outDefault = df.printToString(configDefault);
        assertThat(outDefault).contains("null");

        PrinterConfiguration configCustom = new PrinterConfiguration().setStringNull("N/A");
        String outCustom = df.printToString(configCustom);
        assertThat(outCustom).contains("N/A");
    }

    @Test
    @DisplayName("Should truncate long cell values with ellipsis")
    void test_longValues_truncatedWithEllipsis() {
        CupOfTea df = new CupOfTea();
        df = df.addColumn("description", String.class, row -> null);

        HashMap<String, Object> r1 = new HashMap<>();
        r1.put("description", "A very long text that exceeds maximum column width easily");
        df = df.addRow(r1);

        PrinterConfiguration config = new PrinterConfiguration()
                .setMaxColumnWidth(15);

        String output = df.printToString(config);
        assertThat(output).contains("...");
        String[] lines = output.split(System.lineSeparator());
        assertThat(lines[1].length()).isEqualTo(lines[2].length());
    }

    @Test
    @DisplayName("Should support custom description or hiding description")
    void test_customDescription_and_hideDescription() {
        CupOfTea df = createSampleDataFrame(2, 2);

        PrinterConfiguration customDescConfig = new PrinterConfiguration()
                .setDescription("Custom Report Table");
        String customOut = df.printToString(customDescConfig);
        assertThat(customOut).startsWith("Custom Report Table");

        PrinterConfiguration noDescConfig = new PrinterConfiguration()
                .setShowDescription(false);
        String noDescOut = df.printToString(noDescConfig);
        assertThat(noDescOut).startsWith("\u250C");
    }

    @Test
    @DisplayName("Should filter out Group columns from printed output")
    void test_groupedColumns_filteredOut() {
        CupOfTea df = new CupOfTea();
        df = df.addColumn("visibleCol", String.class, row -> "test");
        df.getHeaders().add(new Column("groupedCol", Group.class));

        HashMap<String, Object> r1 = new HashMap<>();
        r1.put("visibleCol", "test");
        r1.put("groupedCol", null);
        df = df.addRow(r1);

        String output = df.printToString();
        assertThat(output).contains("visibleCol");
        assertThat(output).doesNotContain("groupedCol");
    }

    @Test
    @DisplayName("Should redirect output to configured PrintStream")
    void test_printStream_redirectsOutput() {
        CupOfTea df = createSampleDataFrame(2, 2);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);

        PrinterConfiguration config = new PrinterConfiguration().setPrintStream(ps);
        df.print(config);

        String printed = baos.toString();
        assertThat(printed).contains("Showing 2 registers");
        assertThat(printed).contains("col_0");
    }

    @Test
    @DisplayName("Should return this DataFrame from all print overloads")
    void test_print_overloads_return_this() {
        CupOfTea df = createSampleDataFrame(3, 2);
        assertThat(df.print()).isSameAs(df);
        assertThat(df.print(2)).isSameAs(df);
        assertThat(df.print(new PrinterConfiguration().setMaxRows(1))).isSameAs(df);
    }
}
