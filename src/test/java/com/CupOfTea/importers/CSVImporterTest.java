package com.CupOfTea.importers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.importers.configurations.CSVImporterConfiguration;

import junit.framework.TestCase;

public class CSVImporterTest extends TestCase {

    private File createTempCsv(String content, Charset charset) throws IOException {
        File tempFile = File.createTempFile("cupoftea_csv_test_", ".csv");
        tempFile.deleteOnExit();
        Files.write(tempFile.toPath(), content.getBytes(charset));
        return tempFile;
    }

    public void test_GivenACSVFileWithHeaders_WhenImporting_ShouldReturnACupOfTeaDataFrame() throws Exception {

        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 500;

        File testFile = new File("src/test/java/com/CupOfTea/testFiles/csv_sample.csv");

        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromCsv(testFile, true);

        assertEquals(expectedColumnsNumber, cupOfTea.getHeaders().size());
        assertEquals(expectedRowsNumber, cupOfTea.getRows().size());
        assertEquals("nome", cupOfTea.getHeaders().get(0).columnName());
        assertEquals("idade", cupOfTea.getHeaders().get(1).columnName());
        assertEquals("cidade", cupOfTea.getHeaders().get(2).columnName());
        assertEquals(String.class, cupOfTea.getHeaders().get(0).columnType());
        assertEquals(String.class, cupOfTea.getHeaders().get(1).columnType());
        assertEquals(String.class, cupOfTea.getHeaders().get(2).columnType());
        assertEquals("Pedro Lucas", cupOfTea.getRow(0).get("nome"));
        assertEquals("11", cupOfTea.getRow(0).get("idade"));
        assertEquals("Santa", cupOfTea.getRow(0).get("cidade"));
        assertEquals("Nipoã", cupOfTea.getRow(1).get("cidade"));

    }

    public void test_GivenACSVFileWithoutHeaders_WhenImporting_ShouldGenerateSequentialZeroBasedHeaders() throws Exception {
        String csv = "Alice,30,Wonderland\nBob,25,Builderland\n";
        File file = createTempCsv(csv, StandardCharsets.UTF_8);

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(file, false);

        assertEquals(3, cupOfTea.getHeaders().size());
        assertEquals("0", cupOfTea.getHeaders().get(0).columnName());
        assertEquals("1", cupOfTea.getHeaders().get(1).columnName());
        assertEquals("2", cupOfTea.getHeaders().get(2).columnName());
        assertEquals(2, cupOfTea.getRows().size());
        assertEquals("Alice", cupOfTea.getRow(0).get("0"));
        assertEquals("30", cupOfTea.getRow(0).get("1"));
        assertEquals("Wonderland", cupOfTea.getRow(0).get("2"));
        assertEquals("Bob", cupOfTea.getRow(1).get("0"));
    }

    public void test_GivenACSVWithQuotedCommasAndEscapedQuotes_WhenImportingWithOpenCSV_ShouldParseColumnsCorrectly() throws Exception {
        String csv = "name,description,location\n"
            + "\"Silva, João\",\"Said \"\"Hello, World!\"\"\",\"São Paulo, SP\"\n"
            + "\"Maria\",\"Escaped \\\"quote\\\" inside\",\"Rio, RJ\"\n";
        File file = createTempCsv(csv, StandardCharsets.UTF_8);

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(file);

        assertEquals(3, cupOfTea.getHeaders().size());
        assertEquals(2, cupOfTea.getRows().size());

        assertEquals("Silva, João", cupOfTea.getRow(0).get("name"));
        assertEquals("Said \"Hello, World!\"", cupOfTea.getRow(0).get("description"));
        assertEquals("São Paulo, SP", cupOfTea.getRow(0).get("location"));

        assertEquals("Maria", cupOfTea.getRow(1).get("name"));
        assertEquals("Escaped \"quote\" inside", cupOfTea.getRow(1).get("description"));
        assertEquals("Rio, RJ", cupOfTea.getRow(1).get("location"));
    }

