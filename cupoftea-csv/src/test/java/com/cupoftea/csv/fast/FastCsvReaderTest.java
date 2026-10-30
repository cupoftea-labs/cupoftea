package com.cupoftea.csv.fast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.importers.configurations.CSVImporterConfiguration;
import com.cupoftea.table.Schema;
import static com.cupoftea.table.Schema.DOUBLE;
import static com.cupoftea.table.Schema.INT;
import static com.cupoftea.table.Schema.STRING;

public class FastCsvReaderTest {

    private File createTempCsv(String content) throws IOException {
        File file = File.createTempFile("fast_csv_test_", ".csv");
        file.deleteOnExit();
        try (FileWriter fw = new FileWriter(file, StandardCharsets.UTF_8)) {
            fw.write(content);
        }
        return file;
    }

    @Test
    @DisplayName("FastCsvReader directly parses numeric CSV into primitive columnar vectors")
    void testFastCsvReaderDirectNumericParsing() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("id,temperature,station\n");
        for (int i = 0; i < 50_000; i++) {
            sb.append(i).append(",").append(String.format(Locale.ROOT, "%.2f", (i % 100) * 0.5)).append(",Station_").append(i % 10).append("\n");
        }
        File file = createTempCsv(sb.toString());

        CSVImporterConfiguration config = new CSVImporterConfiguration()
            .setFastEngine(true)
            .setInferNumericTypes(true);

        CupOfTea df = new CupOfTea();
        df.importFromCsv(file, config);

        assertThat(df.size()).isEqualTo(50_000);
        assertThat(df.getColumnHeader("id").columnType()).isEqualTo(Integer.class);
        assertThat(df.getColumnHeader("temperature").columnType()).isEqualTo(Double.class);
        assertThat(df.getColumnHeader("station").columnType()).isEqualTo(String.class);

        assertThat(df.getRow(0).get("id")).isEqualTo(0);
        assertThat(df.getRow(0).get("temperature")).isEqualTo(0.0);
        assertThat(df.getRow(0).get("station")).isEqualTo("Station_0");
    }

    @Test
    @DisplayName("FastCsvReader works with explicit Schema")
    void testFastCsvReaderWithExplicitSchema() throws IOException {
        String content = "city,pop,lat\n"
            + "Tokyo,37000000,35.6762\n"
            + "Delhi,32000000,28.7041\n"
            + "Shanghai,28000000,31.2304\n";
        File file = createTempCsv(content);

        Schema schema = Schema.of("city", STRING, "pop", INT, "lat", DOUBLE);
        CupOfTea df = new CupOfTea().importFromCsv(file, schema);

        assertThat(df.size()).isEqualTo(3);
        assertThat(df.getColumnHeader("pop").columnType()).isEqualTo(Integer.class);
        assertThat(df.getColumnHeader("lat").columnType()).isEqualTo(Double.class);
        assertThat(df.getRow(0).get("city")).isEqualTo("Tokyo");
        assertThat(df.getRow(0).get("pop")).isEqualTo(37000000);
        assertThat(df.getRow(0).get("lat")).isEqualTo(35.6762);
    }

    @Test
    @DisplayName("FastCsvReader handles missing values and empty cells")
    void testFastCsvReaderMissingValues() throws IOException {
        String content = "a,b,c\n"
            + "1,1.5,foo\n"
            + ",NA,bar\n"
            + "3,3.5,\n";
        File file = createTempCsv(content);

        CSVImporterConfiguration config = new CSVImporterConfiguration()
            .setFastEngine(true)
            .setInferNumericTypes(true);

        CupOfTea df = new CupOfTea();
        df.importFromCsv(file, config);

        assertThat(df.size()).isEqualTo(3);
        assertThat(df.getRow(1).get("a")).isNull();
        assertThat(df.getRow(1).get("b")).isNull();
        assertThat(df.getRow(2).get("c")).isNull();
    }
}
