package com.CupOfTea.utils;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.table.Column;
import com.CupOfTea.table.Group;
import com.CupOfTea.utils.configurations.PrinterConfiguration;

import junit.framework.TestCase;

public class PrinterTest extends TestCase {

    private CupOfTea createSampleDataFrame(int rowCount, int columnCount) throws Exception {
        CupOfTea df = new CupOfTea();
        for (int c = 0; c < columnCount; c++) {
            final String colName = "col_" + c;
            df.addColumn(colName, Integer.class, row -> 0);
        }
        for (int r = 0; r < rowCount; r++) {
            HashMap<String, Object> row = new HashMap<>();
            for (int c = 0; c < columnCount; c++) {
                row.put("col_" + c, "val_" + r + "_" + c);
            }
            df.getRows().add(row);
        }
        return df;
    }

    public void test_defaultPrint_smallDataFrame() throws Exception {
        CupOfTea df = createSampleDataFrame(3, 2);
        String output = df.printToString();

        assertNotNull(output);
        assertTrue(output.contains("Showing 3 registers"));
        assertTrue(output.contains("col_0"));
        assertTrue(output.contains("col_1"));
        assertTrue(output.contains("val_0_0"));
    }

    public void test_lineLengths_areStrictlyEqual() throws Exception {
        CupOfTea df = createSampleDataFrame(5, 3);
        String output = df.printToString();

        String[] lines = output.split(System.lineSeparator());
        // First line is description
        assertEquals("Showing 5 registers", lines[0]);

        // Lines 1 to end are table borders and rows
        int expectedLength = lines[1].length();
        for (int i = 1; i < lines.length; i++) {
            assertEquals("Line " + i + " length mismatch: " + lines[i], expectedLength, lines[i].length());
        }
    }

    public void test_terminalWidth_truncatesColumnsWithEllipsis() throws Exception {
        CupOfTea df = createSampleDataFrame(5, 15);

        PrinterConfiguration config = new PrinterConfiguration()
                .setTerminalWidth(60);

        String output = df.printToString(config);
        String[] lines = output.split(System.lineSeparator());

        // Check that description shows truncated columns
        assertTrue(lines[0].contains("columns)"));
        assertTrue(output.contains("..."));

        // Verify that no line in the table exceeds terminal width
        for (String line : lines) {
            assertTrue("Line exceeds terminal width: length " + line.length() + " > 60: " + line,
                    line.length() <= 60);
        }
    }

    public void test_maxColumns_limitsDisplayedColumns() throws Exception {
        CupOfTea df = createSampleDataFrame(5, 10);

        PrinterConfiguration config = new PrinterConfiguration()
                .setTerminalWidth(200)
                .setMaxColumns(4);

        String output = df.printToString(config);
        assertTrue(output.contains("..."));
        assertTrue(output.contains("(4 of 10 columns)"));
    }

    public void test_maxRows_limitsDisplayedRows() throws Exception {
        CupOfTea df = createSampleDataFrame(20, 2);

        PrinterConfiguration config = new PrinterConfiguration()
                .setMaxRows(5);

        String output = df.printToString(config);
        assertTrue(output.contains("Showing only the first 5 registers"));
        assertTrue(output.contains("val_4_0"));
        assertFalse(output.contains("val_5_0"));
    }

    public void test_showColumnType_displaysTypeInHeader() throws Exception {
        CupOfTea df = new CupOfTea();
        df.addColumn("name", String.class, row -> "Alice");
        df.addColumn("age", Integer.class, row -> 30);

        HashMap<String, Object> r1 = new HashMap<>();
        r1.put("name", "Alice");
        r1.put("age", 30);
        df.getRows().add(r1);

        PrinterConfiguration config = new PrinterConfiguration()
                .setShowColumnType(true);

        String output = df.printToString(config);
        assertTrue(output.contains("name (String)"));
        assertTrue(output.contains("age (Integer)"));
    }