    public void test_GivenACSVWithCustomDelimiterAndQuoteChar_WhenImportingWithConfiguration_ShouldParseCorrectly() throws Exception {
        String csv = "id;title;notes\n"
            + "1;'Book; Part 1';'First edition'\n"
            + "2;'Book; Part 2';'Second edition'\n";
        File file = createTempCsv(csv, StandardCharsets.UTF_8);

        CSVImporterConfiguration config = new CSVImporterConfiguration()
            .setDelimiter(";")
            .setQuoteString("'");

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(file, config);

        assertEquals(3, cupOfTea.getHeaders().size());
        assertEquals(2, cupOfTea.getRows().size());
        assertEquals("Book; Part 1", cupOfTea.getRow(0).get("title"));
        assertEquals("First edition", cupOfTea.getRow(0).get("notes"));
        assertEquals("Book; Part 2", cupOfTea.getRow(1).get("title"));
    }

    public void test_GivenACSVWithCustomCharset_WhenImportingWithConfiguredCharset_ShouldPreserveCharacters() throws Exception {
        String csv = "nome,cidade\nJosé,São José dos Pinhais\nConceição,Maringá\n";
        File file = createTempCsv(csv, StandardCharsets.ISO_8859_1);

        CSVImporterConfiguration config = new CSVImporterConfiguration()
            .setCharset(StandardCharsets.ISO_8859_1);

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(file, config);

        assertEquals(2, cupOfTea.getRows().size());
        assertEquals("José", cupOfTea.getRow(0).get("nome"));
        assertEquals("São José dos Pinhais", cupOfTea.getRow(0).get("cidade"));
        assertEquals("Conceição", cupOfTea.getRow(1).get("nome"));
        assertEquals("Maringá", cupOfTea.getRow(1).get("cidade"));
    }

    public void test_GivenACSVWithUtf8Bom_WhenImporting_ShouldStripBomFromFirstHeader() throws Exception {
        String csv = "\uFEFFid,name\n1,Alice\n";
        File file = createTempCsv(csv, StandardCharsets.UTF_8);

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(file, true);

        assertEquals("id", cupOfTea.getHeaders().get(0).columnName());
        assertEquals("1", cupOfTea.getRow(0).get("id"));
    }

    public void test_GivenACSVWithRaggedRowsTooFewColumns_WhenImporting_ShouldThrowIllegalArgumentException() throws Exception {
        String csv = "a,b,c\n1,2,3\n4,5\n";
        File file = createTempCsv(csv, StandardCharsets.UTF_8);

        CupOfTea cupOfTea = new CupOfTea();
        try {
            cupOfTea.importFromCsv(file, true);
            fail("Expected IllegalArgumentException for ragged row with fewer columns");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Row length mismatch"));
        }
    }

    public void test_GivenACSVWithRaggedRowsTooManyColumns_WhenImporting_ShouldThrowIllegalArgumentException() throws Exception {
        String csv = "a,b,c\n1,2,3\n4,5,6,7\n";
        File file = createTempCsv(csv, StandardCharsets.UTF_8);

        CupOfTea cupOfTea = new CupOfTea();
        try {
            cupOfTea.importFromCsv(file, true);
            fail("Expected IllegalArgumentException for ragged row with extra columns");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Row length mismatch"));
        }
    }

    public void test_GivenACSVWithoutHeadersAndRaggedRows_WhenImporting_ShouldThrowIllegalArgumentException() throws Exception {
        String csv = "1,2,3\n4,5\n";
        File file = createTempCsv(csv, StandardCharsets.UTF_8);

        CupOfTea cupOfTea = new CupOfTea();
        try {
            cupOfTea.importFromCsv(file, false);
            fail("Expected IllegalArgumentException for ragged row when hasHeaders is false");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Row length mismatch"));
        }
    }

