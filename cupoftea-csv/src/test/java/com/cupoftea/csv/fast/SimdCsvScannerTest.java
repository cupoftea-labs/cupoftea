package com.cupoftea.csv.fast;

import static com.cupoftea.table.Schema.BIG_DECIMAL;
import static com.cupoftea.table.Schema.BOOLEAN;
import static com.cupoftea.table.Schema.DATE;
import static com.cupoftea.table.Schema.DATETIME;
import static com.cupoftea.table.Schema.DOUBLE;
import static com.cupoftea.table.Schema.FLOAT;
import static com.cupoftea.table.Schema.INSTANT;
import static com.cupoftea.table.Schema.INT;
import static com.cupoftea.table.Schema.LONG;
import static com.cupoftea.table.Schema.STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.CsvParseException;
import com.cupoftea.importers.configurations.CSVImporterConfiguration;
import com.cupoftea.table.Schema;

public class SimdCsvScannerTest {

    @AfterEach
    void resetDetector() {
        SimdDetector.setForceDisabled(false);
        System.clearProperty(SimdDetector.SIMD_ENABLED_PROPERTY);
    }

    private File createTempCsv(String content) throws IOException {
        File file = File.createTempFile("simd_csv_test_", ".csv");
        file.deleteOnExit();
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    @DisplayName("SimdDetector detects Java Vector API support and honors toggles")
    void testSimdDetectorCapabilitiesAndToggles() {
        assertThat(SimdDetector.isHardwareSupported()).isTrue();
        assertThat(SimdDetector.isSupported()).isTrue();
        assertThat(SimdDetector.getVectorByteLength()).isGreaterThanOrEqualTo(16);
        assertThat(SimdDetector.getVectorBitSize()).isGreaterThanOrEqualTo(128);
        assertThat(SimdDetector.getSpeciesDescription()).isNotBlank();

        SimdDetector.setForceDisabled(true);
        assertThat(SimdDetector.isForceDisabled()).isTrue();
        assertThat(SimdDetector.isSupported()).isFalse();

        SimdDetector.setForceDisabled(false);
        assertThat(SimdDetector.isSupported()).isTrue();

        System.setProperty(SimdDetector.SIMD_ENABLED_PROPERTY, "false");
        assertThat(SimdDetector.isSupported()).isFalse();

        System.setProperty(SimdDetector.SIMD_ENABLED_PROPERTY, "true");
        assertThat(SimdDetector.isSupported()).isTrue();
    }

    @Test
    @DisplayName("SimdCsvScanner.prefixXor computes branchless quote enclosure mask accurately")
    void testPrefixXorEnclosureMask() {
        // Quote at bit 2 and bit 5 -> bits 2, 3, 4 should be 1, bit 5 should be 0
        long quoteMask = (1L << 2) | (1L << 5);
        long enclosure = SimdCsvScanner.prefixXor(quoteMask);
        assertThat(enclosure & 0b11111111L).isEqualTo(0b00011100L);

        // Escaped quotes (adjacent bits 3 and 4) inside outer quotes at bit 1 and bit 7
        long escapedQuotes = (1L << 1) | (1L << 3) | (1L << 4) | (1L << 7);
        long enc2 = SimdCsvScanner.prefixXor(escapedQuotes);
        // Bit 0: 0, Bit 1: 1, Bit 2: 1, Bit 3: 0 (on quote), Bit 4: 1 (on quote), Bit 5: 1, Bit 6: 1, Bit 7: 0
        assertThat((enc2 >>> 2) & 1L).isEqualTo(1L);
        assertThat((enc2 >>> 5) & 1L).isEqualTo(1L);
        assertThat((enc2 >>> 6) & 1L).isEqualTo(1L);
        assertThat((enc2 >>> 7) & 1L).isEqualTo(0L);
    }

    @Test
    @DisplayName("SimdCsvScanner.scan and scanToOffsets filter out delimiters inside quotes across multiple blocks")
    void testSimdScanAndScanToOffsets() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("col1_").append(i)
              .append(",\"quoted,with,commas;and\nnewlines_").append(i).append("\"")
              .append(",\"escaped \"\"quote,here\"\" ok\"")
              .append(",42\n");
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        List<Integer> callbackOffsets = new ArrayList<>();
        boolean endInQuote = SimdCsvScanner.scan(bytes, 0, bytes.length, (byte) ',', (byte) '"', (byte) '\\',
                (offset, ch) -> callbackOffsets.add(offset));

        assertThat(endInQuote).isFalse();
        // 20 rows * (3 commas + 1 newline) = 80 active structural boundaries
        assertThat(callbackOffsets).hasSize(80);

        int[] offsetBuf = new int[128];
        int count = SimdCsvScanner.scanToOffsets(bytes, 0, bytes.length, (byte) ',', (byte) '"', (byte) '\\', offsetBuf);
        assertThat(count).isEqualTo(80);
        for (int i = 0; i < count; i++) {
            assertThat(offsetBuf[i]).isEqualTo(callbackOffsets.get(i));
        }
    }