    public void test_autoAdjustColumnWidth_disabled() throws Exception {
        CupOfTea df = createSampleDataFrame(2, 2);

        PrinterConfiguration config = new PrinterConfiguration()
                .setAutoAdjustColumnWidth(false)
                .setDefaultColumnWidth(15);

        String output = df.printToString(config);
        String[] lines = output.split(System.lineSeparator());
        // Top border should have 1 + 15 + 1 + 15 + 1 = 33 chars
        assertEquals(33, lines[1].length());
    }

    public void test_emptyDataFrame_noColumns() {
        CupOfTea df = new CupOfTea();
        String output = df.printToString();

        assertTrue(output.contains("Empty DataFrame (0 rows, 0 columns)"));
    }

    public void test_emptyDataFrame_withColumnsZeroRows() throws Exception {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class, row -> 0);
        df.addColumn("title", String.class, row -> "");

        String output = df.printToString();
        assertTrue(output.contains("Showing 0 registers"));
        assertTrue(output.contains("id"));
        assertTrue(output.contains("title"));
    }

    public void test_nullValues_renderedAsConfiguredString() throws Exception {
        CupOfTea df = new CupOfTea();
        df.addColumn("col", String.class, row -> null);

        HashMap<String, Object> r1 = new HashMap<>();
        r1.put("col", null);
        df.getRows().add(r1);

        PrinterConfiguration configDefault = new PrinterConfiguration();
        String outDefault = df.printToString(configDefault);
        assertTrue(outDefault.contains("null"));

        PrinterConfiguration configCustom = new PrinterConfiguration().setStringNull("N/A");
        String outCustom = df.printToString(configCustom);
        assertTrue(outCustom.contains("N/A"));
    }

    public void test_longValues_truncatedWithEllipsis() throws Exception {
        CupOfTea df = new CupOfTea();
        df.addColumn("description", String.class, row -> null);

        HashMap<String, Object> r1 = new HashMap<>();
        r1.put("description", "A very long text that exceeds maximum column width easily");
        df.getRows().add(r1);

        PrinterConfiguration config = new PrinterConfiguration()
                .setMaxColumnWidth(15);

        String output = df.printToString(config);
        assertTrue(output.contains("..."));
        String[] lines = output.split(System.lineSeparator());
        assertEquals(lines[1].length(), lines[2].length());
    }

    public void test_customDescription_and_hideDescription() throws Exception {
        CupOfTea df = createSampleDataFrame(2, 2);

        PrinterConfiguration customDescConfig = new PrinterConfiguration()
                .setDescription("Custom Report Table");
        String customOut = df.printToString(customDescConfig);
        assertTrue(customOut.startsWith("Custom Report Table"));

        PrinterConfiguration noDescConfig = new PrinterConfiguration()
                .setShowDescription(false);
        String noDescOut = df.printToString(noDescConfig);
        assertTrue(noDescOut.startsWith("\u250C"));
    }

    public void test_groupedColumns_filteredOut() throws Exception {
        CupOfTea df = new CupOfTea();
        df.addColumn("visibleCol", String.class, row -> "test");
        df.getHeaders().add(new Column("groupedCol", Group.class));

        HashMap<String, Object> r1 = new HashMap<>();
        r1.put("visibleCol", "test");
        r1.put("groupedCol", null);
        df.getRows().add(r1);

        String output = df.printToString();
        assertTrue(output.contains("visibleCol"));
        assertFalse(output.contains("groupedCol"));
    }

    public void test_printStream_redirectsOutput() throws Exception {
        CupOfTea df = createSampleDataFrame(2, 2);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);

        PrinterConfiguration config = new PrinterConfiguration().setPrintStream(ps);
        df.print(config);

        String printed = baos.toString();
        assertTrue(printed.contains("Showing 2 registers"));
        assertTrue(printed.contains("col_0"));
    }

    public void test_print_overloads_return_this() throws Exception {
        CupOfTea df = createSampleDataFrame(3, 2);
        assertSame(df, df.print());
        assertSame(df, df.print(2));
        assertSame(df, df.print(new PrinterConfiguration().setMaxRows(1)));
    }
}
