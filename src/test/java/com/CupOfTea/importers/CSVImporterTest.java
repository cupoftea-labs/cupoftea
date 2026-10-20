package com.CupOfTea.importers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.importers.configurations.CSVImporterConfiguration;

public class CSVImporterTest {

    private File createTempCsv(String content, Charset charset) throws IOException {
        File tempFile = File.createTempFile("cupoftea_csv_test_", ".csv");
        tempFile.deleteOnExit();
        Files.write(tempFile.toPath(), content.getBytes(charset));
        return tempFile;
    }

    @Test
    @DisplayName("Should import CSV with headers and return populated DataFrame")
    void test_GivenACSVFileWithHeaders_WhenImporting_ShouldReturnACupOfTeaDataFrame() throws Exception {
        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 500;

        File testFile = new File("src/test/java/com/CupOfTea/testFiles/csv_sample.csv");

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(testFile, true);

        assertThat(cupOfTea.getHeaders()).hasSize(expectedColumnsNumber);
        assertThat(cupOfTea.getRows()).hasSize(expectedRowsNumber);
        assertThat(cupOfTea.getHeaders().get(0).columnName()).isEqualTo("nome");
        assertThat(cupOfTea.getHeaders().get(1).columnName()).isEqualTo("idade");
        assertThat(cupOfTea.getHeaders().get(2).columnName()).isEqualTo("cidade");
        assertThat(cupOfTea.getHeaders().get(0).columnType()).isEqualTo(String.class);
        assertThat(cupOfTea.getHeaders().get(1).columnType()).isEqualTo(String.class);
        assertThat(cupOfTea.getHeaders().get(2).columnType()).isEqualTo(String.class);
        assertThat(cupOfTea.getRow(0).get("nome")).isEqualTo("Pedro Lucas");
        assertThat(cupOfTea.getRow(0).get("idade")).isEqualTo("11");
        assertThat(cupOfTea.getRow(0).get("cidade")).isEqualTo("Santa");
        assertThat(cupOfTea.getRow(1).get("cidade")).isEqualTo("Nipoã");
    }

    @Test
    @DisplayName("Should generate sequential zero-based headers when CSV has no headers")
    void test_GivenACSVFileWithoutHeaders_WhenImporting_ShouldGenerateSequentialZeroBasedHeaders() throws Exception {
        String csv = "Alice,30,Wonderland\nBob,25,Builderland\n";
        File file = createTempCsv(csv, StandardCharsets.UTF_8);

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(file, false);

        assertThat(cupOfTea.getHeaders()).hasSize(3);
        assertThat(cupOfTea.getHeaders().get(0).columnName()).isEqualTo("0");
        assertThat(cupOfTea.getHeaders().get(1).columnName()).isEqualTo("1");
        assertThat(cupOfTea.getHeaders().get(2).columnName()).isEqualTo("2");
        assertThat(cupOfTea.getRows()).hasSize(2);
        assertThat(cupOfTea.getRow(0).get("0")).isEqualTo("Alice");
        assertThat(cupOfTea.getRow(0).get("1")).isEqualTo("30");
        assertThat(cupOfTea.getRow(0).get("2")).isEqualTo("Wonderland");
        assertThat(cupOfTea.getRow(1).get("0")).isEqualTo("Bob");
    }

    @Test
    @DisplayName("Should parse quoted commas and escaped quotes correctly using OpenCSV")
    void test_GivenACSVWithQuotedCommasAndEscapedQuotes_WhenImportingWithOpenCSV_ShouldParseColumnsCorrectly() throws Exception {
        String csv = "name,description,location\n"
            + "\"Silva, João\",\"Said \"\"Hello, World!\"\"\",\"São Paulo, SP\"\n"
            + "\"Maria\",\"Escaped \\\"quote\\\" inside\",\"Rio, RJ\"\n";
        File file = createTempCsv(csv, StandardCharsets.UTF_8);

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(file);

        assertThat(cupOfTea.getHeaders()).hasSize(3);
        assertThat(cupOfTea.getRows()).hasSize(2);

        assertThat(cupOfTea.getRow(0).get("name")).isEqualTo("Silva, João");
        assertThat(cupOfTea.getRow(0).get("description")).isEqualTo("Said \"Hello, World!\"");
        assertThat(cupOfTea.getRow(0).get("location")).isEqualTo("São Paulo, SP");

        assertThat(cupOfTea.getRow(1).get("name")).isEqualTo("Maria");
        assertThat(cupOfTea.getRow(1).get("description")).isEqualTo("Escaped \"quote\" inside");
        assertThat(cupOfTea.getRow(1).get("location")).isEqualTo("Rio, RJ");
    }