    public void test_GivenACSVWithNumericColumns_WhenInferNumericTypesIsEnabled_ShouldInferIntegerAndDoubleTypes() throws Exception {
        String csv = "item,quantity,price,code\n"
            + "Apple,10,1.99,4D\n"
            + "Banana,25,0.75,3F\n"
            + "Orange,5,2,9A\n";
        File file = createTempCsv(csv, StandardCharsets.UTF_8);

        CSVImporterConfiguration config = new CSVImporterConfiguration()
            .setInferNumericTypes(true);

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(file, config);

        assertEquals(String.class, cupOfTea.getColumnHeader("item").columnType());
        assertEquals(Integer.class, cupOfTea.getColumnHeader("quantity").columnType());
        assertEquals(Double.class, cupOfTea.getColumnHeader("price").columnType());
        assertEquals(String.class, cupOfTea.getColumnHeader("code").columnType());

        assertEquals("Apple", cupOfTea.getRow(0).get("item"));
        assertEquals(Integer.valueOf(10), cupOfTea.getRow(0).get("quantity"));
        assertEquals(Double.valueOf(1.99), cupOfTea.getRow(0).get("price"));
        assertEquals("4D", cupOfTea.getRow(0).get("code"));

        assertEquals(Double.valueOf(2.0), cupOfTea.getRow(2).get("price"));
    }

    public void test_GivenACSVWithEmptyCellsInNumericColumn_WhenInferNumericTypesIsEnabled_ShouldConvertEmptyToNull() throws Exception {
        String csv = "name,score\nAlice,100\nBob,\nCharlie,85\n";
        File file = createTempCsv(csv, StandardCharsets.UTF_8);

        CSVImporterConfiguration config = new CSVImporterConfiguration()
            .setInferNumericTypes(true);

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(file, config);

        assertEquals(Integer.class, cupOfTea.getColumnHeader("score").columnType());
        assertEquals(Integer.valueOf(100), cupOfTea.getRow(0).get("score"));
        assertNull(cupOfTea.getRow(1).get("score"));
        assertEquals(Integer.valueOf(85), cupOfTea.getRow(2).get("score"));
    }

    public void test_GivenPopulatedDataFrame_WhenReimportingCSV_ShouldReplacePreviousData() throws Exception {
        File firstFile = createTempCsv("a,b\n1,2\n3,4\n", StandardCharsets.UTF_8);
        File secondFile = createTempCsv("x,y,z\n9,8,7\n", StandardCharsets.UTF_8);

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(firstFile, true);
        assertEquals(2, cupOfTea.getHeaders().size());
        assertEquals(2, cupOfTea.getRows().size());

        cupOfTea.importFromCsv(secondFile, true);
        assertEquals(3, cupOfTea.getHeaders().size());
        assertEquals(1, cupOfTea.getRows().size());
        assertEquals("x", cupOfTea.getHeaders().get(0).columnName());
        assertEquals("9", cupOfTea.getRow(0).get("x"));
    }

    public void test_GivenEmptyCSVFile_WhenImporting_ShouldReturnEmptyDataFrame() throws Exception {
        File emptyFile = createTempCsv("", StandardCharsets.UTF_8);

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(emptyFile, true);

        assertEquals(0, cupOfTea.getHeaders().size());
        assertEquals(0, cupOfTea.getRows().size());
    }

    public void test_GivenNullArguments_WhenImportingOrConfiguring_ShouldThrowIllegalArgumentException() throws Exception {
        CupOfTea cupOfTea = new CupOfTea();
        try {
            cupOfTea.importFromCsv(null, true);
            fail("Expected IllegalArgumentException for null file");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            new CSVImporterConfiguration().setDelimiter("");
            fail("Expected IllegalArgumentException for empty delimiter");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            new CSVImporterConfiguration().setCharset((Charset) null);
            fail("Expected IllegalArgumentException for null charset");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

}
