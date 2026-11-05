package com.cupoftea.csv.fast;

import static com.cupoftea.table.Schema.STRING;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.importers.configurations.CSVImporterConfiguration;
import com.cupoftea.table.Schema;

/**
 * Randomized check that the scalar (SWAR-assisted) path reproduces exactly the fields written,
 * across delimiters, line endings and fields containing every structural byte.
 */
class ScalarSwarDifferentialTest {

    private static final String ALPHABET = "abcdefgh ij,;\"\n\r\u00e9\u20ac\u0081\u00ff0123";

    @Test
    @DisplayName("Scalar path round-trips random fields with structural bytes, CRLF/LF, multiple delimiters")
    void randomRoundTrip() throws IOException {
        Random rnd = new Random(7);
        for (char delim : new char[]{',', ';', '\t'}) {
            for (String eol : new String[]{"\n", "\r\n"}) {
                for (int iter = 0; iter < 30; iter++) {
                    int rows = 1 + rnd.nextInt(60);
                    int cols = 1 + rnd.nextInt(4);
                    String[][] expected = new String[rows][cols];
                    StringBuilder sb = new StringBuilder();
                    for (int c = 0; c < cols; c++) {
                        sb.append(c == 0 ? "" : String.valueOf(delim)).append("c").append(c);
                    }
                    sb.append(eol);
                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < cols; c++) {
                            int len = rnd.nextInt(5) == 0 ? rnd.nextInt(70) : rnd.nextInt(12);
                            StringBuilder f = new StringBuilder();
                            for (int i = 0; i < len; i++) {
                                char ch = ALPHABET.charAt(rnd.nextInt(ALPHABET.length()));
                                if (ch == delim) {
                                    ch = 'z';
                                }
                                f.append(ch);
                            }
                            // A lone trailing \r/space on unquoted values is trimmed by the parser; always quote.
                            String v = f.toString();
                            expected[r][c] = v;
                            sb.append(c == 0 ? "" : String.valueOf(delim));
                            sb.append('"').append(v.replace("\"", "\"\"")).append('"');
                        }
                        sb.append(eol);
                    }
                    File file = File.createTempFile("swar_diff_", ".csv");
                    file.deleteOnExit();
                    Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));

                    Object[] schema = new Object[cols * 2];
                    for (int c = 0; c < cols; c++) {
                        schema[c * 2] = "c" + c;
                        schema[c * 2 + 1] = STRING;
                    }
                    CSVImporterConfiguration config = new CSVImporterConfiguration()
                            .setFastEngine(true)
                            .setSimdEnabled(false)
                            .setDelimiter(delim)
                            .setPreserveEmptyStrings(true)
                            .setSchema(Schema.of(schema));
                    CupOfTea df = new CupOfTea();
                    df.importFromCsv(file, config);

                    assertThat(df.size()).as("rows delim=%s iter=%d", (int) delim, iter).isEqualTo(rows);
                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < cols; c++) {
                            assertThat(df.getRow(r).get("c" + c))
                                    .as("r=%d c=%d delim=%d", r, c, (int) delim)
                                    .isEqualTo(expected[r][c]);
                        }
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Unquoted plain fields, no quote char configured, spanning SWAR word boundaries")
    void unquotedLongFieldsNoQuote() throws IOException {
        StringBuilder sb = new StringBuilder("a;b\n");
        String[] a = new String[40];
        for (int i = 0; i < 40; i++) {
            a[i] = "x".repeat(i) + "\u00e9" + "y".repeat(40 - i);
            sb.append(a[i]).append(';').append(i).append('\n');
        }
        File file = File.createTempFile("swar_unq_", ".csv");
        file.deleteOnExit();
        Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        for (boolean noQuote : new boolean[]{false, true}) {
            CSVImporterConfiguration config = new CSVImporterConfiguration()
                    .setFastEngine(true)
                    .setSimdEnabled(false)
                    .setDelimiter(';')
                    .setSchema(Schema.of("a", STRING, "b", Schema.INT));
            if (noQuote) {
                config.setQuoteChar('\0');
            }
            CupOfTea df = new CupOfTea();
            df.importFromCsv(file, config);
            assertThat(df.size()).isEqualTo(40);
            for (int i = 0; i < 40; i++) {
                assertThat(df.getRow(i).get("a")).isEqualTo(a[i]);
                assertThat(((Number) df.getRow(i).get("b")).intValue()).isEqualTo(i);
            }
        }
    }
}