    @Test
    @DisplayName("Should handle multiline values and empty quoted fields in CSV")
    void test_GivenACSVWithMultilineValuesAndEmptyQuotedFields_WhenImporting_ShouldParseCorrectly() throws Exception {
        String csv = "id,notes,status\n"
            + "1,\"Line one\nLine two\",\"ACTIVE\"\n"
            + "2,\"\",\"PENDING\"\n"
            + "3,\"Field with, comma and \"\"quotes\"\"\",\"DONE\"\n";
        File file = createTempCsv(csv, StandardCharsets.UTF_8);

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(file);

        assertThat(cupOfTea.getRows()).hasSize(3);
        assertThat(cupOfTea.getRow(0).get("notes")).isEqualTo("Line one\nLine two");
        assertThat(cupOfTea.getRow(0).get("status")).isEqualTo("ACTIVE");
        assertThat(cupOfTea.getRow(1).get("notes")).isEqualTo("");
        assertThat(cupOfTea.getRow(1).get("status")).isEqualTo("PENDING");
        assertThat(cupOfTea.getRow(2).get("notes")).isEqualTo("Field with, comma and \"quotes\"");
    }

    @Test
    @DisplayName("Should parse CSV with custom delimiter and quote character")
    void test_GivenACSVWithCustomDelimiterAndQuoteChar_WhenImportingWithConfiguration_ShouldParseCorrectly() throws Exception {
        String csv = "id;title;notes\n"
            + "1;'Book; Part 1';'First edition'\n"
            + "2;'Book; Part 2';'Second edition'\n";
        File file = createTempCsv(csv, StandardCharsets.UTF_8);

        CSVImporterConfiguration config = new CSVImporterConfiguration()
            .setDelimiter(";")
            .setQuoteString("'");

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(file, config);

        assertThat(cupOfTea.getHeaders()).hasSize(3);
        assertThat(cupOfTea.getRows()).hasSize(2);
        assertThat(cupOfTea.getRow(0).get("title")).isEqualTo("Book; Part 1");
        assertThat(cupOfTea.getRow(0).get("notes")).isEqualTo("First edition");
        assertThat(cupOfTea.getRow(1).get("title")).isEqualTo("Book; Part 2");
    }

    @Test
    @DisplayName("Should preserve characters when importing with custom charset")
    void test_GivenACSVWithCustomCharset_WhenImportingWithConfiguredCharset_ShouldPreserveCharacters() throws Exception {
        String csv = "nome,cidade\nJosé,São José dos Pinhais\nConceição,Maringá\n";
        File file = createTempCsv(csv, StandardCharsets.ISO_8859_1);

        CSVImporterConfiguration config = new CSVImporterConfiguration()
            .setCharset(StandardCharsets.ISO_8859_1);

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(file, config);

        assertThat(cupOfTea.getRows()).hasSize(2);
        assertThat(cupOfTea.getRow(0).get("nome")).isEqualTo("José");
        assertThat(cupOfTea.getRow(0).get("cidade")).isEqualTo("São José dos Pinhais");
        assertThat(cupOfTea.getRow(1).get("nome")).isEqualTo("Conceição");
        assertThat(cupOfTea.getRow(1).get("cidade")).isEqualTo("Maringá");
    }