    @Test
    @DisplayName("SIMD scanner handles quotes and CRLF straddling 16/32/64-byte vector boundaries")
    void testVectorBoundaryStraddlingQuotesAndCrlf() {
        // Test every possible byte alignment offset from 0 to 70
        for (int pad = 0; pad <= 70; pad++) {
            String padding = "x".repeat(pad);
            String csv = "prefix,quoted,num\r\n"
                    + padding + ",\"a,b\"\"c,d\r\ne,f\",123\r\n"
                    + "tail,\"\"\"start_and_end\"\"\",456\r\n";

            byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

            CupOfTea simdDf = new CupOfTea();
            new FastCsvReader(new CSVImporterConfiguration().setSimdEnabled(true).setInferNumericTypes(true))
                    .readInto(bytes, simdDf);

            CupOfTea scalarDf = new CupOfTea();
            new FastCsvReader(new CSVImporterConfiguration().setSimdEnabled(false).setInferNumericTypes(true))
                    .readInto(bytes, scalarDf);

            assertThat(simdDf.size()).isEqualTo(2);
            assertThat(simdDf.getRow(0).get("prefix")).isEqualTo(pad == 0 ? null : padding);
            assertThat(simdDf.getRow(0).get("quoted")).isEqualTo("a,b\"c,d\r\ne,f");
            assertThat(simdDf.getRow(0).get("num")).isEqualTo(123);

            assertThat(simdDf.getRow(1).get("prefix")).isEqualTo("tail");
            assertThat(simdDf.getRow(1).get("quoted")).isEqualTo("\"start_and_end\"");
            assertThat(simdDf.getRow(1).get("num")).isEqualTo(456);

            assertThat(simdDf.getRow(0).get("prefix")).isEqualTo(scalarDf.getRow(0).get("prefix"));
            assertThat(simdDf.getRow(0).get("quoted")).isEqualTo(scalarDf.getRow(0).get("quoted"));
            assertThat(simdDf.getRow(1).get("quoted")).isEqualTo(scalarDf.getRow(1).get("quoted"));
        }
    }

    @Test
    @DisplayName("SIMD scanner handles fast-path block skipping for >512B unquoted and quoted spans")
    void testFastPathBlockSkippingLongFields() {
        String longUnquoted = "A".repeat(600);
        String longQuotedWithDelims = "\"" + "B".repeat(300) + ",;\n\r" + "C".repeat(300) + "\"";
        String csv = "col_a,col_b,col_c\n"
                + longUnquoted + "," + longQuotedWithDelims + ",999\n";

        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        CupOfTea df = new CupOfTea();
        new FastCsvReader(new CSVImporterConfiguration().setSimdEnabled(true).setInferNumericTypes(true))
                .readInto(bytes, df);

        assertThat(df.size()).isEqualTo(1);
        assertThat(df.getRow(0).get("col_a")).isEqualTo(longUnquoted);
        assertThat(df.getRow(0).get("col_b")).isEqualTo("B".repeat(300) + ",;\n\r" + "C".repeat(300));
        assertThat(df.getRow(0).get("col_c")).isEqualTo(999);
    }

    @Test
    @DisplayName("Complete parity between SIMD, Scalar, and OpenCSV across RFC 4180 edge cases")
    void testRfc4180ParityAcrossAllEngines() throws IOException {
        String csv = "id,name,notes,score,active,trailing\r\n"
                + "1,\"Silva, João\",\"Line 1\nLine 2\",95.5,true,\"\"\r\n"
                + "2,\"Said \"\"Hello, World!\"\"\",\"\",,false,ok\r\n"
                + "3,\"Maria\",\"Escaped \\\"quote\\\" inside\",-42.125,true,NA\r\n"
                + "4,Plain,No newline at EOF,0.0,false,end";

        File file = createTempCsv(csv);
        Schema schema = Schema.of(
                "id", INT,
                "name", STRING,
                "notes", STRING,
                "score", DOUBLE,
                "active", BOOLEAN,
                "trailing", STRING
        );

        CupOfTea simdDf = new CupOfTea();
        simdDf.importFromCsv(file, new CSVImporterConfiguration()
                .setSchema(schema).setFastEngine(true).setSimdEnabled(true));

        CupOfTea scalarDf = new CupOfTea();
        scalarDf.importFromCsv(file, new CSVImporterConfiguration()
                .setSchema(schema).setFastEngine(true).setSimdEnabled(false));

        CupOfTea openCsvDf = new CupOfTea();
        openCsvDf.importFromCsv(file, new CSVImporterConfiguration()
                .setSchema(schema).setFastEngine(false));

        assertThat(simdDf.size()).isEqualTo(4);
        assertThat(scalarDf.size()).isEqualTo(4);
        assertThat(openCsvDf.size()).isEqualTo(4);

        for (int r = 0; r < 4; r++) {
            for (String col : List.of("id", "name", "notes", "score", "active", "trailing")) {
                assertThat(simdDf.getRow(r).get(col))
                        .as("Row %d col %s (SIMD vs Scalar)", r, col)
                        .isEqualTo(scalarDf.getRow(r).get(col));
                assertThat(simdDf.getRow(r).get(col))
                        .as("Row %d col %s (SIMD vs OpenCSV)", r, col)
                        .isEqualTo(openCsvDf.getRow(r).get(col));
            }
        }
    }

    @Test
    @DisplayName("Large multi-chunk file with multiline quoted fields parses in parallel without corrupting chunk boundaries")
    void testMultiChunkParallelQuotedMultilineFile() throws IOException {
        // Generate > 600KB CSV with multiline quoted fields so multiple parallel chunks are exercised
        StringBuilder sb = new StringBuilder();
        sb.append("id,category,description,amount,flag\n");
        int rows = 10_000;
        for (int i = 0; i < rows; i++) {
            sb.append(i).append(",")
              .append("CAT_").append(i % 15).append(",")
              .append("\"Item #").append(i).append(": has, comma and\nembedded newline with \"\"quotes\"\" inside\",")
              .append(String.format(Locale.ROOT, "%.3f", i * 1.25)).append(",")
              .append(i % 2 == 0 ? "true" : "false").append("\n");
        }
        File file = createTempCsv(sb.toString());
        assertThat(file.length()).isGreaterThan(512 * 1024);

        CupOfTea simdDf = new CupOfTea();
        simdDf.importFromCsv(file, new CSVImporterConfiguration()
                .setFastEngine(true).setSimdEnabled(true).setInferTypes(true));

        CupOfTea scalarDf = new CupOfTea();
        scalarDf.importFromCsv(file, new CSVImporterConfiguration()
                .setFastEngine(true).setSimdEnabled(false).setInferTypes(true));

        assertThat(simdDf.size()).isEqualTo(rows);
        assertThat(scalarDf.size()).isEqualTo(rows);
        assertThat(simdDf.getRow(0).get("description"))
                .isEqualTo("Item #0: has, comma and\nembedded newline with \"quotes\" inside");
        assertThat(simdDf.getRow(rows - 1).get("id")).isEqualTo(rows - 1);
        assertThat(simdDf.getRow(rows - 1).get("amount")).isEqualTo((rows - 1) * 1.25);
    }

    @Test
    @DisplayName("Supports custom NA strings, all schema types, and ragged row error detection in SIMD mode")
    void testCustomNaStringsAllTypesAndRaggedRowErrors() {
        String csv = "i,l,d,f,b,s,dt,ldt,inst,bd\n"
                + "10,10000000000,3.14,2.5,true,hello,2025-01-15,2025-01-15T10:20:30,2025-01-15T10:20:30Z,99.95\n"
                + "MISSING,MISSING,MISSING,MISSING,MISSING,MISSING,MISSING,MISSING,MISSING,MISSING\n";

        Schema schema = Schema.of(
                "i", INT,
                "l", LONG,
                "d", DOUBLE,
                "f", FLOAT,
                "b", BOOLEAN,
                "s", STRING,
                "dt", DATE,
                "ldt", DATETIME,
                "inst", INSTANT,
                "bd", BIG_DECIMAL
        );

        CSVImporterConfiguration config = new CSVImporterConfiguration()
                .setSchema(schema)
                .setNaStrings(Set.of("MISSING"))
                .setSimdEnabled(true);

        CupOfTea df = new CupOfTea();
        new FastCsvReader(config).readInto(csv.getBytes(StandardCharsets.UTF_8), df);

        assertThat(df.size()).isEqualTo(2);
        assertThat(df.getRow(0).get("i")).isEqualTo(10);
        assertThat(df.getRow(0).get("l")).isEqualTo(10000000000L);
        assertThat(df.getRow(0).get("d")).isEqualTo(3.14);
        assertThat(df.getRow(0).get("f")).isEqualTo(2.5f);
        assertThat(df.getRow(0).get("b")).isEqualTo(true);
        assertThat(df.getRow(0).get("s")).isEqualTo("hello");
        assertThat(df.getRow(1).get("i")).isNull();
        assertThat(df.getRow(1).get("s")).isNull();
        assertThat(df.getRow(1).get("bd")).isNull();

        // Ragged row (too few columns)
        byte[] tooFew = "a,b,c\n1,2\n".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> new FastCsvReader(new CSVImporterConfiguration().setSimdEnabled(true))
                .readInto(tooFew, new CupOfTea()))
                .isInstanceOf(CsvParseException.class)
                .hasMessageContaining("Row length mismatch");

        // Ragged row (too many columns)
        byte[] tooMany = "a,b,c\n1,2,3,4\n".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> new FastCsvReader(new CSVImporterConfiguration().setSimdEnabled(true))
                .readInto(tooMany, new CupOfTea()))
                .isInstanceOf(CsvParseException.class)
                .hasMessageContaining("Row length mismatch");
    }
}