    @Test
    @DisplayName("Should strip BOM from first header when UTF-8 BOM is present")
    void test_GivenACSVWithUtf8Bom_WhenImporting_ShouldStripBomFromFirstHeader() throws Exception {
        String csv = "\uFEFFid,name\n1,Alice\n";
        File file = createTempCsv(csv, StandardCharsets.UTF_8);

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(file, true);

        assertThat(cupOfTea.getHeaders().get(0).columnName()).isEqualTo("id");
        assertThat(cupOfTea.getRow(0).get("id")).isEqualTo("1");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when row has fewer columns than header")
    void test_GivenACSVWithRaggedRowsTooFewColumns_WhenImporting_ShouldThrowIllegalArgumentException() throws Exception {
        String csv = "a,b,c\n1,2,3\n4,5\n";
        File file = createTempCsv(csv, StandardCharsets.UTF_8);

        CupOfTea cupOfTea = new CupOfTea();
        assertThatThrownBy(() -> cupOfTea.importFromCsv(file, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Row length mismatch");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when row has extra columns")
    void test_GivenACSVWithRaggedRowsTooManyColumns_WhenImporting_ShouldThrowIllegalArgumentException() throws Exception {
        String csv = "a,b,c\n1,2,3\n4,5,6,7\n";
        File file = createTempCsv(csv, StandardCharsets.UTF_8);

        CupOfTea cupOfTea = new CupOfTea();
        assertThatThrownBy(() -> cupOfTea.importFromCsv(file, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Row length mismatch");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for ragged rows when hasHeaders is false")
    void test_GivenACSVWithoutHeadersAndRaggedRows_WhenImporting_ShouldThrowIllegalArgumentException() throws Exception {
        String csv = "1,2,3\n4,5\n";
        File file = createTempCsv(csv, StandardCharsets.UTF_8);

        CupOfTea cupOfTea = new CupOfTea();
        assertThatThrownBy(() -> cupOfTea.importFromCsv(file, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Row length mismatch");
    }

    @Test
    @DisplayName("Should infer Integer and Double column types when inferNumericTypes is enabled")
    void test_GivenACSVWithNumericColumns_WhenInferNumericTypesIsEnabled_ShouldInferIntegerAndDoubleTypes() throws Exception {
        String csv = "item,quantity,price,code\n"
            + "Apple,10,1.99,4D\n"
            + "Banana,25,0.75,3F\n"
            + "Orange,5,2,9A\n";
        File file = createTempCsv(csv, StandardCharsets.UTF_8);

        CSVImporterConfiguration config = new CSVImporterConfiguration()
            .setInferNumericTypes(true);

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(file, config);

        assertThat(cupOfTea.getColumnHeader("item").columnType()).isEqualTo(String.class);
        assertThat(cupOfTea.getColumnHeader("quantity").columnType()).isEqualTo(Integer.class);
        assertThat(cupOfTea.getColumnHeader("price").columnType()).isEqualTo(Double.class);
        assertThat(cupOfTea.getColumnHeader("code").columnType()).isEqualTo(String.class);

        assertThat(cupOfTea.getRow(0).get("item")).isEqualTo("Apple");
        assertThat(cupOfTea.getRow(0).get("quantity")).isEqualTo(10);
        assertThat(cupOfTea.getRow(0).get("price")).isEqualTo(1.99);
        assertThat(cupOfTea.getRow(0).get("code")).isEqualTo("4D");
        assertThat(cupOfTea.getRow(2).get("price")).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Should convert empty numeric cells to null when inferNumericTypes is enabled")
    void test_GivenACSVWithEmptyCellsInNumericColumn_WhenInferNumericTypesIsEnabled_ShouldConvertEmptyToNull() throws Exception {
        String csv = "name,score\nAlice,100\nBob,\nCharlie,85\n";
        File file = createTempCsv(csv, StandardCharsets.UTF_8);

        CSVImporterConfiguration config = new CSVImporterConfiguration()
            .setInferNumericTypes(true);

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(file, config);

        assertThat(cupOfTea.getColumnHeader("score").columnType()).isEqualTo(Integer.class);
        assertThat(cupOfTea.getRow(0).get("score")).isEqualTo(100);
        assertThat(cupOfTea.getRow(1).get("score")).isNull();
        assertThat(cupOfTea.getRow(2).get("score")).isEqualTo(85);
    }

    @Test
    @DisplayName("Should replace previous data when reimporting CSV")
    void test_GivenPopulatedDataFrame_WhenReimportingCSV_ShouldReplacePreviousData() throws Exception {
        File firstFile = createTempCsv("a,b\n1,2\n3,4\n", StandardCharsets.UTF_8);
        File secondFile = createTempCsv("x,y,z\n9,8,7\n", StandardCharsets.UTF_8);

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(firstFile, true);
        assertThat(cupOfTea.getHeaders()).hasSize(2);
        assertThat(cupOfTea.getRows()).hasSize(2);

        cupOfTea.importFromCsv(secondFile, true);
        assertThat(cupOfTea.getHeaders()).hasSize(3);
        assertThat(cupOfTea.getRows()).hasSize(1);
        assertThat(cupOfTea.getHeaders().get(0).columnName()).isEqualTo("x");
        assertThat(cupOfTea.getRow(0).get("x")).isEqualTo("9");
    }

    @Test
    @DisplayName("Should return empty DataFrame for empty CSV file")
    void test_GivenEmptyCSVFile_WhenImporting_ShouldReturnEmptyDataFrame() throws Exception {
        File emptyFile = createTempCsv("", StandardCharsets.UTF_8);

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromCsv(emptyFile, true);

        assertThat(cupOfTea.getHeaders()).isEmpty();
        assertThat(cupOfTea.getRows()).isEmpty();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for null arguments or invalid config")
    void test_GivenNullArguments_WhenImportingOrConfiguring_ShouldThrowIllegalArgumentException() {
        CupOfTea cupOfTea = new CupOfTea();

        assertThatThrownBy(() -> cupOfTea.importFromCsv(null, true))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new CSVImporterConfiguration().setDelimiter(""))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new CSVImporterConfiguration().setCharset((Charset) null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
